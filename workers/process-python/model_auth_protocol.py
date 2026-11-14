"""Same-device OAuth protocol and protected credential files. JSON in/out, no secret logging."""
from __future__ import annotations

from contextlib import contextmanager
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.parse
import urllib.request
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "personal-secretary"))
from private_config import PrivateProcessLock, read_private, write_private
from secretary_oauth import ISSUER, RESOURCE, SCOPES, NoRedirect, request_json, verify

CODEX_CLIENT = "app_EMoamEEZ73f0CkXaXp7hrann"


def validate(tokens, kind, client, nonce=None, subject=None):
    if not isinstance(client, str) or not 1 <= len(client) <= 256 or client == "dynamic_agent_client":
        raise ValueError("ISSUED_CLIENT_REQUIRED")
    if str(tokens.get("token_type", "")).lower() != "bearer":
        raise ValueError("MODEL_TOKEN_TYPE_INVALID")
    discovery = request_json(ISSUER + "/.well-known/openid-configuration")
    if discovery.get("issuer") != ISSUER:
        raise ValueError("MODEL_TOKEN_ISSUER_INVALID")
    keys = request_json(discovery.get("jwks_uri", "")).get("keys")
    if not isinstance(keys, list) or len(keys) > 32:
        raise ValueError("MODEL_TOKEN_KEYS_INVALID")
    identity = verify(tokens.get("id_token"), client, keys)
    access = verify(tokens.get("access_token"), RESOURCE, keys)
    if (not identity.get("sub") or identity["sub"] != access.get("sub")
            or subject is not None and subject != identity["sub"]
            or access.get("client_id") != client
            or nonce is not None and not hmac.compare_digest(str(identity.get("nonce", "")), nonce)):
        raise ValueError("MODEL_ACCOUNT_OR_CLIENT_CHANGED")
    if any(c.get("azp") is not None and c["azp"] != client for c in (identity, access)):
        raise ValueError("MODEL_TOKEN_AUTHORIZED_PARTY_INVALID")
    if isinstance(identity.get("aud"), list) and len(identity["aud"]) > 1 and identity.get("azp") != client:
        raise ValueError("MODEL_TOKEN_AUTHORIZED_PARTY_INVALID")
    scopes = tokens.get("scope")
    if not isinstance(scopes, str) or not set(scopes.split()).issubset(str(access.get("scope", "")).split()):
        raise ValueError("MODEL_TOKEN_SCOPE_MISMATCH")
    for name in ("access_token", "refresh_token", "id_token"):
        if not isinstance(tokens.get(name), str) or not 1 <= len(tokens[name]) <= 32768:
            raise ValueError("MODEL_TOKEN_FIELD_REQUIRED")
    lifetime = tokens.get("expires_in")
    if type(lifetime) is not int or not 1 <= lifetime <= 86400:
        raise ValueError("MODEL_TOKEN_EXPIRY_INVALID")
    now = time.time()
    expires = min(now + lifetime, access["exp"], identity["exp"])
    if expires <= now + 30:
        raise ValueError("MODEL_TOKEN_EXPIRED")
    earliest = tokens.get("earliest_refresh_at", 0)
    if isinstance(earliest, str):
        from datetime import datetime
        parsed = datetime.fromisoformat(earliest.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("MODEL_REFRESH_TIME_INVALID")
        earliest = parsed.timestamp()
    if type(earliest) not in (int, float) or not 0 <= earliest <= expires:
        raise ValueError("MODEL_REFRESH_TIME_INVALID")
    account = plan = None
    if kind == "CODEX_APP_SERVER":
        metadata = access.get("https://api.openai.com/auth")
        if client != CODEX_CLIENT or not isinstance(metadata, dict) or not isinstance(metadata.get("chatgpt_account_id"), str) or not metadata["chatgpt_account_id"]:
            raise ValueError("CODEX_CLIENT_OR_ACCOUNT_REQUIRED")
        account, plan = metadata["chatgpt_account_id"], metadata.get("chatgpt_plan_type")
    elif kind != "CHATGPT_RESPONSES":
        raise ValueError("MODEL_CONNECTION_KIND_INVALID")
    return {"kind": kind, "clientId": client, "subject": identity["sub"], "accountId": account,
            "planType": plan, "expiresAt": int(expires * 1000), "earliestRefreshAt": int(earliest * 1000),
            "inferenceAllowed": kind == "CODEX_APP_SERVER" or {"resource.invoke", "chatgpt.tokens.use.direct"}.issubset(scopes.split()),
            "tokens": {name: tokens[name] for name in ("access_token", "refresh_token", "id_token", "scope")}, "state": "READY"}


def projection(record, reference):
    return {**{k: record[k] for k in ("kind", "clientId", "subject", "accountId", "planType", "expiresAt", "earliestRefreshAt", "inferenceAllowed")},
            "credentialFile": reference, "refreshDigest": hashlib.sha256(record["tokens"]["refresh_token"].encode()).hexdigest()}


class Credentials:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)

    def path(self, reference):
        if not isinstance(reference, str) or not re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\.json", reference):
            raise ValueError("MODEL_CREDENTIAL_FILE_INVALID")
        path = self.directory / reference
        if path.is_symlink():
            raise ValueError("MODEL_CREDENTIAL_FILE_INVALID")
        return path

    @contextmanager
    def locked(self, reference):
        path = self.path(reference)
        lock = PrivateProcessLock(path.with_suffix(".lock"))
        lock.acquire()
        try:
            record = read_private(path)
            if not isinstance(record, dict) or record.get("format") != "aiw-model-credential-v1":
                raise ValueError("MODEL_CREDENTIAL_FILE_MISSING_OR_INVALID")
            yield path, record
        finally:
            lock.release()

    def save(self, record):
        reference = str(uuid.uuid4()) + ".json"
        write_private(self.path(reference), {**record, "format": "aiw-model-credential-v1"})
        return projection(record, reference)

    def access(self, reference):
        with self.locked(reference) as (_, record):
            if record["state"] != "READY" or record["expiresAt"] <= time.time() * 1000:
                raise ValueError("MODEL_AUTH_REQUIRED")
            return {"accessToken": record["tokens"]["access_token"]}

    def refresh(self, input):
        reference = input["credentialFile"]
        with self.locked(reference) as (path, old):
            if old["state"] != "READY" or any(old[key] != input[key] for key in ("kind", "clientId", "subject")):
                raise ValueError("MODEL_REFRESH_STATE_OR_IDENTITY_CHANGED")
            if old["earliestRefreshAt"] > time.time() * 1000:
                raise ValueError("MODEL_REFRESH_TOO_EARLY")
            # Persist uncertainty before sending a rotating credential. Never automatically resend it.
            write_private(path, {**old, "state": "REFRESHING"})
            try:
                params = {"grant_type": "refresh_token", "client_id": old["clientId"], "refresh_token": old["tokens"]["refresh_token"]}
                if old["kind"] == "CHATGPT_RESPONSES":
                    params["resource"] = RESOURCE
                tokens = request_json(ISSUER + ("/api/accounts/oauth/token" if old["kind"] == "CHATGPT_RESPONSES" else "/oauth/token"), params)
                tokens.setdefault("scope", old["tokens"]["scope"])
                current = validate(tokens, old["kind"], old["clientId"], subject=old["subject"])
                if current["accountId"] != old["accountId"]:
                    raise ValueError("MODEL_ACCOUNT_CHANGED")
                replacement = self.save(current)
                write_private(path, {"format": "aiw-model-credential-v1", "state": "REPLACED"})
                return replacement
            except Exception:
                write_private(path, {**old, "state": "REFRESH_OUTCOME_UNKNOWN"})
                raise ValueError("MODEL_AUTH_REFRESH_OUTCOME_UNKNOWN") from None

    def revoke(self, reference):
        with self.locked(reference) as (path, record):
            # Local revocation always takes effect, even when the provider does not confirm it.
            write_private(path, {"format": "aiw-model-credential-v1", "state": "REVOKED"})
            try:
                discovery = request_json(ISSUER + "/.well-known/openid-configuration")
                if discovery.get("issuer") != ISSUER:
                    return False
                request_json(discovery.get("revocation_endpoint", ""), {"client_id": record["clientId"], "token": record["tokens"]["refresh_token"], "token_type_hint": "refresh_token"})
                return True
            except Exception:
                return False

    def discard(self, reference):
        with self.locked(reference) as (path, _):
            write_private(path, {"format": "aiw-model-credential-v1", "state": "REVOKED"})


