# Changelog

## 4.0.0-beta.9.dev1 · Earth Engine testing

WORLD INTELLIGENCE opens a keyless globe from the existing Matrix. It adds sourced geographical queries, dated observations and predictions, local map rendering, offline archives and Earth keypoint exchange on Android, Windows and Python. The development build includes a fix for browsers that reuse a panel for a new location and language. Physical handset validation is pending. [Mission 004 record](docs/mission/MISSION_004.md)

## In development · 4.0.0-beta.8.dev1

Android adds a ninth Google Earth action, a reviewed ChatGPT text share, manual coordinate entry and foreground capture with measurement details. The emergency panel uses verified offline numbers and a manual country override. Regional controls and the Matrix support English, German and Romanian. Windows and Python include the same offline geographic data. [Engineering record](docs/mission/MISSION_003.md)

## 4.0.0-beta.7

Beta6 installed both APKs on the hosted runner, then exceeded its ten-minute instrumentation deadline in software mode. Beta7 uses hardware acceleration when available to the runner and allows a bounded thirty-minute instrumentation run in software mode. Release validation checks the Android version code against the candidate number. Android code 10, Python 4.0.0b7 and Windows 4.0.0.7 identify this private-repository candidate.

## 4.0.0-beta.6 — public beta candidate

Repair hosted emulator startup for emulator 37 with the documented process-local
TCG opt-in when KVM is unavailable. Set the newly created test AVD's data partition
to 6 GiB. Beta5's exact APK passed six local instrumentation tests and real
synthetic document workflows; its hosted emulator exited before app installation.
Preserve that tag. This candidate uses Android code9, Python4.0.0b6 and
Windows4.0.0.6. All final artifact and public delivery gates must run again.

## 4.0.0-beta.5 — public beta candidate

Make BETA and the application version visible on Android's startup screen, retain
the BETA label in its top bar, and show the desktop BETA header/version. Beta4's
Windows gate passed completely; its tag remains preserved. Android code8,
Python4.0.0b5 and Windows4.0.0.5 identify this candidate. Full runtime verification
and public delivery remain required before publication.

## 4.0.0-beta.4 — public beta candidate

Beta3's Windows GUI/native workflows passed, but its final PE check retained a
beta2 numeric assertion. Derive the fixed PE fields from the already-verified
version string and check Android/Python/Windows versions before packaging.
Preserve beta3's tag and failed evidence. Android code7, Python4.0.0b4 and Windows
4.0.0.4 identify the new candidate; all final gates must run again.

## 4.0.0-beta.3 — public beta candidate

- Preserve both earlier beta tags and the recovered v3 baseline; Android code 6,
  Python 4.0.0b3 and Windows file version 4.0.0.3 identify this candidate.
- Wait for a usable Android package service, use non-streaming installation,
  retry only bounded transient failures, and retain sanitized failure diagnostics.
  Beta2's combined release failed with a package-service broken pipe before tests.
- Add `fic diagnostics` to verify packaged schemas and versions offline without
  opening user data. Exercise it after clean wheel installation.
- Prepare the requested Windows/APK filenames, public beta release metadata,
  installation instructions and trusted-tag signing restrictions.
- Public release remains pending all exact-artifact gates and the publication audit.

## 4.0.0-beta.2 — candidate in validation

The beta.1 tag and unpublished draft are preserved. Its three target CI workflows
passed, but the combined release workflow failed during dependency inventory.
This candidate adds bounded inventory diagnostics and uses a new coordinated
version: Android code 5, Python 4.0.0b2 and Windows 4.0.0.2. Release validation
and publication are pending; data contracts remain v1 and Room remains v3.

## 4.0.0-beta.1 — experimental candidate

This entry describes implementation, not a published release.

- Shared version-1 contracts, fixtures and reusable Python core/CLI for local import/export, persistence and geographic search (`732b01b`).
- Android Known Network storage, validation and additive Room 2 -> 3 migration (`5413fff`); reviewed selected import, duplicate handling, editing, queries, deliberate export, read-only Contacts import and canonical report exchange (`b17abbf`).
- Portable Windows x64 workstation using the installed core, with packaged GUI/restart/native interaction evidence (`7c811d3`).
- Repaired Overpass identification/error mapping/cancellation, stale cache handling, non-destructive migration, bounded geocoder completion and dialer behavior. Emergency coordinate/radius handling, unsupported safety claims and backup exposure repaired (`eab136c`).
- Preserved package identities, eight original Action Matrix surfaces, observation/history data and recovered baseline. NETWORK details remain excluded from ordinary reports and AI packets by default.

Preliminary development gates: Android 92 JVM tests per variant and six emulator tests; Python 45 tests and five clean-wheel checks; Windows five focused tests, 28 packaged widget checks and seven native checks. Incremental/dirty-worktree evidence is not final candidate, GitHub CI or publication proof.

Android uses debug signing; Windows is unsigned. Physical-device validation is not claimed. VCF supports a bounded UTF-8 3.0/4.0 subset; CSV/VCF export reports field loss. Databases are not encrypted. Historical v1 migration evidence is reconstructed. AI/weather prototypes remain distinct from working flows. See [mission status](docs/mission/STATUS.md).

## 3.0.0 — recovered baseline

Recovered Android version `3.0.0`, code 3, is preserved by `v3-recovered-baseline` at `e45f9b863892b1796774a8132a213ea99c4518d2`. This records recovery provenance, not a newly published release.

