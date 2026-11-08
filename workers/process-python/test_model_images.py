import base64
import hashlib
import json
import sys
import time
import types
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from model_images import input_message, observed, validate, wrap


def image():
    raw = b"\x89PNG\r\n\x1a\n" + b"image-payload"
    return {"id": "selected", "mime": "image/png", "sha256": hashlib.sha256(raw).hexdigest(), "base64": base64.b64encode(raw).decode()}


class ModelImagesTest(unittest.IsolatedAsyncioTestCase):
    def test_frozen_hash_and_selection_limits_are_enforced(self):
        value = image()
        with self.assertRaisesRegex(RuntimeError, "INVALID_OR_CHANGED"):
            validate([{**value, "sha256": "0" * 64}])
        with self.assertRaisesRegex(RuntimeError, "INVALID"):
            validate([value, value])
        with self.assertRaisesRegex(RuntimeError, "LIMIT"):
            validate([value] * 5)

    def test_observation_contains_hashes_without_base64(self):
        value = image(); raw = input_message([value], "question"); visible = observed(raw)
        self.assertEqual(raw[0]["content"][1]["type"], "input_image")
        self.assertIn(value["sha256"], json.dumps(visible))
        self.assertNotIn(value["base64"], json.dumps(visible))

    async def test_files_are_reused_within_request_and_only_created_ids_are_deleted(self):
        created, deleted, events = [], [], []
        class Files:
            async def create(self, *, file, purpose, expires_after):
                created.append((file, purpose, expires_after))
                return SimpleNamespace(id="file-api-owned", expires_at=int(time.time()) + 3600)
            async def delete(self, identity, *, timeout):
                deleted.append(identity)
                return SimpleNamespace(id=identity, deleted=True)
        class Model:
            async def get_response(self, instructions, items):
                return items
        fake = types.ModuleType("agents.models.interface");fake.Model = object
        request = SimpleNamespace(model_images=[image()], attempt=SimpleNamespace(check=lambda: None,
            evidence=SimpleNamespace(record=lambda *args, **kwargs: events.append(kwargs))))
        with patch.dict("os.environ", {"AIW_MODEL_IMAGE_TRANSPORT": "files"}), patch.dict(sys.modules, {"agents.models.interface": fake}):
            model = wrap(Model(), SimpleNamespace(files=Files()), request, SimpleNamespace(provider="deepseek", protocol="responses"))
            for _ in range(2):
                projected = await model.get_response(None, input_message(request.model_images, "question"))
                self.assertEqual(projected[0]["content"][1]["file_id"], "file-api-owned")
            await model.close()
        self.assertEqual(len(created), 1)
        self.assertEqual(created[0][2], {"anchor": "created_at", "seconds": 3600})
        self.assertEqual(deleted, ["file-api-owned"])
        self.assertTrue(any(event["status"] == "deleted" for event in events))


if __name__ == "__main__":
    unittest.main()
