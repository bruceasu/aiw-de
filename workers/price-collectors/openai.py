"""Collect OpenAI's public Markdown price tables as non-billable observations."""
from __future__ import annotations

from decimal import Decimal, InvalidOperation
from hashlib import sha256
import re
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

SOURCE = "https://developers.openai.com/api/docs/pricing.md"
VERSION = "openai-markdown-1"
MAX_BYTES = 2_000_000
_MONEY = re.compile(r"^\$(?:0|[1-9]\d*)(?:\.\d{1,6})?$")
_SEPARATOR = re.compile(r"^:?-{3,}:?$")
_SHORT = ["Model", "Input", "Output", "Cached input"]
_LONG = ["Model", "Short context input", "Short context output", "Short context cached input",
         "Short context 30m write", "Short context 12h write", "Long context input", "Long context output",
         "Long context cached input", "Long context 30m write", "Long context 12h write"]


class PriceStructureError(ValueError):
    pass


class SourceFetchError(RuntimeError):
    pass


def _official(url):
    try:
        parts = urlsplit(url)
        return (parts.scheme == "https" and parts.hostname == "developers.openai.com"
                and parts.port in (None, 443) and not parts.username and not parts.password and not parts.fragment)
    except ValueError:
        return False


class _OfficialRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not _official(newurl):
            raise SourceFetchError("SOURCE_REDIRECT_INVALID")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch(source_url: str = SOURCE):
    if not _official(source_url):
        raise SourceFetchError("SOURCE_URL_INVALID")
    request = Request(source_url, headers={"Accept": "text/markdown", "User-Agent": "AIW-price-observation/1"})
    opener = build_opener(ProxyHandler({}), _OfficialRedirect())
    try:
        with opener.open(request, timeout=10) as response:
            if response.status != 200 or not _official(response.geturl()):
                raise SourceFetchError("SOURCE_UNAVAILABLE")
            if response.headers.get_content_type().lower() not in {"text/markdown", "text/plain"}:
                raise SourceFetchError("SOURCE_FORMAT_INVALID")
            declared = response.headers.get("Content-Length")
            if declared is not None and (not declared.isdecimal() or int(declared) > MAX_BYTES):
                raise SourceFetchError("SOURCE_SIZE_INVALID")
            content = response.read(MAX_BYTES + 1)
            if not content or len(content) > MAX_BYTES:
                raise SourceFetchError("SOURCE_SIZE_INVALID")
            return content, time.time_ns() // 1_000_000, response.geturl()
    except (HTTPError, URLError, OSError) as error:
        raise SourceFetchError("SOURCE_UNAVAILABLE") from error


def _cells(line):
    line = line.strip()
    if not (line.startswith("|") and line.endswith("|")) or "\\|" in line:
        return None
    return [cell.strip() for cell in line[1:-1].split("|")]


def _price(value):
    if value == "-":
        return None
    if not _MONEY.fullmatch(value):
        raise PriceStructureError("UNSUPPORTED_PRICE")
    try:
        result = Decimal(value[1:]) * 1_000_000
    except InvalidOperation as error:
        raise PriceStructureError("UNSUPPORTED_PRICE") from error
    if result != result.to_integral_value() or result > 9_000_000_000_000_000:
        raise PriceStructureError("UNSUPPORTED_PRICE")
    return int(result)


def parse(content: bytes, observed_ms: int, source_url: str = SOURCE):
    if not isinstance(content, bytes) or not content or len(content) > MAX_BYTES:
        raise PriceStructureError("SOURCE_SIZE_INVALID")
    if type(observed_ms) is not int or observed_ms <= 0 or not _official(source_url):
        raise PriceStructureError("SOURCE_IDENTITY_INVALID")
    try:
        text = content.decode("utf-8")
    except UnicodeError as error:
        raise PriceStructureError("SOURCE_ENCODING_INVALID") from error
    if not re.search(r"(?:prices?|pricing)\s+per\s+1m\s+tokens", text, re.I):
        raise PriceStructureError("PRICE_UNIT_CHANGED")
    lines = text.splitlines()
    tables, headings = [], []
    index = 0
    while index < len(lines):
        line = lines[index].strip()
        if re.match(r"^#{1,6}\s+", line):
            headings.append(line[:300])
            headings = headings[-4:]
        header = _cells(line)
        if header not in (_SHORT, _LONG):
            index += 1
            continue
        if index + 1 >= len(lines):
            raise PriceStructureError("PRICE_TABLE_INCOMPLETE")
        separator = _cells(lines[index + 1])
        if separator is None or len(separator) != len(header) or not all(_SEPARATOR.fullmatch(cell) for cell in separator):
            raise PriceStructureError("PRICE_TABLE_INCOMPLETE")
        rows = []
        index += 2
        while index < len(lines):
            cells = _cells(lines[index])
            if cells is None:
                break
            if len(cells) != len(header) or not cells[0] or len(cells[0]) > 200:
                raise PriceStructureError("PRICE_ROWS_CHANGED")
            rows.append({"modelLabel": cells[0], "rates": [_price(value) for value in cells[1:]]})
            if len(rows) > 1000:
                raise PriceStructureError("PRICE_TABLE_TOO_LARGE")
            index += 1
        if not rows:
            raise PriceStructureError("PRICE_TABLE_EMPTY")
        tables.append({"sectionHeadings": list(headings), "columns": header, "rows": rows})
        if len(tables) > 100:
            raise PriceStructureError("PRICE_TABLE_TOO_LARGE")
    if not tables:
        raise PriceStructureError("PRICE_TABLE_NOT_FOUND")
    return {
        "protocol": "aiw.price-observation.v1", "provider": "openai", "sourceUrl": source_url,
        "contentSha256": sha256(content).hexdigest(), "collectorVersion": VERSION, "observedMs": observed_ms,
        "currency": "USD", "unit": "micro-currency-per-million-tokens", "validity": "OBSERVED_ONLY",
        "accountingReady": False,
        "missing": ["TRUSTED_EFFECTIVE_INTERVAL", "SERVICE_TIER_AND_PRICING_CONDITIONS"],
        "conditions": {"pricingTables": "source-order; tier and context thresholds not inferred"}, "models": tables,
    }
