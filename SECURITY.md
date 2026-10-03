# Security and privacy

FIC is local-first experimental software, not an emergency dispatch service or a
guarantee of safety. Saved addresses never establish current physical presence.
No microphone, camera, SMS, background-location or write-Contacts feature is added.

Android stores scans and NETWORK records in app-private Room tables with explicit
migrations and fail-closed unsupported schemas. Cloud/device-transfer backup rules
exclude app data and allowBackup is false. Windows stores SQLite in a per-user
application-data profile. Neither database is encrypted. OS administrators, a
compromised account/device, forensic recovery and independently copied exports are
outside this storage isolation. Deletion is logical, not guaranteed secure erasure.

API-key ciphertext uses Android Keystore AES-GCM; an unreadable record stays intact
until deliberate replacement/removal. Confidential server client secrets should not
be entered in a mobile app. Optional address-geocoder keys are memory-only. No tokens,
keys, raw provider URLs or personal records belong in Git, CI logs or issue reports.

Import uses selected files with bounded bytes/records/properties/depth. Remote vCard
references remain inert; no content executes. VCF/CSV format loss is disclosed;
canonical JSON has strict version/schema/provenance validation. Future unsupported
schema versions fail explicitly. Notes/provider text are data, never tool instructions.
Preview and cancellation do not mutate contacts. Same source identities are idempotent;
names/addresses alone do not merge records. User-selected exports may contain private
notes, numbers and addresses; review scope before saving or sharing.

External geocoding is OFF by default and transmits only the selected address after
the provider/address preview and explicit send. It still discloses an address.
No automatic AI sharing, location sharing, cloud synchronization or contact telemetry.
Ordinary field reports and AI packets omit NETWORK contacts and counts. Free-form
notes that users deliberately write/export may themselves contain sensitive data.
Dial/email/map intents require a user action and never automatically contact anyone.

Use private repository security reporting or contact the repository owner through an
already established authorized channel. Include a synthetic reproduction, software
version and sanitized error state; do not attach a real address book or database.
No automated message or external disclosure is sent by this repository's tooling.

Release gates review reachable history and allowlisted artifacts; .gitignore alone
does not prove history is safe. Debug-signed Android and unsigned Windows artifacts
are labeled. Never weaken TLS/OS security or erase history to bypass a failed update.
