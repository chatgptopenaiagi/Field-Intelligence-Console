# MISSION 001 — FINAL ENGINEERING HANDOFF

**ENGINEERING SUSPENDED — RECOVERABLE HANDOFF**

Owner stop instruction received on 2026-11-20. This document supersedes all earlier
autonomous continuation/publication instructions. No new development, tests,
dependency installation, build, candidate, visibility change or release publication
is authorized by this handoff. Existing hosted workflows may finish independently.
The handoff records their last observed states, without waiting for completion.

## Frozen identity and the beta5/beta6 distinction

- Project: `C:\xampp\FieldIntelligenceConsole`.
- Original repository: https://github.com/chatgptopenaiagi/Field-Intelligence-Console
- Development/default branch: `v4-development`; repository **PRIVATE** at shutdown.
- Owner-named beta5: `v4.0.0-beta.5`, source
  `07f2f9f25a47f6771e149b38970914911471d4fa`.
- Actual branch HEAD when the stop arrived:
  `6335a0e4c64a1d50af2436454a579818b0ea724b`, already tagged and pushed as
  `v4.0.0-beta.6` **before** the stop instruction. This corrects the owner's stale
  beta5 checkpoint without discarding either candidate. No candidate was created
  during shutdown. Beta6 contains the emulator compatibility repair described below.
- Working tree was clean, with no visible untracked files or uncommitted source
  changes at freeze. `1 PROMT.txt` is preserved locally, excluded through
  `.git/info/exclude`; ignored tools, binaries and evidence remain local.
- Final handoff is a documentation-only descendant of `6335a0e`; locate it with
  `git log -1 --format=%H --grep='docs: preserve Mission 001 beta5 engineering handoff'`.
  Its SHA is also reported to the owner. No source or tag rewriting is required.

| Preserved tag | Commit (peeled) |
| --- | --- |
| `v3-recovered-baseline` | `e45f9b863892b1796774a8132a213ea99c4518d2` |
| `v4.0.0-beta.1` | `e6ad92573e832c75684d2464fcc4b81042e42788` |
| `v4.0.0-beta.2` | `296730438928717404c99bab8d0ba080659abd49` |
| `v4.0.0-beta.3` | `8c6e7e53e82991a9419d0861e6c91b7dd7f8d95e` |
| `v4.0.0-beta.4` | `0f2451828a8d03d5a2695fe87d0daa76cfa75c03` |
| `v4.0.0-beta.5` | `07f2f9f25a47f6771e149b38970914911471d4fa` |
| `v4.0.0-beta.6` | `6335a0e4c64a1d50af2436454a579818b0ea724b` |

## 1. PROJECT OVERVIEW

FIC turns a place and moment into awareness, intelligence and persistent field
memory. The original Android Action Matrix retains INFO SCAN, HISTORY, NAVIGATION,
AI ANALYSIS, RISK / SAFETY, LOGISTICS, FINANCIAL and EMERGENCY EXIT. Local Known
Network records people/organizations associated with saved places; an association
does not establish anyone's current physical presence. The Windows workstation and
Python core share local report/contact workflows. This is the recovered original
project, not a replacement repository or framework rewrite.

The owner reports initial development with Manus AI approximately a year earlier.
That provenance could not be independently verified: no distinct Manus artifact
was identified. October recovery records describe an unborn Git repository and 74
staged recovered files, committed as the protected baseline. There is no recovered
pre-baseline Git history proving earlier authorship or dates. Later recovery and
productization are present in the preserved commits. See
[discovery](DISCOVERY_2026-11-20.md) for the initial inventory and environment.

## 2. CURRENT ARCHITECTURE

