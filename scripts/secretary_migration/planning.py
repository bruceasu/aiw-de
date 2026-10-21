"""Ownership-safe, non-executing export/import plans for FD-024."""

from __future__ import annotations

import re
import uuid
from collections import Counter, defaultdict, deque
from pathlib import Path

from .source import MigrationError, canonical, digest, read_source, readonly_snapshot


SCHEMA = "aiw.secretary.migration.v1"
MAP_SCHEMA = "aiw.secretary.owner-map.v1"
IDENTIFIER = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.:@/-]{0,255}\Z")

# Only these exact columns may reach a future private archival store. Authority,
# enabled boots, budgets, task permits and mixed audit/request snapshots stay put.
PRIVATE_COLUMNS = {
    "secretary_work": "id revision generation state plan_id due_ms payload updated_ms",
    "secretary_message": "id work_id input_hash user_text reply created_ms source_revision",
    "secretary_request": "id work_id generation phase plan_version source_attempt consumed",
    "delegation_grant": "id revision state original_text rule_json template_id template_revision effective_ms expires_ms",
    "delegation_template": "id revision original_text rule_json source_grant source_revision created_ms",
    "delegation_draft": "id original_text created_ms",
}
MIXED = {"delegation_history", "delegation_request", "delegation_audit", "delegation_template_history"}


def require_identifier(value: object) -> str:
    if not isinstance(value, str) or not IDENTIFIER.fullmatch(value):
        raise MigrationError("Invalid explicit owner or Worker identity")
    return value


def owner_mapping(value: object) -> dict:
    if not isinstance(value, dict) or set(value) != {"schema", "mappings"} or value["schema"] != MAP_SCHEMA:
        raise MigrationError("Explicit versioned owner-map is required")
    if not isinstance(value["mappings"], list) or len(value["mappings"]) > 1000:
        raise MigrationError("Owner-map must contain a bounded mappings list")
    result = {}
    destinations = {}
    for mapping in value["mappings"]:
        if not isinstance(mapping, dict) or set(mapping) != {"sourceActor", "ownerId", "workerId", "evidenceRef"}:
            raise MigrationError("Owner-map fields must be exact; implicit/default mappings are forbidden")
        actor = mapping["sourceActor"]
        if not isinstance(actor, str) or not actor.strip() or len(actor) > 256 or actor != actor.strip():
            raise MigrationError("A nonblank exact source actor is required")
        if actor in result:
            raise MigrationError("Duplicate source actor mapping")
        owner = require_identifier(mapping["ownerId"])
        worker = require_identifier(mapping["workerId"])
        evidence = mapping["evidenceRef"]
        if not isinstance(evidence, str) or not evidence.strip() or len(evidence) > 1024:
            raise MigrationError("Each mapping needs an external ownership evidence reference")
        if owner in destinations and destinations[owner] != worker:
            raise MigrationError("One export owner must have one explicitly selected destination Worker")
        destinations[owner] = worker
        result[actor] = dict(mapping)
    return result


def _index(rows: list[dict], key: str) -> dict:
    result = {}
    for row in rows:
        identity = row[key]
        if not isinstance(identity, str) or not identity or identity in result:
            raise MigrationError("Source contains missing or ambiguous record identities")
        result[identity] = row
    return result


def _source_identity(table: str, row: dict) -> str:
    return digest({"table": table, "id": row["id"]})


