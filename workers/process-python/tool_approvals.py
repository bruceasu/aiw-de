"""Guarded Worker approval request handoff. Decisions never become permissions here."""


def acknowledge(broker):
    with broker.lock:
        pending = broker.pending_approval
        if not pending or broker._active or broker.attempt.resource_uncertain:
            raise RuntimeError("TOOL_APPROVAL_QUIESCENCE_UNCONFIRMED")
        if broker.attempt.reason != "stop-requested":
            broker.attempt.check()
        broker.client._post(broker.base + "/yield/ack", pending["yield"])
        broker.attempt.evidence.record("approval-yield", approvalId=pending["approvalId"], status="acknowledged")