| Area | Important source paths and responsibility |
| --- | --- |
| Android entry/UI | `app/src/main/java/com/fieldconsole/MainActivity.kt`, `ui/main/MainScreen.kt`, `viewmodel/MainViewModel.kt`; Kotlin, Compose, Hilt and coroutine/Flow state |
| Android location/actions | `location/LocationManager.kt`, `ReverseGeocoder.kt`, `GeoContext.kt`, `intent/IntentDispatcher.kt`, `EmergencyDialer.kt`, `prompt/FieldPromptGenerator.kt` under the same package root |
| Android storage/providers | `data/local/database/AppDatabase.kt`, `data/local/dao/`, `data/local/entity/`, `data/repository/`, `data/remote/emergency/OverpassHttpClient.kt`; Room v3, explicit migrations, local history/cache |
| Android Known Network | `network/NetworkCodec.kt`, `NetworkRepository.kt`, `NetworkGeography.kt`, `SharedSchemaValidator.kt`, `viewmodel/NetworkViewModel.kt`, `ui/network/NetworkScreen.kt`, `ContactEditor.kt`, `PhoneContactsImport.kt` |
| Android report exchange | `export/ReportExchange.kt`, `FieldReportExporter.kt`, `ui/network/ReportScreen.kt`; real Storage Access Framework OpenDocument/CreateDocument |
| Windows | `windows/fic_windows.py`, `geocoding_dialog.py`; Tk/ttk native desktop, shared Python core, PyInstaller portable x64 EXE; profile data persists in user-local storage |
| Python | `python/src/field_intelligence_console/{cli,models,store,exchange,geography,providers}.py`; schema validation, SQLite, bounded import/export, geography, CLI and offline diagnostics |
| Contracts | `shared/CONTRACT.md`, `shared/schemas/`, `shared/fixtures/`; NetworkDataset and FieldReport schema v1. Packaged copies under Python `schemas/` and `fixtures/` |
| Tests | `app/src/test/`, `app/src/androidTest/`, `python/tests/`, `python/verify_wheel.py`, `windows/test_workstation.py`, `workstation_checks.py`, `native_ui_check.py`, `tooling/tests/` |
| Gates | `tooling/{android_gate,windows_gate,python_gate,release_candidate}.ps1`, `android_runtime.py`, `check_versions.py`, `release_inventory.py`, `finalize_release.py`, `hosted_exchange.py`, `audit_upload.py`, `audit_artifacts.py` |
| Hosted automation | `.github/workflows/{android-ci,windows-ci,python-ci,release,exchange-ci,inventory-ci}.yml` |

Android application identities remain `com.fieldconsole` and testing
`com.fieldconsole.debug`. The distributed candidate is the debug/testing APK with
the retained certificate pinned in `shared/release-signing.json`. Its private key
is an existing Actions secret, never a committed file. Windows is unsigned.
Beta5 maps to Python `4.0.0b5`, Android code8 and Windows `4.0.0.5`; beta6 maps to
`4.0.0b6`, code9 and `4.0.0.6`. Data schema versions are independent of app versions.

Imports support bounded UTF-8 VCF3/4, mapped CSV and canonical FIC JSON. Preview,
selection and explicit confirmation precede writes; repeat imports detect source
duplicates. JSON preserves ancestry/provenance; VCF/CSV have documented loss limits.
Exports require deliberate selection. Contacts/counts are excluded from ordinary
field reports and AI packets. No real personal contacts or databases were used.

## 3. COMPLETED ENGINEERING

Earlier recovery, retained in this mission, added bounded Overpass calls, distinct
empty/failure and stale-cache states, geographic filters, completed geocoding,
non-destructive Room migrations and human-confirmed emergency dialer actions. It
implemented Known Network management and shared report exchange, the Python core
and Windows workstation. These were cumulative existing work, not all new today.

This 2026-11-20 continuation:

1. Verified the original repository/account, preserved history and protected tags,
   documented architecture/environment and repaired stale project-local tool paths.
2. Beta3: waited for Android boot **and package service**, used non-streaming
   `adb install -r`, bounded transient retries, rejected deterministic install errors,
   checked exact APK/test hashes and retained sanitized failure diagnostics. Added
   ten focused runtime-helper regression tests and offline `fic diagnostics` with
   clean-wheel verification. Prepared requested BETA filenames/public metadata.
3. Beta4: repaired a stale Windows PE numeric-version assertion and added source
   version consistency preflight before expensive packaging.
4. Beta5: made BETA/version visible on Android startup/top bar and the Windows
   header; built all three formats from source `07f2f9f` and exercised the exact APK.
5. Before suspension, beta6: added the emulator37 process-local
   `ANDROID_I_WANT_MY_TCG=yes` opt-in when KVM is inaccessible and explicitly sized
   only newly created CI AVD data partitions to6GiB. Its source/tag were pushed;
   its final hosted Android/release results were still unknown at the freeze.

No speculative framework replacement, global tool upgrade, personal-data import or
new release publication occurred. Local source/tooling tests before freeze passed
47 Python tests and31 tooling tests; actionlint passed with ShellCheck unavailable.

## 4. TEST RESULTS

