import json
from pathlib import Path
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from agent_runtime import AgentRunRequest
from codex_model_session import CodexModelSession


class ModelConversationTest(unittest.TestCase):
    def request(self):
        attempt = SimpleNamespace(check=Mock(), remaining=Mock(return_value=100), resource_uncertain=False,
                                  evidence=SimpleNamespace(record=Mock()), add_cancel_callback=Mock(), remove_cancel_callback=Mock())
        return AgentRunRequest(attempt, ".", SimpleNamespace(writable=True), "Fixed task", "fixed-model",
                               "medium", False, None, "read-only", "never")

    def transport(self, native_item=False):
        session = Mock()
        session.process.poll.return_value = None
        events, turns = [], []

        def rpc(method, params):
            if method == "initialize": return {"userAgent": "mock-codex"}
            if method == "account/read": return {"account": {"type": "chatgpt"}}
            if method == "thread/start":
                return {"thread": {"id": "thread", "ephemeral": True, "path": None}, "cwd": params["cwd"],
                        "approvalPolicy": "never", "sandbox": {"type": "readOnly", "networkAccess": False}}
            if method == "thread/read": return {"thread": {"id": "thread", "ephemeral": True, "path": None}}
            if method == "turn/start":
                turn = "turn-" + str(len(turns) + 1)
                turns.append(turn)
                item = {"type": "commandExecution"} if native_item else {
                    "type": "agentMessage", "id": turn, "phase": "final_answer",
                    "text": json.dumps({"action": "finish", "result": {"verdict": "PASS"}})}
                events.append({"method": "turn/completed", "params": {"threadId": "thread", "turnId": turn,
                               "turn": {"id": turn, "status": "completed", "items": [item]}}})
                return {"turn": {"id": turn}}
            raise AssertionError(method)

        session.rpc.side_effect = rpc
        session.next_event.side_effect = lambda: events.pop(0)
        return session

    def test_two_turns_use_one_checked_thread_and_close_the_process_once(self):
        request, session = self.request(), self.transport()
        with patch("codex_model_session.StdioSession", return_value=session), \
                patch("codex_model_session.preflight", return_value="codex"), \
                patch("codex_model_session.verify_model_only_tools") as verify:
            with CodexModelSession(request, Mock()) as conversation:
                first = conversation.run_turn("first", ())
                second = conversation.run_turn("new observation", ())
        self.assertEqual(json.loads(first), json.loads(second))
        methods = [call.args[0] for call in session.rpc.call_args_list]
        self.assertEqual(methods.count("initialize"), 1)
        self.assertEqual(methods.count("thread/start"), 1)
        self.assertEqual(methods.count("turn/start"), 2)
        verify.assert_called_once_with(session, "thread")
        session.close.assert_called_once()
        request.attempt.remove_cancel_callback.assert_called_once()

    def test_native_tool_item_is_rejected_and_session_is_closed(self):
        session = self.transport(native_item=True)
        with patch("codex_model_session.StdioSession", return_value=session), \
                patch("codex_model_session.preflight", return_value="codex"), \
                patch("codex_model_session.verify_model_only_tools"):
            with self.assertRaisesRegex(RuntimeError, "MODEL_ONLY_NATIVE_ITEM_DENIED"):
                with CodexModelSession(self.request(), Mock()) as conversation:
                    self.addCleanup(Path(conversation.cwd).rmdir)
                    conversation.run_turn("first", ())
        session.close.assert_called_once()

    def test_host_tool_callback_is_never_forwarded_to_a_native_executor(self):
        with self.assertRaisesRegex(RuntimeError, "MODEL_ONLY_NATIVE_TOOL_DENIED"):
            CodexModelSession._deny_tool({"method": "item/tool/call"})


if __name__ == "__main__":
    unittest.main()
