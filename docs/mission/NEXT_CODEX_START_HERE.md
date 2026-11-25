# Mission 007 · Public GitHub launch

The repository is public. Beta11 dev1 is the recommended experimental download. See [Mission 007](MISSION_007.md) for the release archive and anonymous integrity checks. The physical Vivo and official receiving-app checks from Mission 006 remain open.

# Mission 006 · Human Context Intelligence

Start at the beta11 dev1 product commit `cc2df9c69c8dfe2595be642f5573bdc5d6034462` on `v4-development`. The private testing release includes the Android APK, Windows EXE, Python wheel, installation notes and checksum file. Review the direct beta10 upgrade receipt and use the Vivo procedure in section 15 of the mission report. The remaining checks require a physical phone and the receiving apps. [Mission 006](MISSION_006.md) · [Package and test receipts](evidence/mission-006/verification.json) · [Private testing release](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.11.dev1)

# Mission 005 · Cognitive Experience Engine

Start with the private beta10 dev1 testing release from `43175c2fd97988c6095a1bff10060a860c34da13`. The product includes in-app source exploration and Android 2D recovery after WebView renderer loss. Continue with the physical Vivo upgrade and receiving-app checks. [Mission, installation and recovery](MISSION_005.md) · [Package hashes and execution receipts](evidence/mission-005/verification.json)

# Mission 004 · beta9 development testing

Start at product source `ccb3fe8c574c7ac1bd14bc026ecaf271bc4ce985` on `v4-development`. The private testing prerelease contains the APK, Windows EXE, Python wheel and checksums. WORLD INTELLIGENCE opens the Cognitive Earth Engine from the original Matrix. The next gate needs a physical Android phone. [Testing prerelease](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.9.dev1) · [Mission 004 verification and phone procedure](MISSION_004.md) · [Distribution receipt](evidence/mission-004/verification.json)

# Mission 003 · development

On 2026-11-25 the owner authorized private distribution of the archived `v4.0.0-beta.8.dev1` development build at `1bdad1607736306278bfb49f0d39014935d30bd6`. The APK, Windows EXE, Python wheel and checksum file were downloaded from the published release and verified. Beta7 remains the verified baseline. [Testing prerelease](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.8.dev1) · [Download verification](evidence/mission-003/testing-prerelease.json)

