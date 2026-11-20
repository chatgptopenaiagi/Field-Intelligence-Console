# MISSION 001 — ENGINEERING DISCOVERY

## PROJECT DISCOVERY

Canonical root: `C:\xampp\FieldIntelligenceConsole`. Starting branch
`v4-development`, commit `0c0775d84ec7f3716c185da00c776a67035894c6`.
Original connected repository:
https://github.com/chatgptopenaiagi/Field-Intelligence-Console.
GitHub authenticated ownership and admin/write access verified on 2026-11-20;
repository initially PRIVATE, default branch `v4-development`. No replacement repo.

FIC turns a place and moment into awareness, intelligence and persistent field
memory. The recovered Android application uses Kotlin, Jetpack Compose, Hilt,
Room, location services and external HTTP providers. Its Action Matrix retains
INFO SCAN, HISTORY, NAVIGATION, AI ANALYSIS, RISK / SAFETY, LOGISTICS, FINANCIAL
and EMERGENCY EXIT. Saved observations and provider caches are local Room data.
UI/ViewModel state and DAO flows support the original workflow; no server is needed.

| Area | Existing implementation |
| --- | --- |
| `app/`, root Gradle files, `gradle/` | Original Android app, resources, migrations, unit/device tests, pinned wrapper |
| `python/` | Independent Python core/CLI, JSON schema validation, SQLite storage, import/export, geographic queries |
| `windows/` | Tk/ttk desktop workstation sharing the core, PyInstaller portable executable, UI/native tests and licenses |
| `shared/` | FieldReport/NetworkDataset version-1 schemas and explicitly synthetic interoperability fixtures |
| `tooling/`, `.github/` | Local/hosted gates, exact-artifact checks, privacy scans, SBOM and release packaging |
| `docs/`, root Markdown | Architecture, ADRs, recovery history, user/build instructions and mission evidence |
| ignored `.local/`, build folders, virtual environments | Historical artifacts, synthetic databases, local logs, SDK/AVD/cache data; excluded from source/publication |

The root EXE and older candidate APK/EXE/wheel sets are preserved historical
artifacts. Their existence does not verify this candidate. User databases and
personal address books were not opened. The untracked owner prompt `1 PROMT.txt`
is retained unchanged and excluded only through local Git info/exclude.

## DEVELOPMENT HISTORY

The recovered baseline is `e45f9b863892b1796774a8132a213ea99c4518d2`, protected by
`v3-recovered-baseline`. The October recovery record says Git initially had no
commits/refs and preserved the 74 staged recovered files as that root commit.
Therefore the user-reported earlier Manus AI development cannot be authenticated
from pre-recovery Git history. No distinct Manus artifact was identified in the
tracked tree. Do not invent earlier commits, dates, releases or authorship.

The preserved tags are beta1 at `e6ad92573e832c75684d2464fcc4b81042e42788` and beta2
at `296730438928717404c99bab8d0ba080659abd49`. Beta1 is an unpublished draft;
beta2 has no release. The initial history inventory covers 38 commits, 389 unique
blobs and 224 paths. Only the official Gradle wrapper is binary; four data-file
paths are synthetic VCF/CSV fixtures. Full contextual/public exposure review is
separate from pattern scanning.

## RECOVERED FUNCTIONALITY AND LIMITATIONS

Prior recovery repaired bounded Overpass requests, distinct empty/failure states,
stale-cache provenance, geographic filtering, geocoder completion, explicit Room
migrations and human-confirmed dialer actions. It added local Known Network
preview/cancel/confirm, duplicate-aware import, editing/search, geographic queries,
selected export and shared field-report exchange on Android/Windows/Python.
Saved addresses do not establish current physical presence. Ordinary reports and
AI packets exclude contact details and counts. Schemas remain version 1; Room 3.

Disconnected in-app AI/weather/CAD prototypes remain documented limitations.
The original external AI prompt action remains. Optional providers are not tested
with real credentials. Historical Room-v1 evidence is reconstructed; physical
Android/ARM64 runtime testing is not established. Databases are not encrypted.
No framework replacement or speculative performance rewrite is warranted.

## SYSTEM DISCOVERY

PowerShell 7.7, Python 3.14.6, Node/npm, Git/GitHub CLI, CMake/Ninja, Flutter,
Android SDK/ADB and Android Studio are present. Qt/PySide and Visual Studio builds
are unnecessary for the existing Tk/PyInstaller/Kotlin architecture. Default
Java 27 is incompatible with the pinned build; project-local Temurin JDK17 was
downloaded from Adoptium and SHA-256 verified. No global Java change was made.
Copied Python environments referred to a missing interpreter and old workspace;
the core environment was rebound locally. Required SDK34 components are being
prepared locally. An existing emulator on port5556 is unrelated and untouched.

## CURRENT CONFIRMED DEFECT

The paused combined release run37121395951 built all three targets successfully,
then failed installing its APK on the software emulator: package service returned
`Broken pipe (32)`. No instrumentation/finalization/publication followed. Its
seven-day CI artifacts expired before this session. A new beta3 source candidate
must be built and verified; earlier PASS records cannot stand in for it.

Repair: wait for both boot completion and a usable package service; use ADB's
non-streaming installation; retry only transient transport failures within a fixed
budget; fail on deterministic installation errors; preserve bounded diagnostics.
Add offline `fic diagnostics`, maintain explicit BETA versions and the requested
distribution names. Runtime verification and public publication remain pending.
