import os
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from guarded_app_server import _run_turns, _usage_delta, _respond_tool
from formal_questions import YieldRequested


class Session:
    def __init__(self, outputs, usages=None, status='completed'):
        self.events, self.calls = [], []
        for index, output in enumerate(outputs, 1):
            turn = 'turn-' + str(index)
            identity = {'threadId': 'thread', 'turnId': turn}
            if usages and usages[index - 1] is not None:
                total = usages[index - 1]
                self.events.append({'method': 'thread/tokenUsage/updated', 'params': {**identity, 'tokenUsage': {'total': total}}})
            self.events.append({'method': 'item/completed', 'params': {**identity, 'item': {'type': 'agentMessage', 'id': turn, 'text': output}}})
            self.events.append({'method': 'turn/completed', 'params': {**identity, 'turn': {'id': turn, 'status': status}}})

    def rpc(self, method, params):
        self.calls.append((method, params))
        return {'turn': {'id': 'turn-' + str(len(self.calls))}}

    def next_event(self):
        return self.events.pop(0)


class GuardedCompletionTest(unittest.TestCase):
    def request(self, validator):
        attempt = SimpleNamespace(check=Mock(), evidence=Mock(), resource_uncertain=False, instruction_revision=3)
        broker = SimpleNamespace(attempt=attempt, pending_question=None, model_started=Mock(side_effect=['op1', 'op2', 'op3']),
                                 model_finished=Mock(), finish=Mock(), context={'configurationDigest': 'a' * 64})
        return SimpleNamespace(attempt=attempt, broker=broker, instruction='Original fixed task', model='model',
                               reasoning_effort='high', candidate_validator=validator)

    def execute(self, session, request, limit='2'):
        with patch.dict(os.environ, {'AIW_COMPLETION_CORRECTIONS': limit}):
            return _run_turns(session, request, {'thread': 'thread', 'turn': None, 'progress': 'no-new-tools'})

    def test_correction_reuses_thread_with_feedback_only_and_counts_usage_deltas(self):
        validator = Mock(side_effect=[RuntimeError('COMPLETION_VERDICT_INVALID'), {'verdict': 'PASS'}])
        request = self.request(validator)
        session = Session(['bad', 'fixed'], [{'inputTokens': 10, 'cachedInputTokens': 4, 'outputTokens': 2},
                                             {'inputTokens': 25, 'cachedInputTokens': 7, 'outputTokens': 5}])
        self.assertEqual(self.execute(session, request), 'fixed')
        self.assertEqual([call[1]['threadId'] for call in session.calls], ['thread', 'thread'])
        self.assertNotIn('Original fixed task', session.calls[1][1]['input'][0]['text'])
        self.assertIn('Do not repeat successful side effects', session.calls[1][1]['input'][0]['text'])
        self.assertEqual(request.broker.model_finished.call_args_list[1].args[2], {'inputTokens': 15, 'cachedTokens': 3, 'outputTokens': 3})
        request.broker.finish.assert_not_called()

    def test_same_missing_report_condition_stops_without_new_tool_observations(self):
        request = self.request(Mock(side_effect=RuntimeError('COMPLETION_REPORT_CONFLICT')))
        session = Session(['bad', 'still bad', 'must not run'])
        with self.assertRaisesRegex(RuntimeError, 'COMPLETION_CORRECTION_LIMIT'):
            self.execute(session, request)
        self.assertEqual(len(session.calls), 2)
        self.assertFalse(request.attempt.resource_uncertain)

    def test_zero_corrections_rejects_before_returning_a_candidate(self):
        request = self.request(Mock(side_effect=RuntimeError('COMPLETION_REPORT_EMPTY')))
        session = Session(['', 'must not run'])
        with self.assertRaisesRegex(RuntimeError, '^COMPLETION_REPORT_EMPTY$'):
            self.execute(session, request, '0')
        self.assertEqual(len(session.calls), 1)

    def test_repair_required_is_returned_unchanged_without_forcing_a_pass(self):
        request = self.request(Mock(return_value={'verdict': 'REPAIR_REQUIRED'}))
        self.assertEqual(self.execute(Session(['Original blocked findings']), request), 'Original blocked findings')
        self.assertEqual(request.broker.model_started.call_count, 1)

    def test_interrupted_backend_is_not_retried_as_report_format_correction(self):
        request = self.request(Mock())
        with self.assertRaisesRegex(RuntimeError, 'CODEX_TURN_FAILED'):
            self.execute(Session(['partial'], status='interrupted'), request)
        request.candidate_validator.assert_not_called()
        self.assertTrue(request.attempt.resource_uncertain)
        request.broker.finish.assert_called_once_with('op1', 'UNKNOWN', {'code': 'MODEL_OUTCOME_UNKNOWN'})

    def test_question_yield_precedes_candidate_validation(self):
        request = self.request(Mock())
        request.broker.pending_question = {'question': {'questionId': 'q'}}
        with self.assertRaises(YieldRequested):
            self.execute(Session(['partial'], status='interrupted'), request)
        request.candidate_validator.assert_not_called()
        request.broker.finish.assert_not_called()

    def test_missing_usage_never_turns_cumulative_totals_into_a_false_request_cost(self):
        self.assertIsNone(_usage_delta(None, {'inputTokens': 20, 'cachedTokens': 0, 'outputTokens': 2}))
        self.assertIsNone(_usage_delta({'inputTokens': 0, 'cachedTokens': 0, 'outputTokens': 0}, None))
        with self.assertRaisesRegex(RuntimeError, 'USAGE_VECTOR_INVALID'):
            _usage_delta({'inputTokens': 20, 'cachedTokens': 0, 'outputTokens': 2}, {'inputTokens': 19, 'cachedTokens': 0, 'outputTokens': 3})

    def test_callbacks_outside_the_active_turn_never_dispatch_tools(self):
        broker = SimpleNamespace(invoke=Mock(return_value={'text': 'actual read result'}))
        message = {'id': 4, 'method': 'item/tool/call', 'params': {'threadId': 'thread', 'turnId': 'old-turn',
                   'namespace': '', 'callId': 'call', 'tool': 'read', 'arguments': {'path': 'file'}}}
        for turn in [None, 'next-turn']:
            with self.subTest(turn=turn), self.assertRaisesRegex(RuntimeError, 'FOREIGN_TOOL_REQUEST_DENIED'):
                _respond_tool(broker, {'thread': 'thread', 'turn': turn, 'progress': 'fixed'}, message)
        broker.invoke.assert_not_called()
        current = {'thread': 'thread', 'turn': 'old-turn', 'progress': 'fixed'}
        self.assertTrue(_respond_tool(broker, current, message)['result']['success'])
        broker.invoke.assert_called_once_with('read', {'path': 'file'}, 'call')
        self.assertNotEqual(current['progress'], 'fixed')


if __name__ == '__main__':
    unittest.main()