These scopes must not be mixed. Workflow links and preserved receipts are indexed
in [EVIDENCE_INDEX.md](EVIDENCE_INDEX.md). All beta5 rows below refer to `07f2f9f`.

| Verification | Actual result and evidence |
| --- | --- |
| Python beta5 | PASS:47 tests; six clean installation/workflow checks outside checkout. Hosted Windows/Linux run44737761258 PASS; local Python3.14.6 clean-wheel receipt also PASS |
| Windows beta5 | PASS:14 focused tests,28 packaged GUI checks,7 native input/restart checks, correct AMD64/GUI PE version. Standalone44737761558 PASS and combined-build receipts PASS |
| Android beta5 standalone | PASS:44737761362;97 JVM tests per Debug/Release,0 failures/skips; both APK assemblies and lint pass. Lint0 errors, Debug4/Release3 advisory warnings (`AndroidGradlePluginVersion`, `OldTargetApi`) |
| Combined beta5 build | Build job136293401154 PASS in44737774859: APK,EXE,wheel,sdist,sourceZIP,SBOM,notices,inventory produced. This is not final release success |
| Combined beta5 hosted runtime | **FAIL before APK installation**: emulator37.3.10 rejected `-accel off` without TCG opt-in. Overall44737774859 FAILED; finalization/draft skipped |
| Exact beta5 APK, local hardware emulator | **PASS:6 actual instrumentation tests**, API34 AOSP x86_64/WHPX. Installed exact combined-build APK/testAPK hashes; no source-run substitution |
| Beta5 Android real UI/SAF | PASS observations: visible BETA/version, location-denial recovery, VCF preview/cancel-empty/import2/duplicate-unchanged2, CSV mapping/preview cancellation, phone-contact denial, selected JSON export, report import/statuses/export, restart persistence |
| Beta6 Python | Local exact-clean source6335a0e:47 tests and six wheel checks PASS. Hosted44742159088 PASS |
| Beta6 Windows | Hosted44742159009 PASS; artifact metadata verified. Its detailed receipts were not downloaded during shutdown |
| Beta6 Android/combined |44742159219 and44742158905 **IN_PROGRESS at freeze**, no conclusion asserted. No beta6 exact APK downloaded or locally executed |

Local beta5 APK SHA-256:
`43c4e655aa09d813b0dc6fdac7d6d1d2c980d7142714b503727cc8acbdfe3641`.
Instrumentation APK SHA-256:
`8d44d53a4ada978139c7dd834ece1038997d0b18ae6f3f402f77649019e328bb`.
Windows EXE: `7897735842a5640e875d0d89b61432dc55cd51ae1d5089749fbdadc87fab6718`.
Wheel: `cb97002ee7b0b6f99d2ba0189cb5b956e23a61ce382c8beba5dde9a8650de6b0`.
All eight preliminary artifact hashes/sizes are in the preserved build receipt.

## 5. UNFINISHED TASKS

- Beta5 hosted Android runtime remains failed even though its exact APK passed
  locally. The current hosted exchange guard requires a successful exact-tag
  combined workflow; the failed beta5 run does not satisfy it.
- Beta6 hosted Android/combined conclusions, exact artifact hashes, installation,
  runtime and real document workflows were not confirmed. Do not relabel beta5
  evidence as beta6 evidence or infer success from the helper change.
- Exact APK → installed wheel → packaged EXE → fresh wheel exchange was not run
  for beta5/beta6. Two actual synthetic beta5 exports and their attestation are now
  preserved in Git so this evidence need not depend on chat/local files.
- Final11-asset manifest/checksum/notes bundle not finalized for beta5. No uploaded
  candidate byte-for-byte download verification or anonymous access verification.
- Current/new Actions logs and exact final assets still require contextual review.
  Historical source review:42 commits,502 blobs,229 paths;51 reviewed flag groups.
  Completed-log scan:56 runs,0 pattern findings. This is incomplete publication review.
- No public beta is published. Existing beta1 remains an unpublished draft
  release402457718 with11 preserved assets; beta5/beta6 have no release. Repository
  visibility remains private. Handoff expressly prohibits changing that state.
- README still describes a beta6 candidate under verification; it is not a
  publication receipt. Some historical/root reports are stale. This handoff controls.

## 6. KNOWN ISSUES

