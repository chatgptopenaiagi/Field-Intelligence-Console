# ADR 003: Experimental version, Android tooling and privacy boundaries

Accepted 2026-10-03. Preserve Android identities `com.fieldconsole` and
`com.fieldconsole.debug`. Use application release `4.0.0-beta.1`, Android code 4,
Python `4.0.0b1`, Windows numeric file version `4.0.0.1`. Data contracts remain
version 1; Room becomes version 3 for the additive NETWORK/report tables.

AGP 8.1.4 could not run two dependency lint registries. Use AGP 8.5.2 with the
existing Gradle 8.7/JDK17/compile34 toolchain and Build Tools34.0.0. Official
compatibility: https://developer.android.com/build/releases/agp-8-5-0-release-notes.
The integrated gate executed 92 tests per variant, both assemblies and lint.
Skipped custom registries are resolved. Lint retains OldTargetApi and three
AGP-update suggestions; no diagnostic was suppressed. Broad dependency upgrades
and a target-SDK behavior migration are separate work.

NETWORK stays in application-private local storage. No automatic original-file
retention, contact synchronization, geocoding or AI/report inclusion. CSV/VCF are
deliberately lossy; canonical JSON preserves entity ancestry/unknown properties.
Android system Contacts access is read-only and requested at the explicit import
action; document imports never require it. Phone/email/map actions require a tap.

Optional OpenCage forward lookup previews the exact address and provider. It needs
an explicit send action and an in-memory key, returns candidates for deliberate
selection, and does not establish current presence. Provider trial limits are not
an unlimited free service. Both clients bound calls/retries/cache, honor rate limits,
disable redirects and never log URLs or keys. No real credential/request was used
in validation. Review https://opencagedata.com/api, /terms and /credits before use.

Android backups are disabled/excluded; databases are not encrypted. Existing API
key storage uses Android Keystore AES-GCM; unreadable credentials remain for explicit
re-entry/removal. Desktop uses the OS user's private application-data directory,
not a claim of at-rest database encryption. Exports leave this protection boundary.
