# ADR 002: Windows workstation and packaging

Status: accepted for implementation; runtime/package gates remain mandatory.

Use Python's Tk/ttk with the same installed FIC wheel used by the CLI. Keep a single
native desktop window with reports, Known Network, selected place and secondary
settings. Use SQLite in the user's local application-data directory, never the checkout.
Choose Tk over PySide6 here because Python3.14 x64 and Tk9.0 already work locally,
the required forms/tables/file pickers are supported, and Qt would add a separate
runtime and redistribution surface without a required feature benefit. No web server.

Package on Windows with pinned PyInstaller (exact tested revision recorded in the
build requirements). A one-file unsigned GUI EXE is the target; bundle Python/Tcl/Tk
and package schema resources. Verify with a clean profile and real window interactions,
in addition to diagnostics/self-test. Do not assume the source entry point proves EXE use.
Distribute Python/PSF, Tcl/Tk and PyInstaller bootloader exception notices.

Official references inspected 2026-10-03:
- https://docs.python.org/3/library/tkinter.html
- https://pyinstaller.org/en/stable/usage.html
- https://pyinstaller.org/en/stable/license.html

Core has no GUI dependency. Desktop packaging tools are development dependencies.
Keyboard access, readable scaling and persisted light/dark/contrast preferences are
part of the implementation, not a required onboarding sequence.
