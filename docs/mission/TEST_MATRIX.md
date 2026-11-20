# MISSION 001 beta3 — current verification

Preliminary changed-source checks on2026-11-20: Python47 tests PASS; tooling31
tests PASS (including10 Android runtime regression checks); actionlint PASS with
ShellCheck unavailable; PowerShell AST PASS. These are not frozen release gates.
New full Android, Windows packaged/native, clean-wheel, exact-APK runtime,
SAF/exchange, checksum/download and public-access checks remain NOT_EXECUTED.
Historical PASS counts below retain their original source/platform scope.

# Acceptance evidence

These are executed development gates, not a frozen release or GitHub CI claim.
Historical v1 schema remains reconstructed; the exported v2 schema is authentic
recovered-project output. No physical-device validation or real contact data used.

| Gate | Status |
|---|---|
| M0 safety regression | PASS: 48 JVM tests per variant at safety commit |
| Reachable history audit | Reviewed through 6f224c2: 20 commits/284 blobs; next candidate audit pending |
| Shared contracts and Python core | PASS: 45 tests, including 35 shared invalid mutations |
| Android integrated JVM | PASS: 97 tests per variant, zero failures/skips |
| Android assembly/lint | PASS: both assemblies; debug 0 errors/4 warnings, release 0/3 |
| Android migration/emulator UI | PASS: 3 migrations + 3 real Compose tests on isolated API36 x86_64 |
| Wheel clean install/CLI | PASS: five checks outside checkout, packaged resources/CLI/workflows |
| Windows packaged GUI/restart | PASS: 6 focused, 28 packaged widget checks, 7 native interaction checks |
| Cross-platform actual exchange | PASS: APK SAF → fresh wheel → packaged EXE → fresh-wheel readback |
| Final exact-candidate local gates | Candidate1 failed native second-editor opening after 3 checks; other gates passed |
| GitHub CI | PASS at 6f224c2: Android37109052933, Python37109054558, Windows37109056700 |
| Uploaded release asset verification | NOT_EXECUTED |

Scenario mapping:

| Scenario | Executed development evidence |
|---|---|
| A Unicode/multiple VCF values | Shared fixture parsed in Kotlin/Python, imported via APK picker and Windows GUI |
| B preview/cancel/repeat | Android Compose state/Room tests; packaged Tk callback tests; core atomic tests |
| C persistence/ancestry | Room reopen and emulator migration; SQLite reopen; native EXE second process |
| D search/geography | Android UI search, both core qualified-scope/radius suites; packaged Windows query |
| E provenance/unknown | Shared 35 invalid mutations and manual/saved/derived precision tests |
| F actual JSON round trip | `.local/evidence/android-ui-installed-wheel/windows-return-readback.json` |
| G unknown/missing policy | Both validators/parsers, JSON entity round trips; envelope losses disclosed |
| H selected CSV/VCF exports | Core format-loss/formula tests, Android preview, packaged Windows export UI |
| I report/AI privacy | Kotlin report/prompt tests, recursive shared rejection vectors, Python report tests |
| J unsafe/malformed/bounds | Kotlin/Python parser/provider tests, inert-reference and offline socket guards |

Local receipts: android-script-validation, android-ui, m3, python-provenance-development,
windows-gate-final-development and android-ui-installed-wheel under `.local/evidence/`.
Latest shared gates deliberately record dirty development state; repeat from the clean
candidate before publication. Release guard tests: six pass for dirty/mixed-SHA,
unexecuted runtime, skipped tests, missing GUI/wheel and stale receipt rejection.

Beta2 inventory tooling: nine unit tests PASS (six release guards, three inventory
privacy/package-graph tests), actionlint PASS; current AGP package graph102 equals
old local graph but generated paths differ. Hosted37117949965 diagnosed missing
old XML; next hosted run must validate current path in a fresh workspace. Beta2
preliminary clean c429abb Android97/variant and six API34 runtime PASS; lint0errors,
4debug/3release advisory warnings. Final exact-source candidate gates still pending.

Native driver source references: Windows GetGUIThreadInfo can report NULL focus;
pywinauto0.6.9 wraps it unsafely. Safe helper handles absent focus. Pillow12.3 HWND
capture avoids desktop-region fallback: https://pillow.readthedocs.io/en/stable/reference/ImageGrab.html.
ASCII native typing uses documented vk_packet=False fallback and separate Ctrl+A:
https://pywinauto.readthedocs.io/en/latest/code/pywinauto.keyboard.html. Seven pure
native-safety tests PASS, nine release guards/inventory tests PASS; Windows full
focused count increases7->14. One virtual-key7native PASS followed by explicit
foreground loss, then BLOCKED_DESKTOP_BUSY preflight. Do not describe this as local
repeatability PASS. Hosted isolated gates remain mandatory, with actual GUI checks.

Hosted exchange preparation: all21 tooling tests PASS (10exchange,3inventory,8release
guards); seven native-driver safety tests PASS without GUI/input. First native-test
invocation used wrong module search directory and failed import before execution;
correct windows working directory executed all7 successfully. actionlint PASS;
PowerShell AST checks PASS. Full14 Windows tests and hosted runtime remain pending.