def make_export_candidate(source: Path, mapping_document: object,
                          owner_id: str, batch_id: str) -> tuple[dict, dict]:
    """Build an in-memory archival candidate; this function never writes a file."""
    require_identifier(owner_id)
    try:
        if str(uuid.UUID(batch_id)) != batch_id:
            raise ValueError()
    except (ValueError, AttributeError) as exc:
        raise MigrationError("An explicit canonical UUID batch ID is required") from exc
    mapping = owner_mapping(mapping_document)
    destinations = {entry["workerId"] for entry in mapping.values() if entry["ownerId"] == owner_id}
    if len(destinations) != 1:
        raise MigrationError("Selected owner needs one explicit destination in the mapping")
    rows, inventory = read_source(source)
    work = _index(rows["secretary_work"], "id")
    grants = _index(rows.get("delegation_grant", []), "id")
    templates = _index(rows.get("delegation_template", []), "id")
    # Identity resolution follows source relationships, never current login or filename.
    work_owner = {key: mapping.get(row["actor"]) for key, row in work.items()}
    grant_owner = {key: mapping.get(row["owner"]) for key, row in grants.items()}
    counts = {table: Counter() for table in rows}
    records = []
    blockers = Counter()
    for table, table_rows in rows.items():
        for row in table_rows:
            if table not in PRIVATE_COLUMNS:
                counts[table]["sealedMixed" if table in MIXED else "retainedPlatform"] += 1
                continue
            selected = None
            valid = True
            if table == "secretary_work":
                selected = work_owner[row["id"]]
            elif table in {"secretary_request", "secretary_message"}:
                selected = work_owner.get(row["work_id"])
                valid = row["work_id"] in work
                if table == "secretary_message":
                    actor = mapping.get(row["actor"])
                    valid = valid and selected is not None and actor is not None and actor["ownerId"] == selected["ownerId"]
            elif table == "delegation_grant":
                selected = grant_owner[row["id"]]
                template = row["template_id"]
                if template is not None:
                    parent = templates.get(template)
                    valid = parent is not None and parent["owner"] == row["owner"] and parent["revision"] == row["template_revision"]
            else:
                selected = mapping.get(row["owner"])
                if table == "delegation_template" and row["source_grant"] is not None:
                    parent = grants.get(row["source_grant"])
                    valid = parent is not None and parent["owner"] == row["owner"]
            if selected is None or not valid:
                counts[table]["sealedUnverified"] += 1
                continue
            if selected["ownerId"] != owner_id:
                counts[table]["otherOwner"] += 1
                continue
            counts[table]["candidatePrivate"] += 1
            payload = {column: row[column] for column in PRIVATE_COLUMNS[table].split()}
            records.append({"table": table, "sourceKey": _source_identity(table, row),
                            "activation": "ARCHIVE_ONLY", "data": payload})
    by_key = {(record["table"], record["data"]["id"]): record for record in records}
    if len(by_key) != len(records):
        raise MigrationError("Ambiguous source record identity")
    reverse = defaultdict(list)
    invalid = set()
    for key, record in by_key.items():
        table, data = record["table"], record["data"]
        dependencies = []
        if table in {"secretary_request", "secretary_message"}:
            dependencies.append(("secretary_work", data["work_id"]))
        if table == "delegation_grant" and data["template_id"] is not None:
            dependencies.append(("delegation_template", data["template_id"]))
        if table == "delegation_template" and data["source_grant"] is not None:
            dependencies.append(("delegation_grant", data["source_grant"]))
        for parent in dependencies:
            reverse[parent].append(key)
            if parent not in by_key:
                invalid.add(key)
    queue = deque(invalid)
    while queue:
        for child in reverse[queue.popleft()]:
            if child not in invalid:
                invalid.add(child)
                queue.append(child)
    if invalid:
        for table, _ in invalid:
            counts[table]["candidatePrivate"] -= 1
            counts[table]["sealedUnverified"] += 1
        records = [record for key, record in by_key.items() if key not in invalid]
    for row in rows.get("secretary_work", []):
        if row["state"] not in {"DRAFT", "DONE", "CANCELLED"}:
            blockers["nonterminalSecretaryWork"] += 1
    for row in rows.get("delegation_request", []):
        if row["state"] in {"PENDING", "HUMAN"}:
            blockers["unresolvedDelegationRequest"] += 1
    for row in rows.get("delegation_task", []):
        if row["reserved"] or row["started"] and not row["settled"]:
            blockers["unsettledTask"] += 1
    blockers["publicationPermitsRequireReconciliation"] += len(rows.get("delegation_publication_attempt", []))
    blockers["unknownOrMixedOwnership"] += sum(
        count["sealedUnverified"] + count["sealedMixed"] for count in counts.values()
    )
    records.sort(key=lambda record: (record["table"], record["sourceKey"]))
    manifest = {
        "schema": SCHEMA, "batchId": batch_id, "ownerId": owner_id,
        "workerId": next(iter(destinations)), "mappingDigest": digest(mapping_document),
        "sourceSchema": inventory["sourceSchema"], "sourceSnapshotDigest": inventory["snapshotDigest"],
        "recordCount": len(records), "recordsDigest": digest(records),
        "activation": "ARCHIVE_ONLY", "platformAuthorityIncluded": False,
    }
    candidate = {"manifest": manifest, "records": records}
    candidate["packageDigest"] = digest(candidate)
    validate_package(candidate, owner_id, manifest["workerId"])
    plan = {
        "schema": "aiw.secretary.migration-plan.v1", "operation": "export-plan",
        "manifest": manifest, "packageDigest": candidate["packageDigest"],
        "inventory": inventory, "disposition": {table: dict(count) for table, count in counts.items()},
        "blockers": {key: value for key, value in sorted(blockers.items()) if value},
        "executionEnabled": False, "cutoverEnabled": False, "sourceWritten": False,
        "ownershipEvidence": "CALLER_ASSERTION_REQUIRES_EXTERNAL_VERIFICATION",
        "mixedRecords": "SEALED_IN_SOURCE; NO_AUTOMATIC_SNAPSHOT_SPLITTING",
        "warnings": ["Snapshot-only inventory cannot prove the live system is paused or fully reconciled",
                     "All professional tasks and external effects need a separate live cutover inventory"],
    }
    return candidate, plan


