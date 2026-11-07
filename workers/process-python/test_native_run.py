import hashlib
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from native_run import NativeRun, input_question, open_run, CAPABILITY, QUESTION_CAPABILITY
from plan_workspace import PlanWorkspace, input_manifests
from worker_runtime import attempt_scope, snapshot


class NativeRunTest(unittest.TestCase):
    def test_only_explicit_missing_input_opens_a_question(self):
        for text in ['', 'Please repair the output.', '{"verdict":"REPAIR_REQUIRED","completed":false}']:
            with self.subTest(text=text):
                self.assertIsNone(input_question(text))
        value = input_question('```json\n{"outcome":"BLOCKED_INPUT","question":{"text":"Which target?","options":["a","b"]}}\n```\nAIW_VERDICT: REPAIR_REQUIRED')
        self.assertEqual(value['question'], 'Which target?')
        self.assertEqual(value['options'], ['a', 'b'])
        self.assertIsNotNone(input_question('Status: BLOCKED_BEFORE_IMPLEMENTATION\nNeed a decision.'))

    def test_explicit_blocker_cannot_be_reported_as_pass(self):
        run = NativeRun.__new__(NativeRun)
        for text in ['{"outcome":"BLOCKED_INPUT","verdict":"PASS"}',
                     '{"outcome":"BLOCKED_INPUT"}\nAIW_VERDICT: PASS']:
            with self.subTest(text=text), self.assertRaisesRegex(RuntimeError, 'COMPLETION_REPORT_CONFLICT'):
                run.validate_report({'verdict': 'PASS'}, text)
        result = run.validate_report({'verdict': 'PASS', 'summary': 'retained'}, '{"outcome":"BLOCKED_INPUT"}')
        self.assertEqual(result, {'verdict': 'REPAIR_REQUIRED', 'summary': 'retained'})

    def test_duplicate_fields_are_rejected_instead_of_overriding_the_status(self):
        with self.assertRaisesRegex(RuntimeError, 'DUPLICATE_FIELD'):
            input_question('{"outcome":"BLOCKED_INPUT","outcome":"PASS"}')

    def checkpoint_run(self, records, continuation=True):
        run = NativeRun.__new__(NativeRun)
        run.task = {'taskId': 'next', 'requiredCapabilities': [QUESTION_CAPABILITY]}
        run.availability = {'checkpointRequired': continuation}
        run.config = SimpleNamespace(capabilities={'handoff-store:' + 'a' * 64})
        run.client = SimpleNamespace(_post=Mock(return_value=records))
        run.base, run.lease = '/tasks/next', {'attemptId': 'next-attempt'}
        run.channel = SimpleNamespace(enter=Mock())
        run.attempt = SimpleNamespace(resource_uncertain=False, evidence=SimpleNamespace(record=Mock()))
        run.active = False
        return run

    def test_a_continuation_requires_the_exact_checkpoint_before_any_model_run(self):
        for records in [[], {}, [None], [{'taskId': 'old', 'attemptId': 'old-attempt'}]]:
            run = self.checkpoint_run(records)
            with self.subTest(records=records), self.assertRaises(RuntimeError):
                run.begin()
            self.assertTrue(run.attempt.resource_uncertain)
            self.assertNotIn('checkpointInputs', run.task)
        fresh = self.checkpoint_run([], continuation=False)
        fresh.begin()
        self.assertEqual(fresh.task['checkpointInputs'], [])

    def test_capability_is_not_a_continuation_marker_and_missing_declaration_is_rejected(self):
        run = self.checkpoint_run([], continuation=None)
        with self.assertRaisesRegex(RuntimeError, 'CONTINUATION_DECLARATION_REQUIRED'):
            run.begin()
        run = self.checkpoint_run([{'source': 'unrequested'}], continuation=False)
        with self.assertRaisesRegex(RuntimeError, 'CHECKPOINT_UNEXPECTED'):
            run.begin()

    def test_a_verified_checkpoint_keeps_source_and_confirmed_answer_identity(self):
        record = {'taskId': 'old', 'attemptId': 'old-attempt', 'answerRevision': 2,
                  'answerDigest': 'b' * 64, 'storageCapability': 'handoff-store:' + 'a' * 64,
                  'handoff': {'manifest': '.ai/handoffs/' + hashlib.sha256(b'old').hexdigest() + '/' + hashlib.sha256(b'old-attempt').hexdigest() + '/manifest.json',
                              'sha256': 'c' * 64, 'location': 'local-directory'}}
        run = self.checkpoint_run([record])
        run.begin()
        self.assertEqual(run.task['checkpointInputs'], [record])
        record['handoff']['manifest'] = '.ai/handoffs/wrong/manifest.json'
        changed = self.checkpoint_run([record])
        with self.assertRaisesRegex(RuntimeError, 'SOURCE_CHANGED'):
            changed.begin()

    def test_plain_unbound_native_tasks_keep_their_existing_path(self):
        config = SimpleNamespace(runtime='codex-app-server', worker_id='worker')
        task = {'taskId': 'task', 'attemptId': 'attempt', 'leaseToken': 'lease'}
        client = SimpleNamespace(_post=Mock(return_value={'supported': False}))
        self.assertIsNone(open_run(config, client, task, SimpleNamespace()))
        with self.assertRaisesRegex(RuntimeError, 'OWNER_BINDING_REQUIRED'):
            open_run(config, client, {**task, 'requiredCapabilities': [CAPABILITY]}, SimpleNamespace())

    def test_unknown_open_result_is_retained_instead_of_replayed_as_repair(self):
        config = SimpleNamespace(runtime='codex-app-server', worker_id='worker', capabilities={'native-config:' + 'a' * 64})
        task = {'taskId': 'task', 'attemptId': 'attempt', 'leaseToken': 'lease'}
        attempt = SimpleNamespace(resource_uncertain=False)
        client = SimpleNamespace(_post=Mock(return_value={'supported': True, 'checkpointRequired': False}))
        with patch('native_run.capabilities', return_value=config.capabilities), patch('native_run.NativeRun', side_effect=RuntimeError('response lost')):
            with self.assertRaisesRegex(RuntimeError, 'response lost'):
                open_run(config, client, task, attempt)
        self.assertTrue(attempt.resource_uncertain)

    def test_checkpoint_checks_the_retained_tree_without_git_or_source_uploads(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'source.txt').write_text('original', encoding='utf-8')
            attempt = SimpleNamespace(check=Mock())
            task = {'taskId': 'source', 'attemptId': 'attempt', 'planId': 'plan'}
            workspace = PlanWorkspace(root, root, attempt, Mock(), Mock())
            with attempt_scope(attempt):
                ref = workspace.handoff(task, {'summary': 'need input', 'verdict': 'REPAIR_REQUIRED'}, None,
                                        checkpoint_tree_digest=snapshot(root, attempt))
                successor = {'checkpointInputs': [{'taskId': 'source', 'attemptId': 'attempt',
                                                   'handoff': {**ref, 'location': str(root)}}]}
                self.assertTrue(input_manifests(successor)[0]['checkpoint'])
                (root / 'source.txt').write_text('changed externally', encoding='utf-8')
                with self.assertRaisesRegex(RuntimeError, 'CHECKPOINT_TREE_CHANGED'):
                    input_manifests(successor)


if __name__ == '__main__':
    unittest.main()
