"""Pure risk decision. Facts must come from the host, never from model arguments.

This module does not grant authority or execute operations. It is not connected
to an execution path until the durable approval protocol is available.
"""
from dataclasses import dataclass
from enum import Enum


class Decision(str, Enum):
    ALLOW = "ALLOW"
    CONFIRM = "CONFIRM"


@dataclass(frozen=True)
class Facts:
    operation: str
    # None means the host could not establish the fact; never infer permission.
    inside_workspace: bool | None = None
    task_scope_verified: bool | None = None
    version_matches: bool | None = None
    new_file: bool | None = None
    task_created_or_generated: bool | None = None
    tracked_and_clean: bool | None = None
    multiple_or_recursive: bool | None = None
    command_safe: bool | None = None


def classify(facts: Facts) -> Decision:
    if facts.inside_workspace is not True:
        return Decision.CONFIRM
    if facts.operation in {"read", "list", "search"}:
        return Decision.ALLOW
    if facts.operation == "create" and facts.new_file is True:
        return Decision.ALLOW
    if facts.operation == "edit" and facts.task_scope_verified is True and facts.version_matches is True:
        return Decision.ALLOW
    if facts.operation == "delete" and facts.multiple_or_recursive is False and facts.version_matches is True:
        if facts.task_created_or_generated is True:
            return Decision.ALLOW
        if facts.task_scope_verified is True and facts.tracked_and_clean is True:
            return Decision.ALLOW
    if facts.operation == "command" and facts.command_safe is True and facts.task_scope_verified is True:
        return Decision.ALLOW
    # Whole-file replacement, outbound publication, ambiguous commands, and
    # nonrecoverable or unscoped deletion remain explicit one-shot decisions.
    return Decision.CONFIRM
