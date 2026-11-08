import unittest
from context_window import safe_history_cut, image_message


class HistoryGroupsTest(unittest.TestCase):
    def test_reasoning_message_and_tool_group_is_atomic(self):
        items = [{"role": "user", "content": "task"},
                 {"type": "reasoning", "id": "rs-1"},
                 {"type": "message", "id": "msg-1", "role": "assistant", "content": "checking"},
                 {"type": "function_call", "id": "fc-1", "call_id": "call-1"},
                 {"type": "function_call_output", "call_id": "call-1", "output": "done"},
                 {"type": "reasoning", "id": "rs-2"},
                 {"type": "message", "id": "msg-2", "role": "assistant", "content": "result"},
                 {"role": "user", "content": "next"}]
        groups = [frozenset({"rs-1", "msg-1", "fc-1"}), frozenset({"rs-2", "msg-2"})]
        for cut in (2, 3, 4, 6):
            self.assertFalse(safe_history_cut(items, cut, groups))
        self.assertTrue(safe_history_cut(items, 5, groups))
        self.assertTrue(safe_history_cut(items, 7, groups))

    def test_image_messages_remain_explicit_protected_content(self):
        self.assertTrue(image_message({"role": "user", "content": [{"type": "input_image", "image_url": "data:image/png;base64,AA=="}]}))
        self.assertFalse(image_message({"role": "user", "content": "plain text"}))


if __name__ == "__main__":
    unittest.main()
