"""Trusted yield handshake. Questions contain no credential, grant or executable source reference."""
import hashlib
import json
import os
from pathlib import Path
import socket
import uuid


class YieldRequested(RuntimeError):
    pass


def storage_capability(repository):
    root = os.path.normcase(str(Path(repository).resolve(strict=True)))
    return "checkpoint-store:" + hashlib.sha256((socket.gethostname().lower() + "\n" + root).encode()).hexdigest()


def ask(broker, args):
    # invoke holds the broker lock: all earlier admitted tools have completed, and queued tools
    # will observe the closed flag. The trusted checkpoint operation may still use existing Git authority.
    if broker.pending_question is not None:
        raise YieldRequested("QUESTION_ALREADY_PENDING")
    if broker.checkpoint is None:
        raise RuntimeError("QUESTION_CHECKPOINT_ADAPTER_REQUIRED")
    broker.accepting_tools = False
    result = broker.checkpoint()
    checkpoint = {"repositoryRef": broker.repository, "baseRevision": broker.task["workspace"]["baseRevision"],
                  "resultRevision": result["resultRevision"], "changedFiles": result["changedFiles"],
                  "configurationDigest": broker.context["configurationDigest"], "storageCapability": storage_capability(broker.source_root)}
    source = broker.client._post(broker.base + "/questions/source", broker.lease)
    payload = {"protocol": "aiw.questions.v1", "sourceQuestionKey": str(uuid.uuid4()), "source": source, "lease": broker.lease,
               "question": args["question"], "context": args["context"], "options": args["options"],
               "answerRequirements": args["answerRequirements"], "checkpoint": checkpoint}
    broker.attempt.evidence.record("question-intent", questionKey=payload["sourceQuestionKey"], status="pending")
    try:
        receipt = broker.client._post(broker.base + "/questions/open", payload)
    except Exception:
        broker.attempt.resource_uncertain = True
        raise
    broker.pending_question = receipt
    return {"questionId": receipt["question"]["questionId"], "state": "WAITING_FOR_QUIESCENCE", "instruction": "Stop. Do not call more tools or start another model turn."}


def acknowledge(broker):
    # Called only after the runtime returned and closed its model/client/process resources.
    with broker.lock:
        if not broker.pending_question or broker._active or broker.attempt.resource_uncertain:
            raise RuntimeError("QUESTION_QUIESCENCE_UNCONFIRMED")
        if broker.attempt.reason != "stop-requested":
            broker.attempt.check()
        broker.client._post(broker.base + "/questions/ack", broker.pending_question["yield"])
        broker.attempt.evidence.record("question-yield", questionId=broker.pending_question["question"]["questionId"], status="acknowledged")


def verify_checkpoints(broker):
    records = broker.client._post(broker.base + "/questions/checkpoints", broker.lease)
    expected = [ref for ref in broker.context["snapshot"]["references"] if ref["kind"] == "checkpoint"]
    if len(records) != len(expected):
        raise RuntimeError("CHECKPOINT_REFERENCE_MISSING")
    for record in records:
        ref, checkpoint = record["reference"], record["checkpoint"]
        if ref not in expected or checkpoint["storageCapability"] != storage_capability(broker.source_root):
            raise RuntimeError("CHECKPOINT_STORAGE_NOT_AVAILABLE")
        raw = record["checkpointJson"]
        if json.loads(raw) != checkpoint or ref["sha256"] != hashlib.sha256(raw.encode()).hexdigest() or ref["revision"] != checkpoint["resultRevision"]:
            raise RuntimeError("CHECKPOINT_CONTENT_CHANGED")
        # Only the configured local object store is consulted. There is no fetch/download fallback.
        from execution_guard import ControlledGit
        git = ControlledGit(broker.source_root, broker.attempt)
        if git._run("rev-parse", "--verify", checkpoint["resultRevision"] + "^{commit}").strip().lower() != checkpoint["resultRevision"].lower():
            raise RuntimeError("CHECKPOINT_COMMIT_NOT_AVAILABLE")
