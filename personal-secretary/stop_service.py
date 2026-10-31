"""Compatibility launcher for desktop service-stop requests."""

from pathlib import Path
import runpy
import sys

_pet = Path(__file__).resolve().parent / "pet"
sys.path.insert(0, str(_pet))
runpy.run_path(str(_pet / "stop_service.py"), run_name="__main__")
