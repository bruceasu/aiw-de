"""Parse DeepSeek's public pricing page into a non-billable observation.

The fetch and parsing rules are independently replaceable as one collector.
Calendar coverage and a trusted effective interval are required before rates
can become accounting rules.
"""
from __future__ import annotations

from decimal import Decimal, InvalidOperation
from hashlib import sha256
from html.parser import HTMLParser
import re
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

SOURCE = "https://api-docs.deepseek.com/quick_start/pricing/"
VERSION = "deepseek-html-1"
MAX_BYTES = 2_000_000
_MODEL = re.compile(r"^(deepseek-[a-z0-9][a-z0-9.-]*)(?:\s*\(\d+\))?$", re.I)
_MONEY = re.compile(r"^\$(?:0|[1-9]\d*)(?:\.\d{1,6})?$")
_PEAK = re.compile(
    r"peak hours are 01:00\s*-\s*04:00 and 06:00\s*-\s*10:00 UTC,\s*"
    r"Monday through Friday,\s*excluding Chinese public holidays", re.I)
_OFF_PEAK = re.compile(
    r"all other hours are off-peak,\s*including weekends and Chinese public holidays in full", re.I)


class PriceStructureError(ValueError):
    """The source no longer matches the supported pricing contract."""


class SourceFetchError(RuntimeError):
    """A public-source request failed; never include its body or credentials."""


def _official(url):
    try:
        parts = urlsplit(url)
        return (parts.scheme == "https" and parts.hostname == "api-docs.deepseek.com"
                and parts.port in (None, 443) and not parts.username and not parts.password
                and not parts.fragment)
    except ValueError:
        return False


class _OfficialRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if not _official(newurl):
            raise SourceFetchError("SOURCE_REDIRECT_INVALID")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch(source_url: str = SOURCE):
    """Fetch the public source without credentials, bounded by the host process."""
    if not _official(source_url):
        raise SourceFetchError("SOURCE_URL_INVALID")
    request = Request(source_url, headers={"Accept": "text/html", "User-Agent": "AIW-price-observation/1"})
    opener = build_opener(ProxyHandler({}), _OfficialRedirect())
    try:
        with opener.open(request, timeout=10) as response:
            if response.status != 200 or not _official(response.geturl()):
                raise SourceFetchError("SOURCE_UNAVAILABLE")
            if response.headers.get_content_type().lower() != "text/html":
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


class _Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tables = []
        self.table = self.row = self.cell = None
        self.text = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style"}:
            self.hidden += 1
        if tag == "table":
            if self.table is not None:
                raise PriceStructureError("NESTED_TABLE")
            self.table = []
        elif tag == "tr" and self.table is not None:
            if self.row is not None:
                raise PriceStructureError("NESTED_ROW")
            self.row = []
        elif tag in {"td", "th"} and self.row is not None:
            if self.cell is not None:
                raise PriceStructureError("NESTED_CELL")
            attrs = dict(attrs)
            try:
                row = int(attrs.get("rowspan", "1"))
                col = int(attrs.get("colspan", "1"))
            except (TypeError, ValueError) as error:
                raise PriceStructureError("INVALID_SPAN") from error
            if not 1 <= row <= 64 or not 1 <= col <= 64:
                raise PriceStructureError("INVALID_SPAN")
            self.cell = [row, col, []]

    def handle_data(self, data):
        if not self.hidden:
            self.text.append(data)
            if self.cell is not None:
                self.cell[2].append(data)

    def handle_endtag(self, tag):
        if tag in {"script", "style"} and self.hidden:
            self.hidden -= 1
        if tag in {"td", "th"} and self.cell is not None:
            row, col, data = self.cell
            self.row.append((row, col, " ".join(" ".join(data).split())))
            self.cell = None
        elif tag == "tr" and self.row is not None:
            self.table.append(self.row)
            self.row = None
        elif tag == "table" and self.table is not None:
            self.tables.append(self.table)
            self.table = None


