"""Read a caller-selected, offline SQLite snapshot without changing it."""

from __future__ import annotations

import hashlib
import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path


MAX_DATABASE_BYTES = 256 * 1024 * 1024
MAX_ROWS = 100_000
MAX_JSON_BYTES = 16 * 1024 * 1024

# Closed V8/V9 vocabulary. Never derive SQL identifiers from a package or caller.
TABLES = {
    "secretary_lock": "id version",
    "secretary_work": "id actor revision generation state plan_id due_ms payload updated_ms",
    "secretary_request": "id work_id generation phase plan_version source_attempt consumed",
    "secretary_participant": "work_id worker_id role",
    "secretary_admitted_task": "task_id work_id generation",
    "secretary_message": "id work_id actor input_hash user_text reply created_ms source_revision",
    "delegation_lock": "id version",
    "delegation_security": "id legacy_auth_required",
    "delegation_legacy_request": "id server_boot method path state started_ms finished_ms",
    "delegation_identity": "worker_id key_hash generation enabled_boot changed_by changed_ms",
    "delegation_target": "target_key generation admitted_ms",
    "delegation_grant": "id owner worker_id revision state original_text rule_json authority template_id template_revision effective_ms expires_ms",
    "delegation_publication_plan": "plan_id target_key",
    "delegation_publication_attempt": "task_id allowed_attempt grant_id grant_revision worker_generation enabled_boot",
    "delegation_history": "grant_id revision snapshot changed_by changed_ms",
    "delegation_budget": "target_key action limit_value used_value reserved_value",
    "delegation_request": "id grant_id grant_revision worker_id worker_generation target_key target_generation snapshot fingerprint task_id state detail created_ms updated_ms",
    "delegation_audit": "id target_key grant_id request_id actor event detail created_ms",
    "delegation_template": "id owner revision original_text rule_json source_grant source_revision created_ms",
    "delegation_draft": "id owner original_text created_ms",
    "delegation_template_history": "template_id revision owner snapshot created_ms",
    "delegation_model_constraint": "task_id grant_id grant_revision catalog_version profiles_json",
    "delegation_task": "task_id target_key allowed_attempt reserved started settled",
    "delegation_model_scope": "target_key role catalog_version profiles_json grant_id grant_revision",
}


class MigrationError(ValueError):
    """A safe, non-sensitive message suitable for a planning CLI."""


def canonical(value: object) -> bytes:
    try:
        return json.dumps(value, ensure_ascii=False, sort_keys=True,
                          separators=(",", ":"), allow_nan=False).encode("utf-8")
    except (TypeError, ValueError) as exc:
        raise MigrationError("Unsupported value in migration data") from exc


def digest(value: object) -> str:
    return hashlib.sha256(canonical(value)).hexdigest()


def _unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise MigrationError("Duplicate JSON object key")
        result[key] = value
    return result


def read_json(path: Path) -> object:
    if not path.is_file() or path.stat().st_size > MAX_JSON_BYTES:
        raise MigrationError("JSON input must be an existing bounded file")
    try:
        raw = path.read_bytes()
        if len(raw) > MAX_JSON_BYTES:
            raise MigrationError("JSON input exceeds the size limit")
        return json.loads(raw.decode("utf-8-sig"), object_pairs_hook=_unique_keys,
                          parse_constant=lambda _: (_ for _ in ()).throw(
                              MigrationError("Non-finite JSON number")))
    except (UnicodeError, json.JSONDecodeError, RecursionError) as exc:
        raise MigrationError("Invalid UTF-8 JSON input") from exc


def _stamp(path: Path) -> tuple:
    stat = path.stat()
    return stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime_ns


@contextmanager
def readonly_snapshot(path: Path):
    path = path.resolve(strict=True)
    if not path.is_file() or path.stat().st_size > MAX_DATABASE_BYTES:
        raise MigrationError("Source must be a bounded offline SQLite snapshot")
    # immutable reads never create WAL/SHM files. They are safe only for an
    # offline snapshot: reject WAL/journal inputs instead of ignoring commits.
    if any(Path(str(path) + suffix).exists() for suffix in ("-wal", "-shm", "-journal")):
        raise MigrationError("Live/WAL databases are not accepted; supply an offline backup snapshot")
    before = _stamp(path)
    connection = None
    try:
        connection = sqlite3.connect(path.as_uri() + "?mode=ro&immutable=1", uri=True)
        connection.row_factory = sqlite3.Row
        connection.enable_load_extension(False)
        connection.execute("PRAGMA query_only=ON")
        connection.execute("PRAGMA trusted_schema=OFF")
        connection.execute("BEGIN")
    except sqlite3.Error as exc:
        if connection is not None:
            connection.close()
        raise MigrationError("Cannot open the selected offline SQLite snapshot") from exc
    try:
        yield connection
        if before != _stamp(path) or any(
            Path(str(path) + suffix).exists() for suffix in ("-wal", "-shm", "-journal")
        ):
            raise MigrationError("Snapshot changed while being read; discard this plan")
    except sqlite3.Error as exc:
        raise MigrationError("Snapshot schema or data cannot be safely read") from exc
    finally:
        connection.close()


def read_source(path: Path) -> tuple[dict, dict]:
    rows = {}
    inventory = {}
    with readonly_snapshot(path) as connection:
        schema = {row[0]: (row[1], row[2]) for row in connection.execute(
            "SELECT name,type,sql FROM sqlite_schema WHERE name NOT LIKE 'sqlite_%'"
        )}
        present = set(schema) & set(TABLES)
        if not present or not {"secretary_work", "secretary_message"}.issubset(present):
            raise MigrationError("Expected V8 secretary schema was not found")
        if any(name.startswith("delegation_") for name in present) and not {
            name for name in TABLES if name.startswith("delegation_")
        }.issubset(present):
            raise MigrationError("Partial V9 delegation schema is not accepted")
        if not {name for name in TABLES if name.startswith("secretary_")}.issubset(present):
            raise MigrationError("Partial V8 secretary schema is not accepted")
        total = 0
        for table, columns in TABLES.items():
            if table not in present:
                continue
            kind, sql = schema[table]
            if kind != "table" or not sql or "VIRTUAL TABLE" in sql.upper():
                raise MigrationError("Migration inputs must contain ordinary tables, not views")
            actual = [row[1] for row in connection.execute(f'PRAGMA table_info("{table}")')]
            if set(actual) != set(columns.split()):
                raise MigrationError("Unsupported V8/V9 column layout; no heuristic migration allowed")
            selected = ",".join(f'"{column}"' for column in columns.split())
            table_rows = []
            for row in connection.execute(f'SELECT {selected} FROM "{table}"'):
                total += 1
                if total > MAX_ROWS:
                    raise MigrationError("Snapshot exceeds the planning row limit")
                data = dict(row)
                if any(value is not None and type(value) not in (int, str) for value in data.values()):
                    raise MigrationError("Unexpected SQLite value type; no implicit conversion allowed")
                table_rows.append(data)
            table_rows.sort(key=canonical)
            rows[table] = table_rows
            # Never put private field values, actor labels or credentials in inventory.
            inventory[table] = {"rows": len(table_rows), "columns": columns.split()}
        unknown = sorted(set(schema) - set(TABLES) - {"flyway_schema_history"})
    return rows, {"tables": inventory, "unexaminedTables": len(unknown),
                  "snapshotDigest": digest(rows), "sourceSchema": "V8+V9" if "delegation_grant" in rows else "V8"}
