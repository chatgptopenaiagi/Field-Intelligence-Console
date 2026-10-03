# M4 Windows workstation implementation

Implemented under windows/, sharing the installed Python core API and shared
fixtures. No second database model, server or Android replacement was introduced.

## Functional evidence

- Native Tk/ttk single-window reports/history, field-report import/display/search/
  reopen/export and local observations.
- VCF/CSV/FIC JSON preview, selectable valid rows, explicit confirmation, atomic
  import, cancellation without mutation and idempotent repeated source import.
- Local contact add/edit/delete/search/detail, multiple-value canonical editor,
  original-address retention, source ancestry and derived-point invalidation on
  address correction. Sensitive detail is absent from overview rows.
- Deliberate selected-record exports with loss warnings. CSV mapping preview and
  spreadsheet-safe core exports; original values remain in canonical JSON.
- Manual/report place selection and all supported scopes through the real core.
  Missing scope evidence displays unavailable count, not an invented zero.
- Optional OpenCage dialog with exact-address/provider preview, explicit disclosure
  consent, ephemeral key, cancellable worker and explicit candidate selection.
  No live provider/key test was performed; mocked UI boundaries were tested.
- Light/dark/high contrast, remembered preferences, text scaling, scrolling contact
  form, keyboard shortcuts and controlled user-initiated system handlers.

## Preliminary validation (not the final release candidate)

Environment: Windows x64, CPython **3.14.7**, Tk **9.0**, PyInstaller **6.22.3**,
pywinauto **0.6.9** and development-only Pillow **12.3.0**.

| Gate | Actual result |
|---|---|
| Focused workstation tests | 5 passed, 0 failures: bounds/unknown location, external URI allowlist, address-edit invalidation, geocoder consent/selection and late-result cancellation |
| Packaged EXE widget gate | 28 checks passed, including external canonical network and report round trips |
| Native mouse/keyboard gate | 7 checks passed: actual window, Ctrl+N, typed contact saved and independently read from isolated SQLite, Escape cancel, search, close, second-process restart |
| PE metadata | AMD64 0x8664, GUI subsystem 2; product 4.0.0-beta.1, file 4.0.0.1 |
| Signing | Authenticode NotSigned; explicitly experimental |
| Offline guard | No socket connection attempted during fixture import/browse/query/export tests |
| Portable runtime | Copied EXE ran outside checkout with Python environment overrides removed; no separate Python installation step for the app |

Native screenshots were visually inspected. This caught and led to repairs for
high-contrast combobox/tab colors and a clipped export bar. Native input/save was
then verified against isolated persisted state, not inferred from a screenshot alone.
No personal contacts, real database or external contact actions were used.

Local evidence: .local/evidence/windows/ (focused-tests.log, package.log,
jvm-wheel-exe-interop.json, windows-vcf-network-return.json,
windows-report-return.json, native-current/). Preliminary EXE:
.local/artifacts/windows/FieldIntelligenceConsole-4.0.0-beta.1-windows-x64.exe.
These artifacts are ignored and must be rebuilt from the coordinator's exact final
candidate. No Git integration or remote changes were performed by the Windows worker.

The Android JVM codec -> separately installed wheel -> packaged Windows
preview/confirm/export chain passed for the two-contact VCF conversion and the
FieldReport. Subsequently the actual Android APK emulator UI/SAF exports passed
through a fresh installed wheel and the packaged Windows preview/confirm/export
workflow, preserving every canonical entity/report field. Receipt:
.local/evidence/windows/android-ui-wheel-exe-interop.json; return files:
windows-android-ui-network-return.json and windows-android-ui-report-return.json
in that same local evidence directory. The Python worker performs final independent
wheel readback. This remains synthetic emulator evidence, not physical GPS proof.

tooling/windows_gate.ps1 ran successfully locally: 5 focused tests, 28 packaged
widget checks, 7 native checks and PE validation. It requires a clean candidate by
default; -AllowDirtyDevelopment explicitly labels preliminary dirty-tree builds.
windows-ci.yml uses that same gate with reviewed immutable Action pins, read-only
token permissions, explicit EXE/receipt uploads and seven-day retention. CI execution
is not claimed merely because the workflow exists.

Latest development gate: .local/evidence/windows-gate-final-development/. Its
receipt explicitly marks a dirty development worktree and records base HEAD;
it does not claim those preliminary artifacts correspond to a frozen release SHA.
The packaged runtime itself reported Python 3.14.7 / AMD64, Tcl 9.0.4, Tk 9.0.4,
OpenSSL 3.5.7 and SQLite 3.50.4. Runtime dependency constraints are pinned to the
versions whose notices were bundled.

## Remaining release gates and honest limits

Final source freeze/rebuild, whole-product cross-platform Android-generated exchange,
full CI, manifest/SBOM/hash verification and private release delivery belong to the
coordinator. This preliminary package is not itself a published release.

The current UI test supplies deterministic file-dialog selections; the separate
native gate verifies mouse/keyboard and persistence but does not exhaust all
Windows accessibility tools, display scales or locales. Actual geocoder account
behavior is unexecuted without a key. Local SQLite is not encrypted. CSV/VCF are
deliberately less expressive than canonical JSON. Enterprise connectors and live
presence are not implemented or implied.

Notices are bundled for FIC, CPython, Tcl/Tk, core dependencies and the PyInstaller
exception. Software license is separate from imported-data ownership. Packaging
warnings include platform-conditional/unused optional modules; core date-time
validation is explicit and does not depend on optional jsonschema format extras.
Runtime workflows, including package resource access, actually passed.
