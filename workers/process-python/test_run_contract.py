import json
import unittest

from run_contract import ContextLedger, RuntimeCapabilities


class Evidence:
    def __init__(self):
        self.events = []

    def record(self, event, **values):
        self.events.append({"event": event, **values})


class ContextLedgerTest(unittest.TestCase):
    def test_capabilities_are_frozen_and_do_not_claim_live_control(self):
        evidence = Evidence()
        ledger = ContextLedger(evidence)
        caps = RuntimeCapabilities("HOST", "HOST", "WORKER_PROJECTION", "TOOL", "NONE", "NONE", "NONE",
                                   "BEST_EFFORT", "UNKNOWN", "BACKEND_TURN")
        ledger.capabilities_observed("codex", caps)
        ledger.capabilities_observed("codex", caps)
        self.assertEqual(len(evidence.events), 1)
        self.assertEqual(evidence.events[0]["capabilities"]["pauseBoundary"], "NONE")
        other = RuntimeCapabilities("HOST", "HOST", "WORKER_PROJECTION", "TOOL", "NONE", "SAME_ATTEMPT", "NONE",
                                    "BEST_EFFORT", "UNKNOWN", "BACKEND_TURN")
        with self.assertRaisesRegex(RuntimeError, "CAPABILITIES_CHANGED"):
            ledger.capabilities_observed("codex", other)

    def test_observations_keep_metadata_without_prompt_or_tool_content(self):
        evidence = Evidence()
        ledger = ContextLedger(evidence)
        projection = {"instructions": "private-task-secret", "messages": [{"content": "private-tool-secret"}], "tools": []}
        observed = ledger.observe("codex", projection, request_scope="BACKEND_TURN")
        events = json.dumps(evidence.events)
        self.assertNotIn("private-task-secret", events)
        self.assertNotIn("private-tool-secret", events)
        self.assertEqual(observed.visibility, "WORKER_PROJECTION")
        self.assertEqual(observed.request_scope, "BACKEND_TURN")
        self.assertFalse(evidence.events[0]["usageKnown"])

    def test_component_changes_do_not_claim_provider_cache_hits(self):
        evidence = Evidence()
        ledger = ContextLedger(evidence)
        projection = {"instructions": "fixed", "messages": ["first"], "tools": []}
        ledger.observe("api", projection, request_scope="REQUEST")
        projection["messages"].append("second")
        ledger.observe("api", projection, request_scope="REQUEST")
        components = [event for event in evidence.events if event["event"] == "context-component"]
        self.assertFalse(components[3]["changed"])
        self.assertTrue(components[4]["changed"])
        self.assertNotIn("cachedTokens", json.dumps(evidence.events))

    def test_inventory_is_deduplicated_and_not_reported_as_actual_attribution(self):
        evidence = Evidence()
        ledger = ContextLedger(evidence)
        ledger.material("knowledge", "approved", "v1", "private-content")
        ledger.material("knowledge", "approved", "v1", "private-content")
        self.assertEqual(len(evidence.events), 1)
        self.assertFalse(evidence.events[0]["complete"])
        self.assertNotIn("private-content", json.dumps(evidence.events))

    def test_unicode_sizes_are_utf8_bytes(self):
        evidence = Evidence()
        observed = ContextLedger(evidence).observe("api", {"messages": ["任务"]}, request_scope="REQUEST")
        raw = json.dumps({"messages": ["任务"]}, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
        self.assertEqual(observed.input_bytes, len(raw))

    def test_request_binding_and_tool_schema_are_metadata_only_and_frozen(self):
        evidence = Evidence()
        ledger = ContextLedger(evidence)
        binding = {'instruction_revision': 3, 'configuration_digest': 'a' * 64, 'model': 'configured-model', 'effort': 'high'}
        observed = ledger.observe('api', {'messages': ['private task'], 'tools': [{'name': 'read'}]}, request_scope='REQUEST', binding=binding)
        self.assertEqual(observed.instruction_revision, 3)
        self.assertEqual(observed.configuration_digest, 'a' * 64)
        self.assertEqual(len(observed.tool_schema_digest), 64)
        self.assertNotIn('private task', json.dumps(evidence.events))
        with self.assertRaisesRegex(RuntimeError, 'CONTEXT_BINDING_CHANGED'):
            ledger.observe('api', {'messages': []}, request_scope='REQUEST', binding={**binding, 'model': 'different'})

    def test_unobservable_native_tool_schema_stays_unknown(self):
        observed = ContextLedger(Evidence()).observe('codex', {'messages': ['task']}, request_scope='BACKEND_TURN')
        self.assertIsNone(observed.tool_schema_digest)


if __name__ == "__main__":
    unittest.main()
