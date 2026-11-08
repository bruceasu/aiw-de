"""Wire-level regressions. Standard library only; no SDK, server, files or network."""
import unittest
import os
from types import SimpleNamespace
from unittest.mock import patch
from api_connection import runtime_type
from deepseek_chat import ChatStream, messages, content
from tool_arguments import parse_tool_arguments


class DeepSeekProtocolTest(unittest.TestCase):
    def test_openai_chat_has_a_distinct_identity_while_codex_ignores_api_settings(self):
        with patch.dict(os.environ, {"AIW_API_PROVIDER": "openai", "AIW_API_PROTOCOL": "chat-completions"}, clear=True):
            self.assertEqual(runtime_type(SimpleNamespace(runtime="openai-agents", process_role="goal-discussion")),
                             "openai-agents:openai:chat-completions:goal-discussion")
            self.assertEqual(runtime_type(SimpleNamespace(runtime="codex-app-server", process_role="coder")), "codex-app-server:coder")
    def test_duplicate_nonfinite_and_nonobject_arguments_are_rejected_before_tools(self):
        for raw in ('{"path":"allowed","path":"other"}', '{"value":NaN}', '{"value":1e1000}', '[]'):
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                parse_tool_arguments(raw)
    def test_reasoning_and_multiple_tool_results_survive_following_user_turn(self):
        history = [
            {"role": "user", "content": "Question"},
            {"type": "reasoning", "content": [{"type": "reasoning_text", "text": "provider reasoning"}]},
            {"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "Checking"}]},
            {"type": "function_call", "call_id": "one", "name": "read", "arguments": '{"path":"a"}'},
            {"type": "function_call", "call_id": "two", "name": "read", "arguments": '{"path":"b"}'},
            {"type": "function_call_output", "call_id": "one", "output": "A"},
            {"type": "function_call_output", "call_id": "two", "output": "B"},
            {"type": "reasoning", "content": [{"type": "reasoning_text", "text": "provider continuation"}]},
            {"role": "assistant", "content": "Done"}, {"role": "user", "content": "Next"}]
        wire = messages("Host instruction", history)
        self.assertEqual(wire[2]["reasoning_content"], "provider reasoning")
        self.assertEqual(wire[2]["content"], "Checking")
        self.assertEqual([call["id"] for call in wire[2]["tool_calls"]], ["one", "two"])
        self.assertEqual([row["tool_call_id"] for row in wire if row["role"] == "tool"], ["one", "two"])
        self.assertEqual(wire[-2]["reasoning_content"], "provider continuation")

    def test_partial_and_duplicate_tool_receipts_are_not_replayed_as_valid_history(self):
        call = {"type": "function_call", "call_id": "one", "name": "read", "arguments": "{}"}
        result = {"type": "function_call_output", "call_id": "one", "output": "done"}
        for history in ([call], [call, result, result], [result], [call, {"role": "user", "content": "next"}]):
            with self.subTest(history=history), self.assertRaisesRegex(RuntimeError, "HISTORY_UNPAIRED"):
                messages(None, history)

    def test_stream_waits_for_complete_arguments_and_terminal_before_accepting_calls(self):
        stream = ChatStream(4096)
        stream.add({"choices": [{"index": 0, "delta": {"reasoning_content": "考虑", "tool_calls": [{"index": 0,
            "id": "one", "type": "function", "function": {"name": "read", "arguments": '{"path":'}}]}}]})
        with self.assertRaisesRegex(RuntimeError, "STREAM_INCOMPLETE"):
            stream.complete()
        stream.add({"choices": [{"index": 0, "delta": {"tool_calls": [{"index": 0, "function": {"arguments": '"目录"}'}}]}, "finish_reason": "tool_calls"}]})
        stream.add({"choices": [], "usage": {"prompt_tokens": 20, "completion_tokens": 10, "prompt_cache_hit_tokens": 0}})
        result = stream.complete()
        self.assertEqual(result["calls"][0]["arguments"], '{"path":"目录"}')
        self.assertEqual(result["reasoning"], "考虑")

    def test_truncated_filtered_and_duplicate_terminal_streams_fail_closed(self):
        for reason in (None, "length", "content_filter", "insufficient_system_resource"):
            stream = ChatStream(4096)
            stream.add({"choices": [{"delta": {"content": "partial"}, "finish_reason": reason}]})
            with self.subTest(reason=reason), self.assertRaisesRegex(RuntimeError, "STREAM_INCOMPLETE"):
                stream.complete()
        stream = ChatStream(4096)
        stream.add({"choices": [{"delta": {"content": "done"}, "finish_reason": "stop"}]})
        with self.assertRaisesRegex(RuntimeError, "DUPLICATE_TERMINAL"):
            stream.add({"choices": [{"delta": {}, "finish_reason": "stop"}]})

    def test_files_use_the_documented_deepseek_flat_file_block(self):
        self.assertEqual(content([{"type": "input_image", "file_id": "file-api-owned"}]),
                         [{"type": "file", "file_id": "file-api-owned"}])


if __name__ == "__main__":
    unittest.main()
