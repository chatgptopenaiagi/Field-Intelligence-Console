# Acceptance evidence

These are executed development gates, not a frozen release or GitHub CI claim.
Historical v1 schema remains reconstructed; the exported v2 schema is authentic
recovered-project output. No physical-device validation or real contact data used.

| Gate | Status |
|---|---|
| M0 safety regression | PASS: 48 JVM tests per variant at safety commit |
| Reachable history audit | Reviewed through 74c9837; final candidate audit pending |
| Shared contracts and Python core | PASS: 45 tests, including 35 shared invalid mutations |
| Android integrated JVM | PASS: 92 tests per variant, zero failures/skips |
| Android assembly/lint | PASS: both assemblies; debug 0 errors/4 warnings, release 0/3 |
| Android migration/emulator UI | PASS: 3 migrations + 2 real Compose tests on isolated API36 x86_64 |
| Wheel clean install/CLI | PASS: five checks outside checkout, packaged resources/CLI/workflows |
| Windows packaged GUI/restart | PASS: 5 focused, 28 packaged widget checks, 7 native interaction checks |
| Cross-platform actual exchange | PASS: APK SAF → fresh wheel → packaged EXE → fresh-wheel readback |
| Final exact-candidate local gates | NOT_EXECUTED |
| GitHub CI | NOT_EXECUTED |
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
