"""Offline source tests: mocks only, no login, model calls or real DPAPI."""
import copy
import hashlib
import tempfile
import unittest
from unittest.mock import patch

import model_auth_protocol as auth


class CredentialFilesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.records = {}
        self.store = auth.Credentials(self.temporary.name)
        self.start_patch("write_private", side_effect=lambda path, value: self.records.__setitem__(str(path), copy.deepcopy(value)))
        self.start_patch("read_private", side_effect=lambda path: copy.deepcopy(self.records.get(str(path))))

    def start_patch(self, name, **kwargs):
        patcher = patch.object(auth, name, **kwargs)
        self.addCleanup(patcher.stop)
        return patcher.start()

    def record(self, token="original"):
        return {"kind": "CHATGPT_RESPONSES", "clientId": "issued-client", "subject": "subject", "accountId": None,
                "planType": None, "expiresAt": 9999999999999, "earliestRefreshAt": 0, "inferenceAllowed": True,
                "state": "READY", "tokens": {"access_token": "access-" + token, "refresh_token": "refresh-" + token,
                "id_token": "identity-" + token, "scope": auth.SCOPES}}

    def test_projection_contains_file_reference_and_digest_without_tokens(self):
        saved = self.store.save(self.record())
        self.assertNotIn("tokens", saved)
        self.assertEqual(saved["refreshDigest"], hashlib.sha256(b"refresh-original").hexdigest())
        self.assertEqual(self.store.access(saved["credentialFile"]), {"accessToken": "access-original"})

    def test_bootstrap_client_is_selected_in_python_and_never_used_for_code_exchange(self):
        from urllib.parse import urlsplit, parse_qs
        result=auth.run({"operation":"authorize","client":"","host":"host","redirect":"http://127.0.0.1:1455/auth/callback",
                         "state":"state","nonce":"nonce","challenge":"challenge"})
        self.assertEqual(["dynamic_agent_client"],parse_qs(urlsplit(result["url"]).query)["client_id"])
        request=self.start_patch("request_json")
        with self.assertRaisesRegex(ValueError,"ISSUED_CLIENT_REQUIRED"):
            auth.run({"operation":"exchange","directory":self.temporary.name,"client":"dynamic_agent_client"})
        request.assert_not_called()

    def test_uncertain_refresh_is_not_retried(self):
        saved = self.store.save(self.record())
        request = self.start_patch("request_json", side_effect=OSError("lost reply"))
        for _ in range(2):
            with self.assertRaises(ValueError):
                self.store.refresh({**saved, "kind": "CHATGPT_RESPONSES"})
        self.assertEqual(request.call_count, 1)
        self.assertEqual(self.records[str(self.store.path(saved["credentialFile"]))]["state"], "REFRESH_OUTCOME_UNKNOWN")

    def test_refresh_replaces_reference_and_retired_reference_cannot_return_new_generation(self):
        saved = self.store.save(self.record())
        request = self.start_patch("request_json", return_value={})
        self.start_patch("validate", return_value=self.record("rotated"))
        current = self.store.refresh({**saved, "kind": "CHATGPT_RESPONSES"})
        self.assertNotEqual(current["credentialFile"], saved["credentialFile"])
        with self.assertRaises(ValueError):
            self.store.access(saved["credentialFile"])
        self.assertEqual(self.store.access(current["credentialFile"]), {"accessToken": "access-rotated"})
        params = request.call_args.args[1]
        self.assertEqual(params["client_id"], "issued-client")
        self.assertNotIn("scope", params)

    def test_only_opaque_local_file_names_are_accepted(self):
        for value in ("../other.json", "C:/private/token.json", "https://example.com/token.json"):
            with self.assertRaises(ValueError):
                self.store.access(value)

    def test_revoke_failure_still_erases_local_tokens(self):
        saved = self.store.save(self.record())
        self.start_patch("request_json", side_effect=OSError("offline"))
        self.assertFalse(self.store.revoke(saved["credentialFile"]))
        self.assertNotIn("tokens", self.records[str(self.store.path(saved["credentialFile"]))])
        with self.assertRaises(ValueError):
            self.store.access(saved["credentialFile"])


if __name__ == "__main__":
    unittest.main()
