# FIC Windows workstation

The experimental portable x64 EXE includes Python, Tcl/Tk and the FIC core.
Users do not need Python, Java, Android Studio or a server. It is **unsigned**;
verify its release checksum and publisher context. Do not disable Windows security.

Open the EXE to use one native window:

- **Reports / History:** import a FIC FieldReport, inspect its observation,
  location provenance and provider/freshness/error states, search, reopen, export
  one selected report or create a local observation. Use a report's coordinates
  as the selected place. Desktop GPS is never assumed.
- **KNOWN NETWORK:** select VCF 3.0/4.0, CSV or FIC JSON; review valid/invalid/
  duplicate counts, select valid records, then confirm an atomic local import.
  Cancel is read-only. Original files are not retained. CSV mappings use
  canonical-column=input-column pairs; available names are shown in preview.
- Add/edit/delete local records. Open details intentionally to see phone/email
  values and notes. The primary form edits the first address/phone/email;
  the secondary canonical JSON editor preserves and edits multiple typed values,
  source ancestry, unknown fields and association provenance. Stable IDs cannot
  change during an edit. Address corrections invalidate old derived coordinates.
- Choose a manual or report-derived place and a qualified geographic scope.
  Unknown precision/fields remain unavailable. Counts mean saved associations,
  never people physically present. Coarse points are excluded from precise radius
  matching. A manually saved point requires a deliberate precision choice.
- Select specific records before exporting. FIC JSON preserves canonical fields;
  VCF/CSV preview explains their loss. Spreadsheet-safe CSV escapes formula-like
  values; original values remain intact in FIC JSON. Exports are unencrypted.
- Optional OpenCage geocoding is off by default. Select one saved address, review
  the exact text/provider, enter an ephemeral masked key, and explicitly send.
  Choose a returned candidate before saving derived coordinates. No import,
  startup, query or map opening geocodes contacts. No paid key is required offline.
- Phone, email and map actions require explicit user clicks and confirmation.
  FIC opens controlled system handlers; it does not send messages or auto-call.
- Settings offer light/dark/high-contrast themes and text sizes. Ctrl+F searches,
  Ctrl+I imports, Ctrl+N creates a contact, Enter opens details, Escape cancels
  dialogs. Large-text contact forms scroll.

## Storage, removal and limitations

Normal data is in the current user's local application-data
FieldIntelligenceConsole directory. It is local SQLite, **not encrypted**.
Protect the Windows account and backups. --profile selects a separate
profile; development always uses synthetic isolated profiles outside the checkout.

To uninstall the portable application, close it and remove its EXE. This does not
remove the local library. Delete chosen records inside the app or deliberately
remove the profile after backing up anything wanted. Existing exports, backups,
filesystem recovery and original imported files are outside in-app deletion.
There is no automatic sync, telemetry, contact-book access or AI sharing.
Imported provider results are displayed; desktop AI/weather live providers are
not claimed as implemented. OpenCage runtime uses a configured user's account;
development tests use synthetic mocked responses.

## Development and verification

Use the installed wheel, not an accidental source-tree import. Create a local
.venv-windows with Python 3.14, install windows/requirements-build.txt, then run
windows/build.ps1 -Wheel followed by the verified wheel path.

build.ps1 installs that wheel and creates a one-file GUI EXE with bundled
schemas/fixtures/notices and numeric Windows version 4.0.0.8, product version
4.0.0-beta.8.dev1. Build from the exact frozen release commit for delivery.

Focused tests: .venv-windows/Scripts/python.exe -m unittest discover -s windows
-p test_workstation.py -v

The EXE supports --self-test and --ui-test, each requiring an explicit isolated
--profile and optionally --evidence. The UI test operates real Tk widgets/callbacks
with synthetic package fixtures and intercepted file dialogs; this alone is not
native mouse/keyboard evidence. Optional paired --interop-input / --interop-output
checks exact canonical preservation through preview/confirm/export widgets.

windows/native_ui_check.py takes the EXE and --evidence directory. It copies the
EXE outside the checkout, verifies window/process ownership, interacts via Windows
mouse/keyboard, validates a saved synthetic contact, captures local screenshots,
closes it and restarts it. Its evidence is local-only. Automation dependencies
are development-only and are not imported by the GUI.

Third-party notices are bundled and accessible in Settings. See ADR 002 for the
Tk decision. Packaging warnings for unused platform/optional modules are reviewed;
the runtime gate establishes operation.
