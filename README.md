# 🚀 FIELD INTELLIGENCE CONSOLE
## BETA

**[Download the beta9 Cognitive Earth Engine Android test APK](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/download/v4.0.0-beta.9.dev1/FieldIntelligenceConsole-BETA9-ANDROID-TEST.apk)**. Sign in to the private repository. Use **SCAN → OPEN MATRIX → WORLD INTELLIGENCE** to open the globe and geographical queries. Android 10+ is required. The APK can update the published beta8 development installation. Keep beta7 on a separate phone. Physical validation is pending. [Windows EXE, Python wheel and checksums](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.9.dev1) · [Test evidence](docs/mission/MISSION_004.md)

**[Download the beta8 development APK for Android testing](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/download/v4.0.0-beta.8.dev1/FieldIntelligenceConsole-BETA8-ANDROID-TEST.apk)**. Sign in to GitHub and use an Android 10 or newer test phone where `com.fieldconsole.debug` is absent. The published beta7 APK has the same package ID and a different signer. Keep it on its current phone. Physical-device validation is pending. [Installation, checksums and companion downloads](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.8.dev1)

**[Download Field Intelligence Console 4.0.0-beta.7](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases/tag/v4.0.0-beta.7)** from the private repository. The prerelease includes the Windows x64 EXE, Android testing APK and Python wheel. Verify the download against `SHA256SUMS.txt`. The [Mission 002 recovery record](docs/mission/MISSION_002.md) links the test reports and release verification.

**ONE TAP. INSTANT AWARENESS.**

FIC organizes a place and moment into awareness, intelligence, persistent field
memory and a local Known Network. It informs the human; the human decides.
The recovered Kotlin application is preserved alongside a Windows workstation and
an independently installable Python core. This is experimental software.

## Get and run FIC

Artifacts are delivered through the original
[repository releases](https://github.com/chatgptopenaiagi/Field-Intelligence-Console/releases).
The published prerelease includes installation notes, a manifest and SHA-256 checksums.

- **Android:** install `FieldIntelligenceConsole-BETA-Android.apk`, a debug/testing APK, on Android 10/API29
  or newer. It uses `com.fieldconsole.debug`; the release application identity remains
  `com.fieldconsole`. Check signing compatibility before upgrading an existing app.
  Never uninstall a working installation to bypass a certificate mismatch. SCAN
  records coordinates, measurement time and reported accuracy; resolution is not
  guaranteed. Tools opens Known Network, field reports, appearance and settings.
- **Windows x64:** download `FieldIntelligenceConsole-BETA-Windows-x64.exe` and run it. Python/Java/Gradle
  are not required by the packaged user application. It is unsigned; verify the
  published SHA-256 and provenance without disabling Windows security. Use reports,
  Known Network and manually/report-selected places in one native window. Desktop
  location is not presented as GPS. Data persists in the current user's local app data.
- **Python/developers:** install the wheel in a virtual environment with
  `python -m pip install field_intelligence_console-4.0.0b7-py3-none-any.whl`, then
  `fic --help` or `fic diagnostics`. Diagnostics checks packaged schemas offline
  without accessing a user database. The core has no GUI/server/
  credential requirement for local workflows. Python 3.14 is locally tested;
  the package declares 3.11+, with other interpreter coverage documented by CI.

## Use Known Network

Select VCF, CSV or FIC JSON, parse, review valid/invalid/duplicate counts, select
records and confirm. Cancel leaves the store unchanged. Repeat source records do
not silently create duplicates or overwrite unrelated records. Create/edit/search/
delete local contacts and organizations; open details intentionally to reveal
numbers and notes. Android VCF import uses the document picker without broad storage
or Contacts permission. Optional phone-contact import requests read-only permission
only at that action and never modifies the system address book.

Select a place and scope: building, street, radius, neighborhood, city, region or
country. Text matches need qualifying address fields; meter-level radius requires
a suitable saved point. Unknown scopes remain unavailable, not reassuring zeroes.
**Saved geographic association is not current physical presence.** A home/work
label is source metadata, not proof of current residence, ownership or whereabouts.

Exports require selection and preview. Canonical FIC JSON carries ancestry and
geographic provenance. VCF3/4 text and documented CSV are narrower formats with
visible loss warnings. CSV export neutralizes spreadsheet formulas while JSON
preserves original values, including `+` phone numbers. See
[shared contracts](shared/CONTRACT.md) for bounds, CSV columns and compatibility.
No automatic PHOTO/URL/KEY fetching or original-file retention occurs.

## Offline use and providers

Local history, contact management, manual associations and offline emergency numbers
remain available without provider credentials. External lookups can fail or become
stale; failure is distinct from a successful empty lookup. Emergency actions open a
dialer for human confirmation. FIC never automatically calls anyone.

Optional forward geocoding is off by default. It previews the address/provider and
requires an explicit send action and key; select a returned candidate deliberately.
An address is private data even without a name. Provider terms/trial limits apply.
AI ANALYSIS retains the external prompt action. In-app AI/weather/CAD adapters and
legacy hooks are not all connected; saving a key does not make them operational.
NETWORK details and counts are excluded from ordinary reports and AI packets.

## Privacy, removal and development

No automatic contact cloud sync or contact telemetry. Android backup is disabled;
local databases are not encrypted. Deliberate exports, OS backups and copied files
have separate retention. Deleting a record is not forensic secure erasure. Android
uninstall removes app-private data; export anything needed first. On Windows, close
the app before intentionally deleting its documented profile; remove the portable
EXE separately. Python can be uninstalled with `pip uninstall field-intelligence-console`;
explicitly created data/output files are retained until you remove them.

Read [security](SECURITY.md), [building](BUILDING.md), [release process](RELEASING.md),
[architecture](docs/ARCHITECTURE.md) and [mission evidence](docs/mission/STATUS.md).
Software retains the [MIT license](LICENSE). This grants no rights over imported
contact data; owners remain responsible for authorized use and sharing.
