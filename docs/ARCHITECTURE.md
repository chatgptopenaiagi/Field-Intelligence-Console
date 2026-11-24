# Architecture

**ONE TAP. INSTANT AWARENESS.** FIC combines awareness, intelligence, persistent field memory and a local Known Network. Saved geographic association is never current physical presence.

## Applications and contracts

| Component | Responsibility |
| --- | --- |
| Android at repository root | Kotlin/Compose field workflow, Hilt, location capture, Room history, emergency lookup, the original eight Action Matrix surfaces, Known Network and deliberate report exchange. |
| `python/` | Installable core and CLI: validation, bounded VCF/CSV/FIC JSON exchange, SQLite libraries, normalization, provenance and geographic queries. Local operations require no GUI, server or credentials. |
| `windows/` | Native Tk/ttk workstation using the installed wheel. PyInstaller bundles the runtime and resources into a portable Windows x64 GUI for reports, history and contact workflows. |
| `shared/` | Versioned schemas, contract rules and synthetic fixtures consumed by Kotlin and Python. Separate executable implementations require real interoperability tests. |

Application versions map to Android/Windows `4.0.0-beta.7`, Android code 10, Python `4.0.0b7` and Windows numeric version `4.0.0.7`. Exchange schemas are version 1; Room is version 3. These domains are independent.

## Persistence and state

Compose uses ViewModels and local UI state; it is not uniformly stateless. Room retains field entries and provider caches alongside canonical network JSON and exchange reports. DAO flows drive lists. Import selection is atomic and shares ID/ancestry duplicate rules with preview; no fuzzy name/address merging occurs.

Explicit v1 -> v2 -> v3 migrations replace destructive fallback. Version 3 adds network/report tables without changing observation tables. The v1 fixture is reconstructed, not an authentic historical export; the exported v2 schema is available. Isolated JVM and emulator migration tests do not prove every historical installation.

Android document pickers require no broad storage permission. Optional phone-contact import requests read-only permission after its explicit action; denial leaves file import usable. Calls open a dialer only after human action. Desktop locations are manual or report-derived, never claimed as GPS measurements.

## Provenance and geography

Reports retain measurement metadata, observations, provider states, freshness and address provenance. Missing coordinates remain absent. Ordinary reports and AI packets exclude NETWORK details and summaries by default.

Contacts preserve original values, multiple addresses, ancestry and distinct location associations. Street/neighborhood text matching requires locality and country. Radius results measure distance to eligible saved building/street points. Explicitly verified manual points work offline without fabricated geocoder success. Failed, ambiguous and coarse centroid placements do not become precise proximity evidence. Counts deduplicate entities.

VCF supports a bounded UTF-8 vCard 3.0/4.0 text subset with diagnostics for unsupported or malformed input. References are inert. CSV supports mapping and spreadsheet-safe export; canonical JSON preserves original values. Entity extensions round-trip; unsupported dataset-envelope fields produce a warning and are not stored as entity fields. VCF/CSV report meaningful loss.

## Providers and limitations

Overpass has a central identifying HTTP client, bounded requests and explicit failures distinct from successful empty responses. Failed refresh preserves stale cache provenance. Node coordinates and way/relation centers are radius-filtered; centers are approximate, not entrances or verified availability. Reverse geocoding bounds caller completion and worker resources; timeout cannot guarantee the platform service stops.

Optional forward geocoding requires selected address/provider preview and explicit sending and acceptance. Imports, startup and SCAN do not send contacts automatically. Missing credentials leave local workflows usable; development uses mocks and synthetic inputs.

AI ANALYSIS retains its external ChatGPT workflow. Prototype CoreEngine/AI ViewModel components are not a verified operational replacement: Google Earth/SolidWorks placeholders and Autodesk account access do not establish place analysis. Weather components remain disconnected from the main operational workflow. Legacy export hooks are distinct from working canonical report exchange. Desktop displays imported provider states without claiming live AI/weather integration.

## Privacy, packaging and evidence

Android backup/transfer is disabled and excluded. API keys use Keystore-backed protection; local databases and deliberate exports are **not encrypted**. There is no automatic contact synchronization or AI sharing. Deletion does not erase external exports or guarantee forensic erasure.

Android retains `com.fieldconsole` and `com.fieldconsole.debug`: JDK 17, Gradle 8.7, AGP 8.5.2, SDK 34 and min SDK 29. Experimental artifacts are debug-signed; Windows is unsigned. The pure-Python wheel bundles schemas. Local interpreter evidence is CPython 3.14.7; declared Python >=3.11 does not establish testing of every version.

Development gates executed 92 Android JVM tests per variant, six emulator tests, 45 Python tests plus five clean-wheel checks, and Windows five focused tests, 28 packaged widget checks and seven native checks. These incremental/dirty-worktree gates are not frozen-candidate, GitHub CI or publication evidence. See [mission status](mission/STATUS.md) and [test matrix](mission/TEST_MATRIX.md).

