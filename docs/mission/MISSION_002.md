# Mission 002 recovery record

Mission 002 resumed on 2026-11-24 in the existing private repository. The [beta7 prerelease](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.7) is published in that repository. All eleven published downloads were compared byte for byte with the verified candidate.

## Recovered checkpoint

The clean local and remote `v4-development` branches pointed to handoff `d76f1711b885bda8b420fba916c1cbece57482d5`. Tags beta1 through beta6 and the recovered baseline were present. GitHub listed only the beta1 draft, release 402457718 with eleven assets.

Beta5 source `07f2f9f25a47f6771e149b38970914911471d4fa` passed local exact-APK runtime and Android document workflows. Its combined hosted run failed before installation. Beta6 source `6335a0e4c64a1d50af2436454a579818b0ea724b` superseded beta5. Its [Android](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159219), [Windows](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159009) and [Python](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159088) runs passed. Both APKs installed on the first attempt in [combined run 44742158905](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742158905). Software-mode instrumentation exceeded 600 seconds, returned exit 124 and produced no passing count. Finalization was skipped. The beta6 finalizer also required Android code 8 while the APK has code 9.

The eight beta6 assets were recovered from Actions artifact 14632013408. Every size and SHA-256 matched the build receipt in `.local/evidence/mission002/beta6-build/`. The hosted failure receipt is in `.local/evidence/mission002/remote-diagnostics/`.

## Beta7 candidate

The version is 4.0.0-beta.7, Android code 10, Python 4.0.0b7 and Windows 4.0.0.7. The Kotlin/Compose/Room Android app, native Tk Windows workstation, Python CLI/SQLite core and shared v1 contracts retain the architecture described in [ARCHITECTURE.md](../ARCHITECTURE.md). Release engineering changes are in [CHANGELOG.md](../../CHANGELOG.md) and the private-repository procedure in [RELEASING.md](../../RELEASING.md).

Recover the exact shipped source with `git rev-parse 'v4.0.0-beta.7^{commit}'`. Query workflows by that SHA. The mission-owned local API 34 AVD is FIC_MISSION002_API34 on emulator-5584. Use only synthetic fixtures.

## Verification on the candidate binaries

The annotated beta7 tag resolves to `8987a0c894dba9002cde1820e9e185e3766ef865`. [Android](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45482458460), [Windows](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45482458470) and [Python](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45482458393) all passed. The build job of [the combined workflow](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45482458425) produced eight preliminary assets. All eight downloads matched the build receipt. The source archive's 255 files matched the tagged Git blobs after accounting for Windows checkout line endings.

| Gate | Executed result |
| --- | --- |
| Windows x64 | 14 focused tests, 28 packaged GUI checks and 7 native input/restart checks passed. The PE receipt records AMD64 GUI and file version 4.0.0.7. |
| Python | 47 unit tests and 6 clean-wheel workflow checks passed. CI tested Windows and Linux. |
| Android build | 97 JVM tests per variant, zero failures, errors or skips. Both assemblies and lint passed. Debug has 4 advisory warnings and release has 3. |
| Android installation and runtime | The exact combined APK installed over the retained beta6 testing certificate on the mission-owned AOSP API 34 x86_64 emulator. All 6 instrumentation tests passed. Beta6 also passed all 6 locally before the update. |
| Android documents and navigation | Startup displayed beta7. Location denial showed an actionable error. VCF preview/cancel preserved an empty store, import added 2 contacts and reimport retained 2. CSV mapping preview parsed 1 record and was canceled. File import remained available after denying phone contacts. The system document picker exported 2 selected contacts and 1 imported report. Restart preserved both contacts and the report. |

The file picker tests used the fixtures in `shared/fixtures/`. The installed APK hash was read back from Android. Its network export is 4,621 bytes and its report export is 1,258 bytes. The historical version inside the imported report belongs to the fixture. The [receipts and exact exports](evidence/2026-11-24/) bind them to the beta7 binary and source.

Raw local evidence is in `.local/evidence/mission002/`. The optional AVD at `.local/evidence/mission002/avd/` was stopped via its verified name and serial, and ADB confirmed its exit. No physical device, ARM64 runtime, live GPS or provider key was tested. The APK uses the retained testing certificate and the Windows executable is unsigned.

## Independent finalization and exchange

Beta7 combined attempt 1 failed during hosted app installation on the software emulator. The approved fallback ran after the runner denied KVM group access. The first install returned a transient transport error. The second exited 255 without a specific package-manager error. The original build succeeded, so only the failed jobs were requested again.

From a clean detached checkout of the beta7 tag, the release finalizer validated the original eight binaries/metadata files against the build receipt and the local exact-APK runtime receipt. It produced eleven files under `.local/evidence/mission002/beta7-local-finalize/assets/`, including installation instructions, limitations, inventory, release manifest and checksums. [Local inventory](evidence/2026-11-24/beta7-local-inventory.json).

The same exchange implementation used by CI ran locally on a separate, non-interactive Windows desktop. Two fresh wheel environments, the packaged EXE and the Android document-picker exports passed exact entity, ancestry and report comparison. The [local exchange receipt](evidence/2026-11-24/beta7-local-exchange.json) records all three distribution hashes and the packaged UI count. Hosted receipts and release readback are tracked separately.

## Release and recovery

The [combined workflow, attempt 2](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45482458425/attempts/2) reused the first build and passed six hosted instrumentation tests in software mode. The hosted finalizer verified all eleven files. The [hosted exchange run](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/45489676633) used the beta7 SAF exports and the same APK, EXE and wheel hashes. Both installed-wheel passes and the 28 packaged GUI checks passed. Two contacts, their ancestry and one complete report matched after the round trip.

The coordinator reviewed the tag and source archive, incremental history and Actions logs, build scan, SBOM and notices. The two new historical findings were synthetic fixture coordinates and example.org email addresses. The notice email is the upstream libbzip2 license attribution. See the [publication review](evidence/2026-11-24/publication-review.json).

The [private-repository prerelease](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.7) contains eleven assets. All eleven were downloaded from the draft, compared with the candidate, then downloaded and compared again after publication. The release readback confirmed the tag, prerelease flag, published state and private repository access. Beta1 remains a separate draft. The [release verification](evidence/2026-11-24/release-verification.json) lists exact sizes and SHA-256 hashes, including the checksum file's own hash. [Manifest](evidence/2026-11-24/beta7-release-manifest.json) · [Exchange receipt](evidence/2026-11-24/beta7-hosted-exchange.json) · [Workflow results](evidence/2026-11-24/beta7-workflows.json).

For recovery on another machine, use repository access to download the release with `gh release download v4.0.0-beta.7 --repo chatgptopenaiagi/Field-Intelligence-Console --dir beta7-download` into an empty directory. Use the committed release-verification inventory to verify the byte count and SHA-256 of every file. Read `RELEASE_NOTES.md` in that directory for installation and limitations. The three deliverables and the other eight release files also remain together locally in `.local/evidence/mission002/published-download/`.

Release acceptance is complete. Any future binary change needs its own version, tag and exact-artifact evidence. The hardware and live-provider coverage limits above remain. The latest handoff is the documentation-only commit with message `Record published beta7 prerelease and verified asset inventory [skip ci]` on `v4-development`.
