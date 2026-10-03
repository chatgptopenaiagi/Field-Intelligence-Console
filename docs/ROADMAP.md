# Roadmap

**ONE TAP. INSTANT AWARENESS.** The mission delivers an experimental Android, Windows and Python product while preserving recovered history. Earlier speculative architecture phases are not delivery promises.

## Implemented

- Android safety: identifying Overpass requests, failure/empty distinction, stale cache retention, bounded geocoder completion, non-destructive migrations, dialer-only emergency actions, coordinate/radius checks and backup exclusions.
- Shared contracts and standalone Python core/CLI for bounded exchange, validation, provenance, persistence and qualified geographic queries (`732b01b`).
- Android network persistence, shared validation and additive Room v3 migration (`5413fff`).
- Android Known Network import/review/manage/query/export, intentional Contacts import and canonical report exchange (`b17abbf`).
- Packaged Windows workstation reusing the installed core, with report/contact workflows and native GUI evidence (`7c811d3`).

Saved associations never mean people present now. Optional external geocoding requires explicit address/provider preview and sending. Credentials are unnecessary for local workflows.

## Remaining release gates

1. Freeze a clean candidate and rebuild all artifacts from that commit.
2. Complete exact Android -> installed wheel -> packaged Windows -> return exchange evidence, preserving privacy, migration and runtime limitations.
3. Execute local gates and actual GitHub workflows where capacity permits. Workflow files alone are not execution evidence.
4. Verify artifact metadata, signing, dependencies, licenses and SBOM; audit all uploaded history/source/artifacts for secrets and personal data.
5. Publish only after required gates pass: reviewed source, protected baseline and three verified artifacts in the intended private experimental prerelease. Download assets and compare bytes/hashes before completion.

Preliminary gates: Android 92 tests per variant plus six emulator tests; Python 45 tests plus five clean-wheel checks; Windows five focused, 28 packaged widget and seven native checks. These include incremental/dirty-worktree runs. Physical-device validation remains separate from x86_64 emulator coverage. See [status](mission/STATUS.md), [test matrix](mission/TEST_MATRIX.md) and [blockers](mission/BLOCKERS.md).

## Limits and future horizon

AI engine prototypes and weather components are not established main-workflow integrations. Production signing, wider device/interpreter coverage and broader vCard support need separate evidence. Databases are not encrypted; reconstructed v1 tests cannot establish authentic historical compatibility.

RELATIONSHIPS remains a future graph concept. Cloud/team synchronization, live enterprise directories, live tracking, public distribution, PyPI publication, plugin systems and bundled local AI models are outside this release.

