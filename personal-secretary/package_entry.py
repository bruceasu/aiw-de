"""PyInstaller entry point for the one-folder Windows application."""

from pathlib import Path
import sys

from env_config import load_program_env

_program_file = Path(sys.executable) if getattr(sys, "frozen", False) else Path(__file__)
load_program_env(_program_file)

if len(sys.argv) > 1 and sys.argv[1] == "--service":
    del sys.argv[1]
    from main import main

    main()
else:
    from pet.desktop_webview import main

    main()
