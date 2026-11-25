# Mission 004

[Packaged verification and recovery](MISSION_004.md) · [Testing prerelease inventory](evidence/mission-004/verification.json)

# Mission 003

[Development validation and remaining release gates](MISSION_003.md) · [Development artifact inventory](evidence/mission-003/development-artifacts.json) · [Synthetic ChatGPT captures](evidence/mission-003/synthetic-payloads.json)

# Mission 002 · 2026-11-24

Mission 002 completed. The [beta7 prerelease](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.7) is published in the private repository. Start with [the recovery record](MISSION_002.md) and [exact release evidence](evidence/2026-11-24/).

# MISSION 001 — EVIDENCE INDEX

**ENGINEERING SUSPENDED — RECOVERABLE HANDOFF, 2026-11-20.**

Read [FINAL_HANDOFF_2026-11-20.md](FINAL_HANDOFF_2026-11-20.md) for result scope.
No tests were executed to create this index. Existing receipts were copied and
bounded observations recorded during shutdown. Raw logs/screenshots are not tests
by themselves; a build receipt is not proof of final publication.

Local base below: `C:\xampp\FieldIntelligenceConsole\.local\evidence\mission001-20261120\`.
Committed evidence directory: [evidence/2026-11-20/](evidence/2026-11-20/).
`B5` means `07f2f9f25a47f6771e149b38970914911471d4fa`;
`B6` means `6335a0e4c64a1d50af2436454a579818b0ea724b`.
The evidence directory is included in the handoff commit; remote preservation is
verified by matching the pushed branch SHA. It contains no binaries, SDKs, AVDs,
credentials, real personal contacts or user databases. JSON export names/values are
the explicitly synthetic test fixtures. `evidence-integrity.json` records file hashes.
The scoped `.gitattributes` disables JSON newline conversion so another checkout
retains those exact bytes. The imported report deliberately retains its fixture's
original `app_version` beta1; its executed APK/source are beta5, as the attestation
records. Do not rewrite report provenance to match the executing application.

## Durable receipts and local originals

| Description / source | Original path under local base | Result | Git preservation / remaining local need |
| --- | --- | --- | --- |
| Combined build, B5 | `beta5-build/build-receipt.json` | BUILD_PASS;8 preliminary assets | `beta5-build-receipt.json`; complete original binaries still needed for delivery |
| Android units/assemblies/lint/signatures, B5 | `beta5-build/receipts/android.json` | PASS;97 per variant;0 lint errors | `beta5-android-gate.json`; runtime separately scoped |
| Windows packaged gate, B5 | `beta5-build/receipts/windows.json`, `windows-ui.json`, `windows-native.json`, `pe-metadata.json` | PASS;14/28/7,AMD64 GUI/version | `beta5-windows-gate.json`, `beta5-packaged-ui.json`, `beta5-native-ui.json`, `beta5-pe-metadata.json` |
| Python combined gate, B5 | `beta5-build/receipts/python.json` | PASS;47tests/6wheel workflows | `beta5-python-gate.json` |
| Local clean wheel, B5 | `python-beta5-clean/gate-receipt.json` | PASS,Python3.14.6,source clean | `beta5-local-clean-wheel.json`; package bytes differ from hosted build, not interchangeable hashes |
| Local clean wheel, B6 | `python-beta6-clean/gate-receipt.json` | PASS,47/6,source clean | `beta6-local-clean-wheel.json`; no beta6 APK runtime inference |
| Exact local APK runtime, B5 | `beta5-build/android-runtime.json`, `android-runtime-diagnostics.json`, `local-exact-apk-runtime.log` | PASS,6 instrumentation tests | `beta5-local-android-runtime.json`, `beta5-local-runtime-diagnostics.json`; raw log remains local |
| Actual Android UI/SAF, B5 | `android-ui/`, `android-ui-actions.jsonl` | PASS observations detailed in handoff | `beta5-android-saf-observation.json`; XML/screenshots/action journal remain local and are not uploaded |
| Selected network export, B5 | `android-ui/device-network-beta5.json` |2synthetic entities;4621bytes | `device-network-beta5.json`; exact bytes preserved; attestation has hash |
| Completed report export, B5 | `android-ui/device-report-beta5.json` |1synthetic report;1258bytes | `device-report-beta5.json`; interrupted empty export excluded |
| Artifact privacy scan, B5 | `beta5-build/receipts/artifact-scan.json` |0findings/0incomplete;manual review still required | `beta5-artifact-scan.json`; not publication authorization |
| Source/history/log/draft review | `history-beta6.json`, `history-context.json`, `actions-scan.json`, `actions-logs/`, `historical-beta1-assets-scan.json` |42commits/502blobs;51context-reviewed flags;56completed log scans/0patterns;5historic packages/0findings | `privacy-review-progress.json`; full inventories/logs remain local; current/new logs and final assets still need review |
| Workflow snapshot, B5/B6 | `handoff-workflows.json` | Last-known states, not awaited | `workflow-snapshot.json`; current later state must be queried |
| Remote artifact presence, B5/B6 | `handoff-artifacts.json` | Metadata verified from original GitHub API at shutdown | `actions-artifact-snapshot.json`; names,IDs,hashes,expiry; empty B6 build lists mean not yet uploaded at snapshot |
| Existing release snapshot | `handoff-releases.json` | Only beta1 draft402457718,11assets;no public beta | `release-snapshot.json`; original draft retained unchanged |
| Owned emulator shutdown | `handoff-process-stop.json` | STOPPED,PID22752/serial5582 identity verified | `owned-emulator-stop.json`; AVD data stays local; unrelated5556 untouched |

The local beta5 APK, EXE and wheel are in `beta5-build/assets/` with requested names:

- `FieldIntelligenceConsole-BETA-Android.apk` (60,654,713bytes)
- `FieldIntelligenceConsole-BETA-Windows-x64.exe` (16,188,389bytes)
- `field_intelligence_console-4.0.0b5-py3-none-any.whl` (30,096bytes)

The build receipt records their exact hashes plus sdist, sourceZIP, SBOM, notices
and inventory. Test APK:`beta5-build/android-test/app-debug-androidTest.apk`.
The eight-asset preliminary bundle is not the final eleven-asset release.

## Verified GitHub Actions artifacts

All entries below were reported present/not expired by GitHub during handoff. Their
default retention is seven days, generally expiring **2026-11-27**. This is remote
presence at the snapshot, not a promise that a future download remains available.
The committed JSON snapshot includes exact expiry times, sizes and archive digests.
GitHub artifact archive digests differ from hashes of the files inside the archive.

| Source / run | Artifact name and ID | Remote result / recovery |
| --- | --- | --- |
| B5 combined [44737774859](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44737774859) | `fic-candidate-build`14629164482; `fic-inventory-diagnostics`14629159640 | Build PASS/runtime FAIL; full eight-asset candidate + testAPK + build receipts available; exact local runtime/SAF receipts are committed separately |
| B5 Windows [44737761558](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44737761558) | `windows-experimental`14630051054; `windows-evidence`14629806568 | PASS; standalone bytes need not equal combined bytes |
| B5 Android [44737761362](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44737761362) | `fic-android-apks-07f2f9f25a47f6771e149b38970914911471d4fa`14629818272; matching `fic-android-evidence-…`14629264471 | PASS; full unit/lint summaries and APKs |
| B5 Python [44737761258](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44737761258) | Linux package14629740955/evidence14629491526; Windows package14629146966/evidence14629601484 | PASS; full names in JSON snapshot |
| B6 Windows [44742159009](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159009) | `windows-experimental`14630329776; `windows-evidence`14630808033 | PASS; remote metadata verified; detailed receipt not downloaded at shutdown |
| B6 Python [44742159088](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159088) | Linux package14630862400/evidence14631810742; Windows package14630718598/evidence14631222795 | PASS; full names in JSON snapshot |
| B6 combined [44742158905](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742158905) | No artifact at snapshot | IN_PROGRESS; do not assume later success or assets |
| B6 Android [44742159219](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159219) | No artifact at snapshot | IN_PROGRESS; no wait performed for handoff |

Example after owner-authorized resumption:

```powershell
gh run download 44737774859 --repo chatgptopenaiagi/Field-Intelligence-Console --name fic-candidate-build --dir .local/recovery/beta5-build
gh api repos/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742158905/artifacts
```

Use a fresh destination and verify against the committed build receipt before
executing anything. If artifacts expire and local binaries are lost, source can be
rebuilt from immutable tags, but newly built bytes require fresh hash-bound runtime
evidence. Byte reproducibility was not established. Do not fabricate recovered files.

## Historical and unpreserved material

Beta1 draft assets were previously downloaded under
`.local/evidence/private-release-beta1/downloaded/` (outside the local base above).
All11 files matched current remote digests at handoff; five package scans found0
issues. The draft remains unpublished and unchanged. Its asset IDs/digests are in
`release-snapshot.json`; it must not be accidentally published as the new beta.

Beta2 combined37121395951 failed installation; its old seven-day artifacts expired.
Beta3 Windows44736037415 failed the obsolete PE assertion; beta4 Windows44737100285
and Python44737100291 passed; combined44737108366 was cancelled for visible BETA
labels. These historical results do not establish current release acceptance.

No SDK/emulator images, virtual disks, dependency caches, real databases, secrets,
raw archive logs or unrelated files are committed. Project-local AVD/SDK/JDK and
ignored helper scripts may be useful locally but are not needed to understand the
recovery instructions. Exact source, receipts, synthetic exchange data, failures,
commands and acceptance criteria are preserved in this repository.
