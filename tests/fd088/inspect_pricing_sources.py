"""Read-only official-page inspection. Requires explicit network authorization."""
from html.parser import HTMLParser
import argparse
import json
import urllib.request


URLS = (
    "https://developers.openai.com/api/docs/pricing.md",
    "https://api-docs.deepseek.com/quick_start/pricing/",
)


class SameHostRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        from urllib.parse import urlsplit
        before, after = urlsplit(req.full_url), urlsplit(newurl)
        if after.scheme != "https" or after.hostname != before.hostname:
            raise ValueError("Cross-host redirect refused")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class Tables(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tables = self.rows = self.cells = 0
        self.rowspan = self.colspan = False
        self._cell = None
        self._row = None
        self.sample = []

    def handle_starttag(self, tag, attrs):
        if tag == "table": self.tables += 1
        elif tag == "tr":
            self.rows += 1
            self._row = []
        elif tag in {"td", "th"}:
            self.cells += 1
            attrs = dict(attrs)
            self.rowspan |= "rowspan" in attrs
            self.colspan |= "colspan" in attrs
            self._cell = [tag, attrs.get("rowspan"), attrs.get("colspan"), []]

    def handle_data(self, data):
        if self._cell is not None:
            self._cell[3].append(data)

    def handle_endtag(self, tag):
        if tag in {"td", "th"} and self._cell is not None:
            kind, rowspan, colspan, data = self._cell
            if self._row is not None and len(self._row) < 20:
                self._row.append((kind, rowspan, colspan, " ".join(" ".join(data).split())[:160]))
            self._cell = None
        elif tag == "tr" and self._row is not None:
            if self._row and len(self.sample) < 35:
                self.sample.append(self._row)
            self._row = None


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("--source", choices=("all", "deepseek"), default="all")
    selection = args.parse_args().source
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), SameHostRedirect())
    for url in (URLS if selection == "all" else URLS[1:]):
        request = urllib.request.Request(url, headers={"Accept": "text/markdown,text/html", "User-Agent": "AIW-pricing-inspection/1"})
        try:
            with opener.open(request, timeout=10) as response:
                data = response.read(2_000_001)
                if len(data) > 2_000_000: raise ValueError("Response exceeds limit")
                content = data.decode("utf-8")
                print("source:", url, "status:", response.status, "content-type:", response.headers.get("Content-Type"), "bytes:", len(data))
                if "markdown" in (response.headers.get("Content-Type") or ""):
                    lines = [line.strip()[:300] for line in content.splitlines()
                             if "|" in line or line.lstrip().startswith("#")]
                    print("section-and-table-samples:", json.dumps(lines[:100], ensure_ascii=True))
                else:
                    parser = Tables(); parser.feed(content)
                    print("html-tables:", parser.tables, "rows:", parser.rows, "cells:", parser.cells,
                          "rowspan:", parser.rowspan, "colspan:", parser.colspan)
                    print("table-row-samples:", json.dumps(parser.sample, ensure_ascii=True))
        except (OSError, ValueError, UnicodeError) as error:
            print("source:", url, "inspection-failed:", type(error).__name__)


if __name__ == "__main__":
    main()