def run(input):
    operation = input["operation"]
    if operation == "authorize":
        params = {"client_id": input["client"], "ext_agent_host_id": input["host"], "response_type": "code",
                  "redirect_uri": input["redirect"], "scope": SCOPES, "resource": RESOURCE, "state": input["state"],
                  "nonce": input["nonce"], "code_challenge_method": "S256", "code_challenge": input["challenge"]}
        if input["client"] == "dynamic_agent_client":
            params["agent_name_hint"] = "AIW DE"
        return {"url": ISSUER + "/api/accounts/authorize?" + urllib.parse.urlencode(params)}
    if operation == "models":
        base = (input["base"] or RESOURCE).rstrip("/")
        parsed = urllib.parse.urlsplit(base)
        if (not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment
                or not (parsed.scheme == "https" or parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost", "::1"})):
            raise ValueError("MODEL_DIRECTORY_ENDPOINT_INVALID")
        request = urllib.request.Request(base + "/models", headers={"Authorization": "Bearer " + input["access"], "Accept": "application/json"})
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=12) as response:
            raw = response.read(131073)
            if len(raw) > 131072:
                raise ValueError("MODEL_AUTH_RESPONSE_LIMIT")
            return json.loads(raw)
    store = Credentials(input["directory"])
    if operation == "exchange":
        tokens = request_json(ISSUER + "/api/accounts/oauth/token", {"grant_type": "authorization_code", "client_id": input["client"],
            "redirect_uri": input["redirect"], "code": input["code"], "code_verifier": input["verifier"], "resource": RESOURCE})
        return store.save(validate(tokens, "CHATGPT_RESPONSES", input["client"], nonce=input["nonce"]))
    if operation == "import":
        source = input["source"]
        kind = "CODEX_APP_SERVER" if "tokens" in source else "CHATGPT_RESPONSES"
        client = CODEX_CLIENT if kind == "CODEX_APP_SERVER" else source["client_id"]
        if kind == "CHATGPT_RESPONSES" and source.get("issuer") != ISSUER:
            raise ValueError("MODEL_IMPORT_ISSUER_MISMATCH")
        value = source.get("tokens", source)
        discovery = request_json(ISSUER + "/.well-known/openid-configuration")
        if discovery.get("issuer") != ISSUER:
            raise ValueError("MODEL_TOKEN_ISSUER_INVALID")
        claims = verify(value.get("access_token"), RESOURCE, request_json(discovery.get("jwks_uri", "")).get("keys", []))
        tokens = {**value, "token_type": "Bearer", "scope": claims.get("scope", ""), "expires_in": max(1, int(claims["exp"] - time.time()))}
        return store.save(validate(tokens, kind, client))
    if operation == "access":
        return store.access(input["credentialFile"])
    if operation == "refresh":
        return store.refresh(input)
    if operation == "revoke":
        return {"confirmed": store.revoke(input["credentialFile"])}
    if operation == "discard":
        store.discard(input["credentialFile"])
        return {"discarded": True}
    raise ValueError("MODEL_AUTH_OPERATION_INVALID")


if __name__ == "__main__":
    try:
        raw = sys.stdin.buffer.read(262145)
        if len(raw) > 262144:
            raise ValueError("MODEL_AUTH_REQUEST_LIMIT")
        result = run(json.loads(raw))
        print(json.dumps({"result": result}, ensure_ascii=True, allow_nan=False))
    except Exception as error:
        # Only our fixed identifiers may cross the process boundary; never echo provider text or tokens.
        code = str(error)
        if not re.fullmatch(r"(?:MODEL|CODEX|ISSUED)_[A-Z0-9_]{1,100}", code):
            code = "MODEL_AUTH_PROTOCOL_FAILED"
        print(json.dumps({"error": code}))
        sys.exit(1)
