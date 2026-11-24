# Mission 002 recovery record

Mission 002 resumed on 2026-11-24 in the existing private repository. Delivery is a prerelease accessible to repository readers once verification passes.

## Recovered checkpoint

The clean local and remote `v4-development` branches pointed to handoff `d76f1711b885bda8b420fba916c1cbece57482d5`. Tags beta1 through beta6 and the recovered baseline were present. GitHub listed only the beta1 draft, release 402457718 with eleven assets.

Beta5 source `07f2f9f25a47f6771e149b38970914911471d4fa` passed local exact-APK runtime and Android document workflows. Its combined hosted run failed before installation. Beta6 source `6335a0e4c64a1d50af2436454a579818b0ea724b` superseded beta5. Its [Android](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159219), [Windows](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159009) and [Python](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742159088) runs passed. Both APKs installed on the first attempt in [combined run 44742158905](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/actions/runs/44742158905). Software-mode instrumentation exceeded 600 seconds, returned exit 124 and produced no passing count. Finalization was skipped. The beta6 finalizer also required Android code 8 while the APK has code 9.

The eight beta6 assets were recovered from Actions artifact 14632013408. Every size and SHA-256 matched the build receipt in `.local/evidence/mission002/beta6-build/`. The hosted failure receipt is in `.local/evidence/mission002/remote-diagnostics/`.

## Beta7 candidate

The version is 4.0.0-beta.7, Android code 10, Python 4.0.0b7 and Windows 4.0.0.7. The Kotlin/Compose/Room Android app, native Tk Windows workstation, Python CLI/SQLite core and shared v1 contracts retain the architecture described in [ARCHITECTURE.md](../ARCHITECTURE.md). Release engineering changes are in [CHANGELOG.md](../../CHANGELOG.md) and the private-repository procedure in [RELEASING.md](../../RELEASING.md).

Recover the source with `git rev-parse 'v4.0.0-beta.7^{commit}'` once tagged. Query workflows by that SHA. Use the combined build's own APK, EXE and wheel for exact runtime, Android document workflows, hosted exchange, final inventory and release asset download verification. The mission-owned local API 34 AVD is FIC_MISSION002_API34 on emulator-5584. Use only synthetic fixtures.
