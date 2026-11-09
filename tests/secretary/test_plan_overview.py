"""Offline tests for the Secretary's read-only Plan overview."""
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))
from platform_agent_actions import PlatformAgentActions
from platform_client import PlatformError
from authorization_requests import AuthorizationRequired


class PlanOverviewTest(unittest.TestCase):
    def action(self, profile="LOCAL"):
        backend = SimpleNamespace(profile=profile, session_started=True)
        platform = SimpleNamespace(backend=backend, request=Mock())
        setup = SimpleNamespace(check=Mock(return_value={"enabled": True}))
        runtime = SimpleNamespace(enabled=True, platform=platform, platform_setup=setup, store=Mock())
        service = PlatformAgentActions.__new__(PlatformAgentActions)
        service.runtime, service.store = runtime, runtime.store
        return service, runtime

    def test_local_query_restores_session_and_retries_only_read(self):
        service, runtime = self.action()
        runtime.platform.request.side_effect = [PlatformError(401), {"plans": [], "truncated": False}]
        result = service.perform({"id": "work"}, {"id": "job"}, {"action": "platform_plan_overview"})
        self.assertEqual(result, {"plans": [], "truncated": False})
        self.assertEqual(runtime.platform.request.call_count, 2)
        runtime.platform_setup.check.assert_called_once_with()

    def test_remote_auth_failure_does_not_reconnect_or_replay(self):
        service, runtime = self.action("REMOTE")
        runtime.platform.request.side_effect = PlatformError(401)
        with self.assertRaises(AuthorizationRequired) as raised:
            service.perform({"id": "work"}, {"id": "job"}, {"action": "platform_plan_overview"})
        self.assertEqual(raised.exception.reason, "PLATFORM_SESSION_REQUIRED")
        self.assertEqual(runtime.platform.request.call_count, 1)
        runtime.platform_setup.check.assert_not_called()

    def test_permission_denial_is_not_a_connection_retry(self):
        service, runtime = self.action()
        runtime.platform.request.side_effect = PlatformError(403)
        with self.assertRaises(AuthorizationRequired) as raised:
            service.perform({"id": "work"}, {"id": "job"}, {"action": "platform_plan_overview"})
        self.assertEqual(raised.exception.reason, "PLATFORM_PERMISSION_REQUIRED")
        self.assertEqual(runtime.platform.request.call_count, 1)
        runtime.platform_setup.check.assert_not_called()


if __name__ == "__main__":
    unittest.main()