def validate_package(candidate: object, owner_id: str, worker_id: str) -> dict:
    require_identifier(owner_id)
    require_identifier(worker_id)
    if not isinstance(candidate, dict) or set(candidate) != {"manifest", "records", "packageDigest"}:
        raise MigrationError("Invalid migration package structure")
    manifest = candidate["manifest"]
    fields = {"schema", "batchId", "ownerId", "workerId", "mappingDigest", "sourceSchema",
              "sourceSnapshotDigest", "recordCount", "recordsDigest", "activation", "platformAuthorityIncluded"}
    if not isinstance(manifest, dict) or set(manifest) != fields or manifest["schema"] != SCHEMA:
        raise MigrationError("Unsupported migration package manifest")
    if manifest["ownerId"] != owner_id or manifest["workerId"] != worker_id:
        raise MigrationError("Package does not match the explicit destination owner and Worker")
    if manifest["activation"] != "ARCHIVE_ONLY" or manifest["platformAuthorityIncluded"] is not False:
        raise MigrationError("Authority-bearing or activating packages are forbidden")
    try:
        if str(uuid.UUID(manifest["batchId"])) != manifest["batchId"]:
            raise ValueError()
    except (ValueError, AttributeError, TypeError) as exc:
        raise MigrationError("Invalid package batch identity") from exc
    if manifest["sourceSchema"] not in {"V8", "V8+V9"}:
        raise MigrationError("Unsupported source schema")
    if any(not isinstance(value, str) or not re.fullmatch(r"[a-f0-9]{64}", value) for value in (
        manifest["mappingDigest"], manifest["sourceSnapshotDigest"], manifest["recordsDigest"], candidate["packageDigest"]
    )):
        raise MigrationError("Manifest digests must be exact SHA-256 values")
    records = candidate["records"]
    if not isinstance(records, list) or len(records) > 100_000 or type(manifest["recordCount"]) is not int:
        raise MigrationError("Invalid package row count")
    if len(records) != manifest["recordCount"] or digest(records) != manifest["recordsDigest"]:
        raise MigrationError("Package records do not match their count or digest")
    if digest({"manifest": manifest, "records": records}) != candidate["packageDigest"]:
        raise MigrationError("Package digest mismatch")
    identities = set()
    work_ids = set()
    grant_ids = set()
    template_ids = set()
    for record in records:
        if not isinstance(record, dict) or set(record) != {"table", "sourceKey", "activation", "data"}:
            raise MigrationError("Invalid archival record structure")
        table = record["table"]
        if not isinstance(table, str) or table not in PRIVATE_COLUMNS or record["activation"] != "ARCHIVE_ONLY":
            raise MigrationError("Package contains unsupported, mixed or platform-owned data")
        data = record["data"]
        if not isinstance(data, dict) or set(data) != set(PRIVATE_COLUMNS[table].split()):
            raise MigrationError("Package record columns do not match the closed private schema")
        if any(value is not None and type(value) not in (int, str) for value in data.values()):
            raise MigrationError("Unsupported archival field type")
        if not isinstance(data["id"], str) or not data["id"] or record["sourceKey"] != _source_identity(table, data):
            raise MigrationError("Invalid archival source identity")
        identity = (table, data["id"])
        if identity in identities:
            raise MigrationError("Duplicate archival record identity")
        identities.add(identity)
        if table == "secretary_work":
            work_ids.add(data["id"])
        elif table == "delegation_grant":
            grant_ids.add(data["id"])
        elif table == "delegation_template":
            template_ids.add(data["id"])
    for record in records:
        table, data = record["table"], record["data"]
        if table in {"secretary_request", "secretary_message"} and data["work_id"] not in work_ids:
            raise MigrationError("Orphaned secretary record in package")
        if table == "delegation_grant" and data["template_id"] is not None and data["template_id"] not in template_ids:
            raise MigrationError("Orphaned grant template association")
        if table == "delegation_template" and data["source_grant"] is not None and data["source_grant"] not in grant_ids:
            raise MigrationError("Orphaned template grant association")
    return manifest


