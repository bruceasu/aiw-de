"""Bounded, local parser protocol for the Plane price coordinator.

Provider modules are loaded from the configured collector directory. A repair
candidate is supplied through a temporary directory only after a Plane owner
approves its exact source and page snapshot. Collection may fetch public prices
but never publishes accounting prices. Run with a minimal environment, no
secrets, and close stdin after writing the single request.
"""
from __future__ import annotations

import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys

MAX_REQUEST = 2_800_000
MAX_RESPONSE = 3_800_000
MODULES = {"deepseek": "deepseek.py", "openai": "openai.py"}


class FetchFailed(Exception):
    pass


class StructureChanged(Exception):
    def __init__(self, content, observed, source, version, collector_sha, reason):
        self.content, self.observed, self.source = content, observed, source
        self.version, self.collector_sha, self.reason = version, collector_sha, reason


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_FIELD")
        result[key] = value
    return result


def _load(provider):
    if not isinstance(provider, str) or provider not in MODULES:
        raise ValueError("UNSUPPORTED_PROVIDER")
    module_path = Path(__file__).resolve().parent / MODULES[provider]
    if module_path.is_symlink() or not module_path.is_file() or module_path.stat().st_size > 1_000_000:
        raise ValueError("ADAPTER_UNAVAILABLE")
    source = module_path.read_bytes()
    if not source or len(source) > 1_000_000:
        raise ValueError("ADAPTER_UNAVAILABLE")
    spec = importlib.util.spec_from_file_location("aiw_price_adapter", module_path)
    if spec is None or spec.loader is None:
        raise ValueError("ADAPTER_UNAVAILABLE")
    module = importlib.util.module_from_spec(spec)
    # Execute the exact source bytes being identified; do not use a stale .pyc.
    exec(compile(source, str(module_path), "exec"), module.__dict__)
    collector_sha256 = hashlib.sha256(source).hexdigest()
    module._aiw_collector_sha256 = collector_sha256
    return module, collector_sha256


def execute(request):
    if not isinstance(request, dict) or not {"protocol", "provider", "observedMs", "contentBase64"}.issubset(request) \
            or set(request) - {"protocol", "provider", "observedMs", "contentBase64", "sourceUrl"}:
        raise ValueError("INVALID_REQUEST")
    if request["protocol"] != "aiw.price-parser-request.v1":
        raise ValueError("INVALID_REQUEST")
    encoded = request["contentBase64"]
    if not isinstance(encoded, str):
        raise ValueError("INVALID_CONTENT")
    try:
        content = base64.b64decode(encoded, validate=True)
    except (ValueError, TypeError) as error:
        raise ValueError("INVALID_CONTENT") from error
    module, collector_sha256 = _load(request["provider"])
    if "sourceUrl" in request:
        result = module.parse(content, request["observedMs"], request["sourceUrl"])
    else:
        result = module.parse(content, request["observedMs"])
    result["collectorSha256"] = collector_sha256
    return result


def collect(request):
    if not isinstance(request, dict) or set(request) != {"protocol", "provider"}:
        raise ValueError("INVALID_REQUEST")
    if request["protocol"] != "aiw.price-collector-request.v1":
        raise ValueError("INVALID_REQUEST")
    module, collector_sha256 = _load(request["provider"])
    try:
        content, observed, source = module.fetch()
    except module.SourceFetchError as error:
        raise FetchFailed("PUBLIC_SOURCE_UNAVAILABLE") from error
    try:
        result = module.parse(content, observed, source)
    except module.PriceStructureError as error:
        reason = str(error)
        if not re.fullmatch(r"[A-Z0-9_]{1,80}", reason):
            reason = "UNCLASSIFIED_STRUCTURE"
        raise StructureChanged(content, observed, source, module.VERSION, collector_sha256, reason) from error
    result["collectorSha256"] = collector_sha256
    return result, base64.b64encode(content).decode("ascii")


def fetch_page(request):
    if not isinstance(request, dict) or set(request) != {"protocol", "provider", "sourceUrl"}:
        raise ValueError("INVALID_REQUEST")
    if request["protocol"] != "aiw.price-page-fetch-request.v1":
        raise ValueError("INVALID_REQUEST")
    module, _ = _load(request["provider"])
    try:
        content, observed, _final_url = module.fetch(request["sourceUrl"])
    except module.SourceFetchError as error:
        raise FetchFailed("PUBLIC_SOURCE_UNAVAILABLE") from error
    return content, observed


def main():
    try:
        raw = sys.stdin.buffer.read(MAX_REQUEST + 1)
        if not raw or len(raw) > MAX_REQUEST:
            raise ValueError("INVALID_REQUEST_SIZE")
        request = json.loads(raw, object_pairs_hook=_unique)
        if isinstance(request, dict) and request.get("protocol") == "aiw.price-collector-request.v1":
            result, content = collect(request)
        elif isinstance(request, dict) and request.get("protocol") == "aiw.price-page-fetch-request.v1":
            content, observed = fetch_page(request)
            result = {"sourceUrl": request["sourceUrl"], "observedMs": observed}
            content = base64.b64encode(content).decode("ascii")
        else:
            result, content = execute(request), None
        page_fetch = isinstance(request, dict) and request.get("protocol") == "aiw.price-page-fetch-request.v1"
        response = json.dumps({"protocol": "aiw.price-parser-response.v1", "state": "PAGE" if page_fetch else "OBSERVED",
                               "observation": None if page_fetch else result,
                               "sourceUrl": result.get("sourceUrl") if page_fetch else None,
                               "observedMs": result.get("observedMs") if page_fetch else None,
                               "contentBase64": content},
                              ensure_ascii=True, separators=(",", ":"), allow_nan=False).encode("ascii")
        if len(response) > MAX_RESPONSE:
            raise ValueError("INVALID_RESPONSE_SIZE")
        sys.stdout.buffer.write(response + b"\n")
        return 0
    except FetchFailed:
        sys.stdout.buffer.write(b'{"protocol":"aiw.price-parser-response.v1","state":"FETCH_FAILED"}\n')
        return 1
    except StructureChanged as error:
        response = json.dumps({"protocol": "aiw.price-parser-response.v1", "state": "STRUCTURE_CHANGED",
                               "sourceUrl": error.source, "observedMs": error.observed,
                               "collectorVersion": error.version, "collectorSha256": error.collector_sha, "reason": error.reason,
                               "contentBase64": base64.b64encode(error.content).decode("ascii")},
                              ensure_ascii=True, separators=(",", ":"), allow_nan=False).encode("ascii")
        if len(response) > MAX_RESPONSE:
            sys.stdout.buffer.write(b'{"protocol":"aiw.price-parser-response.v1","state":"REJECTED"}\n')
        else:
            sys.stdout.buffer.write(response + b"\n")
        return 1
    except Exception:
        # The caller receives a classification only, never source text or input.
        sys.stdout.buffer.write(b'{"protocol":"aiw.price-parser-response.v1","state":"REJECTED"}\n')
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
