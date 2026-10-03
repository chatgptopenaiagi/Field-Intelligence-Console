# Android NETWORK core and persistence

The Kotlin implementation remains inside the existing Android application.
`com.fieldconsole.network` contains bounded offline parsers, validation, provenance,
exports, saved-association queries and the Room repository. UI and optional provider
adapters are separate coordinator-owned work. These core APIs perform no network IO.

## Contracts and bounds

- Uses shared/CONTRACT.md and shared synthetic fixtures directly in JVM resources and
  instrumentation assets. Canonical entity JSON retains unknown fields, original
  phone/email/address text and ancestry; schema versions are independent of app version.
- Dataset envelope extras produce an explicit preview warning because entity storage
  does not retain arbitrary envelope metadata. Keep the original file if those matter.
- Strict finite JSON, 5 MiB input/export, depth 32, 5,000 records; invalid encoding,
  duplicate object keys, unsupported schema versions and current-presence semantics
  are rejected. Invalid individual records have safe index-based preview diagnostics.
- Bounded vCard 3.0/4.0 UTF-8 text subset: multiple cards/phones/addresses, folded
  UTF-8, text escapes, grouped properties, quoted parameters, typed labels, Unicode,
  structured N/ADR, organization, source REV, inert unknown properties. No remote
  PHOTO/URL/KEY fetch, embedded data, legacy binary/quoted-printable or v2.1 support.
  VCF export deliberately warns of FIC metadata loss; it is not a universal parser
  or a lossless vCard editor. References: [RFC 6350](https://www.rfc-editor.org/rfc/rfc6350)
  and [RFC 2426](https://www.rfc-editor.org/rfc/rfc2426).
- CSV has quoted Unicode fields and explicit canonical-column mapping. CSV exports
  quote fields and prefix spreadsheet formula-leading values including + numbers;
  original values stay intact in FIC JSON. CSV retains only primary value/address,
  with a visible loss warning.
- Import is explicit selected IDs, atomic, idempotent by local ID and source-record
  ancestry. Names/addresses never trigger an automatic merge. Preview/cancel writes
  nothing; repeat imports preserve user corrections. Deletion removes app-local
  records only, never the phone address book or original user-selected files.

## Geography semantics

Queries distinguish empty store, location unavailable, insufficient address data,
matches, no matches and failure. Street/neighborhood matches require locality and
country; these are structured-text associations, not boundary proofs. Building
scope additionally requires a resolved building-precision association. Radius uses
resolved building/street points, excludes centroids/ambiguous data, and measures
geodesic distance to the saved point. Entities are counted once within a scope.
Tests cover zero/negative coordinates, poles, antimeridian and inclusive boundaries.
Saved association is never represented as current physical presence.

## Room schema v3

Additive explicit migration 2 -> 3 creates `network_entities` and `exchange_reports`.
Existing field/history, weather and emergency tables are untouched. Version 1 -> 2
SQL remains unchanged and composes into 1 -> 2 -> 3. No destructive fallback.
The authentic exported v2 schema is used to construct isolated migration fixtures;
all FieldEntry metadata, weather/emergency rows and next inserted ID survive Room
generated validation. Failed transactions and malformed migrations preserve data.
The older v1 fixture remains reconstructed, not an authentic historical DB export.

## Validation evidence so far

Focused JVM tests executed using JDK17/Gradle8.7/AGP8.1.4: 28 passing
(15 codec, 5 geography, 4 network persistence, 4 existing database safety).
Three instrumentation migration tests compiled in the Android test APK. No production
DB or real contacts were accessed.
Final application-wide gates are coordinator-owned and supersede this intermediate
focused evidence when the full UI/provider/toolchain changes are integrated.

Ignored logs: `.local/evidence/m3/core-focused*.log` (first compile caught and fixed
the SQL-placeholder literal escape). Synthetic interoperability exports are generated
by production Kotlin codec tests under `app/build/interoperability/`; these are JVM
codec evidence, not proof of a device document-picker interaction.

### Cross-platform validation follow-up

`core-schema-focused-3.log`: 36 focused JVM tests pass, zero failures; debug APK and
test APK assembled. This includes a bounded validator executing the actual bundled
shared JSON schemas, plus all 35 shared malformed mutation vectors. Numeric strings,
missing required fields, invalid null types, unknown versions, unqualified dates,
invalid Unicode, contradictory association metadata and nested NETWORK report data
are rejected. The two schema resources are verified present in the APK.

Manual saved points with explicit verification support offline radius/building scope
without pretending a geocoder resolved them. They require manual-location/user-source,
valid verification time and unresolved status; failed/ambiguous or unsupported points
cannot become trusted evidence. Preview duplicate counts and atomic import use the
same ID/ancestry decision helper. Phone normalization keeps service/extension markers
without inferring a country; original phone text is unchanged.

`migration-emulator.log`: **3 instrumentation migration tests passed** on the mission's
isolated `emulator-5580`, after non-destructive APK replacement. Reconstructed v1 -> v3,
authentic exported v2 -> v3 and unsupported-version fail-closed behavior executed on
Android. Tests use isolated random database filenames. This is emulator evidence,
not physical-device validation. No phone contacts, emergency calls or other device
were accessed. Gradle/emulator test ownership returned to the coordinator.
