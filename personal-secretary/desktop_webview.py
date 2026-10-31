"""Compatibility launcher for the Qt desktop pet UI."""

from pathlib import Path
import runpy
import sys

_pet = Path(__file__).resolve().parent / "pet"
sys.path.insert(0, str(_pet))
runpy.run_path(str(_pet / "desktop_webview.py"), run_name="__main__")
