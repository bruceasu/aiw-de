"""Read and parse public pricing pages; requires explicit network authorization."""
import importlib.util
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[2] / "workers" / "price-collectors"
    failed = False
    for provider in ("openai", "deepseek"):
        try:
            spec = importlib.util.spec_from_file_location("price_inspection_" + provider, root / (provider + ".py"))
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            content, observed, source = module.fetch()
            result = module.parse(content, observed, source)
            print("provider:", provider, "source:", source, "bytes:", len(content),
                  "protocol:", result.get("protocol"), "groups:", len(result.get("models", [])),
                  "accounting-ready:", result.get("accountingReady"), "missing:", result.get("missing"))
        except Exception as error:
            failed = True
            code = str(error)
            if not code.replace("_", "").isalnum() or len(code) > 80:
                code = "UNCLASSIFIED"
            print("provider:", provider, "failed:", type(error).__name__, "code:", code)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