def _grid(rows):
    pending = {}
    result = []
    if len(rows) > 256:
        raise PriceStructureError("TABLE_TOO_LARGE")
    for cells in rows:
        row = {}
        next_pending = {}
        for column, (value, remaining) in pending.items():
            row[column] = value
            if remaining > 1:
                next_pending[column] = (value, remaining - 1)
        column = 0
        for rowspan, colspan, value in cells:
            while column in row:
                column += 1
            if column + colspan > 64 or any(i in row for i in range(column, column + colspan)):
                raise PriceStructureError("OVERLAPPING_SPAN")
            for i in range(column, column + colspan):
                row[i] = value
                if rowspan > 1:
                    next_pending[i] = (value, rowspan - 1)
            column += colspan
        result.append(row)
        pending = next_pending
    if pending:
        raise PriceStructureError("UNFINISHED_SPAN")
    return result


def _micros(value):
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
    if type(observed_ms) is not int or observed_ms <= 0:
        raise PriceStructureError("OBSERVATION_TIME_INVALID")
    if not _official(source_url):
        raise PriceStructureError("SOURCE_URL_INVALID")
    try:
        page = _Page()
        page.feed(content.decode("utf-8"))
        page.close()
    except UnicodeError as error:
        raise PriceStructureError("SOURCE_ENCODING_INVALID") from error
    text = " ".join(" ".join(page.text).split())
    if not _PEAK.search(text) or not _OFF_PEAK.search(text):
        raise PriceStructureError("PEAK_CONDITIONS_CHANGED")
    candidates = []
    for table in page.tables:
        grid = _grid(table)
        if grid and grid[0].get(0) == "MODEL" and any(r.get(0, "").startswith("PRICING") for r in grid):
            candidates.append(grid)
    if len(candidates) != 1:
        raise PriceStructureError("PRICE_TABLE_AMBIGUOUS")
    rows = candidates[0]
    header = rows[0]
    columns = sorted(i for i in header if i >= 3)
    if not columns or columns != list(range(3, 3 + len(columns))) or len(columns) > 32:
        raise PriceStructureError("MODEL_COLUMNS_INVALID")
    models = []
    for column in columns:
        match = _MODEL.fullmatch(header[column])
        if not match or match.group(1).lower() in models:
            raise PriceStructureError("MODEL_NAME_INVALID")
        models.append(match.group(1).lower())
    keys = {"1M INPUT TOKENS (CACHE HIT)": "cached", "1M INPUT TOKENS (CACHE MISS)": "input",
            "1M OUTPUT TOKENS": "output"}
    prices = {model: {"peak": {}, "off_peak": {}} for model in models}
    seen = set()
    for row in rows:
        if not row.get(0, "").startswith("PRICING"):
            continue
        key, band = keys.get(row.get(1)), {"PEAK": "peak", "OFF-PEAK": "off_peak"}.get(row.get(2))
        if key is None or band is None or (key, band) in seen:
            raise PriceStructureError("PRICE_ROWS_CHANGED")
        if set(row) != set(range(3 + len(models))):
            raise PriceStructureError("PRICE_COLUMNS_CHANGED")
        for column, model in zip(columns, models):
            prices[model][band][key] = _micros(row[column])
        seen.add((key, band))
    if len(seen) != 6:
        raise PriceStructureError("PRICE_ROWS_INCOMPLETE")
    return {
        "protocol": "aiw.price-observation.v1", "provider": "deepseek", "sourceUrl": source_url,
        "contentSha256": sha256(content).hexdigest(), "collectorVersion": VERSION,
        "observedMs": observed_ms, "currency": "USD", "unit": "micro-currency-per-million-tokens",
        "validity": "OBSERVED_ONLY", "accountingReady": False,
        "missing": ["TRUSTED_EFFECTIVE_INTERVAL", "CHINESE_PUBLIC_HOLIDAY_CALENDAR"],
        "conditions": {"zone": "UTC", "peakDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
                       "peakWindows": [["01:00", "04:00"], ["06:00", "10:00"]],
                       "excluded": "CHINESE_PUBLIC_HOLIDAYS"},
        "models": [{"model": model, **prices[model]} for model in models],
    }