Resume with a separate Android 10+ test phone where `com.fieldconsole.debug` is absent. The beta7 and dev1 signers differ. Verify the native Google Earth destination and official ChatGPT composer with the existing synthetic fixtures and record what survives returning to FIC. Final beta8 is pending. [Installation and recovery](MISSION_003.md#installation-and-recovery)

# Mission 002 · 2026-11-24

Mission 002 completed. The [beta7 prerelease](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.7) is published in the private repository. Start with [the recovery record](MISSION_002.md) and [exact release evidence](evidence/2026-11-24/).

# NEXT CODEX — START HERE

**ENGINEERING SUSPENDED — RECOVERABLE HANDOFF (2026-11-20)**

Read this before the historical mission, root reports or README. The owner stopped
development and publication. Do not resume merely because older MISSION/AGENTS text
describes autonomous authority. Obtain a new owner instruction to resume; no work,
build, environment experiment, visibility change or release is authorized now.

## Identity and objective

Existing project: `C:\xampp\FieldIntelligenceConsole`.
Existing/private repository: https://github.com/chatgptopenaiagi/Field-Intelligence-Console
Branch/default: `v4-development`. Preserve all history, branches, tags and releases.
The original goal was to recover and improve FIC, verify real Windows EXE, Android
APK and Python wheel from one source, then publish a public BETA. It is incomplete.
No replacement project/repository, no unrelated Windows research or new session.

Owner-named version: **4.0.0-beta.5**, frozen source
`07f2f9f25a47f6771e149b38970914911471d4fa`.
Actual source HEAD immediately before shutdown was already **4.0.0-beta.6**,
`6335a0e4c64a1d50af2436454a579818b0ea724b`, tagged/pushed before the stop arrived.
The handoff commit is a documentation-only descendant. Do not reset to beta5,
retarget either tag, silently discard beta6, or claim its unverified results passed.

Architecture: original Kotlin/Compose/Hilt/Room Android app in`app/`; shared-schema
Python core/CLI/SQLite in`python/`; native Tk/ttk/PyInstaller Windows workstation
in`windows/`; v1 contracts/synthetic fixtures in`shared/`; gates in`tooling/` and
`.github/workflows/`. Detailed module paths are in the
[principal handoff](FINAL_HANDOFF_2026-11-20.md).

## RESUME FROM THIS EXACT POINT

1. Read `FINAL_HANDOFF_2026-11-20.md`, `EVIDENCE_INDEX.md`, the bounded committed
   receipts in`docs/mission/evidence/2026-11-20/`, then current`RESUME.md`/`STATUS.md`.
   Older entries and `FIC_FINAL_REPORT.md` may refer to earlier/private candidates.
2. Verify source/ref identity with read-only commands below. The recovered baseline
   and beta1–6 tags must remain. The documentation commit can be discovered by its
   exact message; do not expect HEAD to equal a binary source SHA.
3. After owner resumption, inspect existing workflows before doing engineering.
   At freeze, beta6 Windows/Python passed; Android and combined were in progress.
   Do not wait on or restart them merely to produce a new checkpoint.
4. Recover existing artifacts/receipts. Beta5 exact binaries were built successfully;
   its combined workflow failed before hosted APK install. Local exact APK tests6
   and real SAF workflows passed. This evidence is now committed, including two
   synthetic exports. Full binaries remain local and in expiring Actions artifacts.
5. Decide which immutable candidate is being completed. Beta6 already contains the
   confirmed emulator37 setup repair. Completing beta5 unchanged requires resolving
   its hosted runtime/exchange requirement honestly; a failed combined run cannot
   pass the existing exchange guard. Do not rerun known-broken beta5 YAML unchanged
   or relabel beta6 artifacts as beta5.

### Read-only recovery commands (PowerShell7)

```powershell
Set-Location -LiteralPath C:\xampp\FieldIntelligenceConsole
git status --short
git branch --show-current
git remote get-url origin
git log -3 --oneline
git log -1 --format=%H --grep='docs: preserve Mission 001 beta5 engineering handoff'
git rev-parse 'v3-recovered-baseline^{commit}'
git rev-parse 'v4.0.0-beta.5^{commit}'
git rev-parse 'v4.0.0-beta.6^{commit}'
git ls-remote origin refs/heads/v4-development 'refs/tags/v4.0.0-beta.*'
gh repo view chatgptopenaiagi/Field-Intelligence-Console --json visibility,defaultBranchRef,url
gh run view 44742158905 --repo chatgptopenaiagi/Field-Intelligence-Console --json status,conclusion,jobs,url
gh run view 44742159219 --repo chatgptopenaiagi/Field-Intelligence-Console --json status,conclusion,jobs,url
```

Expected baseline:e45f9b863892b1796774a8132a213ea99c4518d2;
beta5:07f2f9f25a47f6771e149b38970914911471d4fa;
beta6:6335a0e4c64a1d50af2436454a579818b0ea724b. `origin` must be the original URL.
If the local directory is unavailable, clone this existing private repository with
authorized access. No untracked script/chat context is necessary to understand the
checkpoint. Raw screenshots, AVDs and binaries may need local recovery/rebuild if
Actions retention has expired; summaries and exact hashes survive in Git.

### Existing runs and acceptance

| Source | Workflow | ID / last known state |
| --- | --- | --- |
| beta5 | Python |44737761258 PASS (Windows/Linux) |
| beta5 | Windows |44737761558 PASS:14focused/28GUI/7native |
| beta5 | Android |44737761362 PASS:97 per variant, assemblies/lint,0 lint errors |
| beta5 | Combined |44737774859 FAIL; build passed, hosted emulator refused software mode |
| beta6 | Python |44742159088 PASS; local47+6 clean-wheel PASS |
| beta6 | Windows |44742159009 PASS |
| beta6 | Android |44742159219 IN_PROGRESS; conclusion unknown at freeze |
| beta6 | Combined |44742158905 IN_PROGRESS; conclusion unknown at freeze |

For an authorized continuation, retrieve beta5 existing build without rebuilding:

```powershell
gh run download 44737774859 --repo chatgptopenaiagi/Field-Intelligence-Console --name fic-candidate-build --dir .local/recovery/beta5-build
gh api repos/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742158905/artifacts
```

Use a new empty destination. Beta5 artifact14629164482 is verified present at
handoff and expires2026-11-27. Beta6 combined had no uploaded artifact at the
snapshot; query its actual later state. Successful final release jobs emit
`fic-verified-experimental-assets` (eleven assets); the preliminary build has eight.
Different standalone/combined builds can have different bytes; bind runtime to the
actual distributed APK, not merely the version string.

## Completed work and what not to repeat

Original architecture and repository discovery are complete. Do not investigate
unrelated Windows projects or replace Kotlin/Tk. Preserve recovered eight actions,
bounded providers/cache states, Room migrations, Known Network and report exchange.
Current continuation added package-service readiness/non-streaming install retries,
offline diagnostics, PE/version guards, visible BETA labels and the beta6 emulator
compatibility fix. See CHANGELOG and immutable commits for the implementation.

Do not repeat passed Python47/6, Windows14/28/7 or Android97-per-variant gates for
the same bytes without a new reason. Failed gates are not passes. No live keys,
personal contacts, physical Android, ARM64 runtime or GPS coverage was established.
Existing fixtures and preserved synthetic exports are the allowed test material.

Local JDK17:`.local/tools/jdk17/jdk-17.0.21.1+2`; SDK34:`.local/android-sdk`;
owned AVD root:`.local/avd-mission001`, name`FIC_MISSION001_API34`, serial5582,
6GiB data partition. It is **stopped**, files preserved. Never use/stop unrelated
emulator5556. Do not repeat the failed10GiB storage setup or use the global
avdmanager that resolves unrelated API37. Existing project-local command-line tools
resolve API34 correctly. No global upgrades are needed for recovery.

## Remaining release sequence after explicit owner resumption

1. Inspect pending beta6 conclusions and existing artifacts. Resolve concrete failures
   using their logs; do not invent another candidate just for bookkeeping.
2. On the selected candidate, require same-source clean receipts, Android97 tests per
   variant/both assemblies/lint0 errors, Python47 and six clean-wheel workflows,
   Windows14/28/7 and valid PE metadata. Require actual exact-APK instrumentation6
   and runtime identity/hash receipts. Use `tooling/android_runtime.py` with an
   explicitly owned API34/x86_64 serial; it never authorizes clearing user data.
3. Exact distributed APK must perform real SAF preview/cancel/import/duplicate/export,
   report exchange and restart. Beta5 observations/exports are preserved. Beta6 has
   no local runtime/SAF proof yet. Retained signing enables update installation;
   never uninstall to bypass a mismatch. No beta5 receipt may attest beta6 bytes.
4. Run `.github/workflows/exchange-ci.yml` only after its exact combined run passes.
   Inputs: annotated`release_tag`, successful`candidate_run_id`, base64 network/report
   JSON and Android receipt. The current guard requires synthetic2entities/1report,
   file hashes, source SHA, package and exact APK hash. It exercises two clean wheel
   installs and the actual EXE28GUI workflows; exact entities/ancestry/report must match.
   `tooling/hosted_exchange.py` defines the contract; committed beta5 synthetic files
   can be renamed locally to its`device-network.json`/`device-report.json` inputs.
5. Finalize only same-source/hash-bound PASS evidence with
   `tooling/release_candidate.ps1 -Stage Finalize` and `tooling/finalize_release.py`.
   Exact11-asset allowlist, manifest, checksums, SBOM/notices and source archive commit
   must validate. The finalizer's historical “Beta5 repairs…” paragraph and
   `RELEASING.md`'s beta6/code8 typo should be corrected in future delivery docs;
   actual beta6 metadata is code9. Do not retag source to disguise documentation edits.
6. Finish source/history/Actions-log/exact-asset contextual review. Scans alone do
   not authorize publication. Obtain renewed visibility/publication authority;
   preserve beta1's draft and all older tags/releases. Upload explicit allowlist to
   original repo only, download all11 files and compare bytes/hashes, verify prerelease
   flags and actual anonymous accessibility if public release is again authorized.
7. Update README/release report to actual delivered state. Do not claim COMPLETE until
   all mandatory targets and authorized publication gates pass. Any new source repair
   requires its own immutable versioned artifacts; documentation-only handoffs do not.

There is no reason to run the ignored local `deliver_public_beta.py` during this
suspension. It is unexecuted, incomplete delivery preparation, not a release receipt.
