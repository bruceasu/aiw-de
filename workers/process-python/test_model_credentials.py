import io
import json
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from model_credentials import ModelCredentialProvider, capability
from chatgpt_responses_runtime import ResponsesConversation


class ModelCredentialsTest(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict("os.environ", {"AIW_MODEL_CREDENTIAL_REF": "MC-" + "a" * 36, "AIW_MODEL_CREDENTIAL_VERSION": "1"})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.attempt = Mock()
        self.attempt.remaining.return_value = 60
        self.client = SimpleNamespace(base="http://127.0.0.1:8080/api/r1/worker-protocol/v1", token="machine", model_proof="worker-private-proof", timeout=30,
            _post=Mock(return_value={"kind": "CHATGPT_RESPONSES", "accessToken": "short-access", "generation": 1, "expiresAt": 1234}))

    def provider(self):
        return ModelCredentialProvider(self.client, SimpleNamespace(worker_id="worker"), {"taskId": "task", "attemptId": "attempt", "leaseToken": "lease"}, self.attempt, "model-a")

    def test_provider_uses_exact_attempt_and_never_requests_a_refresh_token(self):
        provider = self.provider()
        self.assertTrue(capability().startswith("model-auth:"))
        self.assertEqual(provider.access("CHATGPT_RESPONSES")["accessToken"], "short-access")
        request = self.client._post.call_args.args[1]
        self.assertEqual(request["lease"], {"workerId": "worker", "attemptId": "attempt", "leaseToken": "lease"})
        self.assertNotIn("refreshToken", request)
        self.assertEqual(request["model"], "model-a")

    def test_plain_http_to_remote_plane_and_wrong_credential_type_are_rejected(self):
        self.client.base = "http://remote.example/api/r1/worker-protocol/v1"
        with self.assertRaisesRegex(RuntimeError, "SECURE_PLANE"):
            self.provider()
        self.client.base = "http://127.0.0.1/api/r1/worker-protocol/v1"
        with self.assertRaisesRegex(RuntimeError, "RESPONSE_INVALID"):
            self.provider().access("CODEX_APP_SERVER")

    def stream(self, *events):
        return io.BytesIO(b"".join(("data: " + json.dumps(event) + "\n\n").encode() for event in events))

    def conversation(self):
        return ResponsesConversation(SimpleNamespace(credential_provider=self.provider(), attempt=self.attempt), Mock())

    def test_stream_requires_completed_before_any_candidate_can_be_returned(self):
        stream = self.stream({"type": "response.output_text.delta", "delta": '{"action":"finish","result":{}}'})
        with self.assertRaisesRegex(RuntimeError, "NOT_COMPLETED"):
            self.conversation()._stream(stream)

    def test_stream_failure_and_native_tool_items_are_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "NOT_COMPLETED"):
            self.conversation()._stream(self.stream({"type": "response.failed"}))
        with self.assertRaisesRegex(RuntimeError, "NOT_COMPLETED"):
            self.conversation()._stream(self.stream({"type": "response.completed", "response": {"status": "completed", "output": [{"type": "function_call"}]}}))

    def test_completed_text_and_actual_usage_are_returned(self):
        text, usage = self.conversation()._stream(self.stream({"type": "response.output_text.delta", "delta": "report"},
            {"type": "response.completed", "response": {"status": "completed", "output": [], "usage": {"input_tokens": 12, "output_tokens": 3}}}))
        self.assertEqual(text, "report")
        self.assertEqual(usage["input_tokens"], 12)

    def test_missing_worker_proof_cannot_use_a_public_connection_reference(self):
        self.client.model_proof = ""
        with self.assertRaisesRegex(RuntimeError, "WORKER_PROOF_REQUIRED"):
            self.provider()

    def test_worker_proof_and_machine_credentials_are_not_in_child_environment(self):
        from worker_runtime import child_environment
        source = {"PATH": "programs", "AIW_MODEL_WORKER_PROOF": "proof", "AIW_WORKER_TOKEN": "machine", "AIW_MODEL_CREDENTIAL_KEY": "vault-key"}
        environment = child_environment(source)
        self.assertEqual(environment, {"PATH": "programs"})
        self.assertEqual(source["AIW_MODEL_WORKER_PROOF"], "proof")


if __name__ == "__main__":
    unittest.main()