def plan_import(candidate: object, owner_id: str, worker_id: str, destination: Path | None = None) -> dict:
    manifest = validate_package(candidate, owner_id, worker_id)
    disposition = "NEW_ARCHIVAL_BATCH"
    if destination is not None:
        # Never create a destination, infer its owner, or update its ledger.
        with readonly_snapshot(destination) as connection:
            identity = connection.execute(
                "SELECT owner_id,worker_id FROM personal_identity WHERE id=1"
            ).fetchall()
            if len(identity) != 1 or tuple(identity[0]) != (owner_id, worker_id):
                raise MigrationError("Destination identity does not match the selected owner and Worker")
            existing = connection.execute(
                "SELECT package_digest,owner_id,worker_id FROM personal_import_batch WHERE batch_id=?",
                (manifest["batchId"],)
            ).fetchall()
            if len(existing) > 1:
                raise MigrationError("Ambiguous destination import batch")
            if existing:
                if tuple(existing[0]) != (candidate["packageDigest"], owner_id, worker_id):
                    raise MigrationError("Batch identity already belongs to a different package or destination")
                disposition = "ALREADY_IMPORTED_NO_CHANGE"
    return {"schema": "aiw.secretary.migration-plan.v1", "operation": "import-plan",
            "manifest": manifest, "packageDigest": candidate["packageDigest"],
            "disposition": disposition, "executionEnabled": False, "cutoverEnabled": False,
            "destinationWritten": False, "activation": "ARCHIVE_ONLY", "platformTablesWritten": [],
            "ownershipEvidence": "PACKAGE_ASSERTION_REQUIRES_EXTERNAL_VERIFICATION"}


def execute_disabled(operation: str) -> None:
    raise MigrationError(f"{operation} execution is disabled for FD-024; only read-only planning is available")