**Emulator storage.** The new local AVD initially defaulted to10GiB and required
about12GiB free; only about9GiB was available. Setting this newly created, empty AVD
to6GiB allowed boot with WHPX. No existing AVD/user data was deleted. Owned name
`FIC_MISSION001_API34`, serial`emulator-5582`, launcherPID22752; local SDK
`.local/android-sdk`, AVD root`.local/avd-mission001`. It was positively identified,
stopped through that serial, and launcher exit verified during shutdown. Images and
synthetic app data are preserved. Existing`emulator-5556` is unrelated and untouched.

**Host/runtime setup.** Global SDK35+/API37 did not supply required SDK34. Project
JDK17/SDK34 were prepared locally; no unrelated global SDK/Java replacement. The
global avdmanager ignored the intended SDK; copying installed command-line tools
into the project SDK let it discover API34. Do not repeat this investigation.
Project JDK: `.local/tools/jdk17/jdk-17.0.21.1+2`. System Python3.14.6; hosted3.14.7.
Old copied virtual environments referenced missing paths; core/CI environments were
rebound. The old Windows venv is not established as usable.

**Workflow failures.** Beta2 combined37121395951 failed on package-service Broken
pipe during install; its artifacts expired. Beta3 Windows44736037415 passed14/28/7
then failed an obsolete PE numeric assertion; beta4 fixed it. Beta4 combined44737108366
was cancelled to rebuild with visible BETA labels. Beta5 hosted runtime failure is
the newer emulator's explicit software-mode requirement, not a demonstrated APK
failure. Beta6's repair is committed but its full result remains pending. Keep bounded
timeouts and evidence; never treat an infrastructure failure as an application PASS.

**UI driver observation.** An initial synthetic report export was interrupted by an
over-fast force-stop in the local driver, leaving an empty export. It was repeated
and completed to1258 bytes. Only completed exports are preserved in Git. No app fix
was inferred. The locally misnamed network export was read back successfully; its
exact bytes, rather than that temporary filename, identify the preserved evidence.

**Application limits.** No physical Android/ARM64 runtime, live GPS, real-provider
key or production signing validation. The AOSP emulator lacks Google Play services;
FusedLocationProvider cannot establish real-device GPS coverage. In-app AI/weather/
CAD hooks are not all connected. Historical Room-v1 evidence is reconstructed,
not an authentic old exported schema. Databases are unencrypted; VCF/CSV are bounded
subsets; unsigned Windows and testing-signed Android require honest release notes.
No performance optimization beyond measured/confirmed needs was invented.

## 7. ENGINEERING DECISIONS

Preserve Kotlin/Compose/Room, Python/Tk, app IDs, schemas, retained signing identity,
original repository and immutable tags. Use incremental fixes. One coordinator
owns Git, SDK and final release builds. Never use real contacts/databases in tests.
Keep raw evidence/caches/images under ignored`.local/evidence/`; commit sanitized
summaries only. Use exact source/tag/hash bindings and fail-closed release guards.
No force-push, retagging, uninstall-to-bypass-signature, security-policy weakening,
automatic contact sync or fabricated test/publication claims.

The owner's earlier public-delivery authorization was suspended by the latest stop
instruction. Future engineering/publication requires an explicit owner resumption.
Do not execute prepared local publication helpers while suspended.

## 8. CONTINUATION PRIORITIES

After explicit resumption, first read
[NEXT_CODEX_START_HERE.md](NEXT_CODEX_START_HERE.md), verify refs and inspect existing
workflow conclusions/artifacts once. Recover evidence before seven-day retention
expires (most current artifacts expire2026-11-27). Do not immediately rebuild.

The smallest remaining path is validation and delivery, not more feature work:
select the exact immutable candidate with the owner context; inspect the already
running beta6 repair results; execute missing exact-artifact Android/SAF and hosted
exchange gates; finish contextual review, final checksums and uploaded download
verification. Publication/visibility must be reauthorized after suspension.

To complete **beta5 specifically**, keep tag07f2f9f unchanged. Its exact local APK
runtime/SAF evidence is reusable only for those bytes. The hosted workflow at that
tag has the confirmed emulator37 issue; blindly rerunning it is not a repair, and
the existing exchange gate rejects its failed run. A future coordinator must resolve
that hosted validation requirement with explicit, traceable evidence. The already
preserved beta6 is the source-level fix; adopting it means a beta6 release, not a
beta5 relabel. Never weaken a gate or move beta5 to disguise this distinction.
