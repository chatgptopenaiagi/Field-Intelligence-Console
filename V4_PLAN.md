# FIELD INTELLIGENCE CONSOLE: verified v3 baseline and v4 plan

Audit date: 2026-10-03. Scope: first milestone only. No application implementation changes.

## Product constraints

Preserve **ONE TAP. INSTANT AWARENESS.** and all eight surfaces: INFO SCAN, HISTORY,
NAVIGATION, AI ANALYSIS, RISK / SAFETY, LOGISTICS, FINANCIAL, EMERGENCY EXIT.
Keep working intent fallbacks and local observations. Do not rewrite the app, change
package identity, introduce destructive migrations, or commit credentials.
The work below is a proposal for subsequent milestones, not authorization to implement them.

## Baseline provenance

- Canonical root: `F:\FieldIntelligenceConsole`.
- Branch on arrival: `v4-development`, unborn (no commits).
- Contrary to the handoff, `git show-ref`, `git tag --list`, and `git log --all`
  showed no existing refs/history. All 74 recovered files were staged.
- Preserved that exact staged tree in root commit
  `e45f9b863892b1796774a8132a213ea99c4518d2` (`Preserve untouched recovered v3 baseline`) and created
  `v3-recovered-baseline` there before editing tooling.
- Git had no author identity. Commits use an explicit Codex identity per command;
  no global Git identity or user attribution was changed.
- Reviewed every recovered Kotlin file (48 production, one test), all Gradle files,
  manifest, nine resources, lint/ProGuard rules, README, three docs, license and ignore rules.
  No AGENTS.md or instrumented test sources were present. Ignored `.kotlin` was empty.

## Identity and environment

| Setting | Verified value |
|---|---|
| Namespace / release application ID | `com.fieldconsole` |
| Debug application ID | `com.fieldconsole.debug` |
| versionName / versionCode | `3.0.0` / `3` |
| minSdk / targetSdk / compileSdk | `29` / `34` / `34` |
| Gradle / Android Gradle Plugin | `8.7` / `8.1.4` |
| Kotlin / Compose compiler / KSP | `1.9.24` / `1.5.14` / `1.9.24-1.0.20` |
| Hilt / Room | `2.51.1` / `2.6.1` |
| Java source and Kotlin bytecode target | `17` |
| Default installed JDK on arrival | Temurin `21.0.12.1+1` |
| Baseline-compatible JDK installed during audit | Temurin `17.0.20.1+1`, `C:\Users\Administrator\.jdks\jdk-17.0.20.1+1` |
| Other installed Java directories | JDK `26.0.2.1`, JRE `1.8.0_503` |
| Android SDK | `C:\Users\Administrator\AppData\Local\Android\sdk` |
| SDK packages on arrival | Platforms 36, 37.0; Build Tools 36.0.0 |
| Devices on arrival | `adb devices`: none |

## Tooling repair and execution evidence

The recovered `gradlew` was a two-line shell script invoking `/opt/gradle-8.7/bin/gradle`.
There was no wrapper JAR or Windows launcher. `gradle.properties` forced
`org.gradle.java.home=/tmp/jdk-17.0.9+9`. An initial attempt through the installed
Gradle 8.14.3 failed on that invalid Java home before application compilation.

Restored official Gradle 8.7 launchers/JAR, retained the pinned Gradle version,
removed only the machine-specific Java-home property, and added the official
distribution SHA-256. Added wrapper-specific line-ending rules and the Unix executable
bit so Windows checkout settings do not break the portable launcher.
Wrapper JAR SHA-256 verified against Gradle's published value:
`cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`.
No dependency upgrades, SDK-level changes, lint suppressions, signing changes,
Kotlin changes, manifest changes, resource changes, or database changes were made.

Installed missing Android platform 34 and Build Tools 33.0.1 without changing compileSdk.
The machine's 2026 sdkmanager compatibility shim split semicolon package names and failed;
direct `android.exe sdk install` unpacked the packages but exited with Windows status
`-1073740791`. Package files and actual Gradle use were checked independently, so this
installer exit is not reported as a successful command. Gradle auto-install overlapped
Build Tools installation and created an extra `33.0.1-2` directory. After the debug
build finished, that duplicate was moved to ignored `build/audit/duplicate-build-tools-33.0.1`
after checking the exact SDK/workspace paths; the canonical `33.0.1` package remains.

The first wrapper-based debug build under default JDK 21 failed at
`:app:compileDebugJavaWithJavac`: AGP's `JdkImageTransform` could not run `jlink` on
Android 34's core-for-system-modules.jar. Kotlin compilation had completed.
This demonstrated the need for a JDK repair: install Temurin JDK 17 under the user's
`.jdks` directory, verify the archive against Adoptium's published SHA-256, and select
it through session-local `JAVA_HOME`. Do not upgrade AGP to work around this baseline issue.
Verified JDK archive SHA-256:
`e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0`.

`assembleDebug` under JDK 17 completed successfully (40 tasks, 14 executed,
26 up-to-date from the first attempt). `aapt2 dump badging` verified the actual APK:
`com.fieldconsole.debug`, version 3.0.0 (3), min 29, target/compile 34.

`test` executed both existing JUnit tests in both variants: 2 debug + 2 release
executions, zero failures/errors/skips (two unique tests, not four distinct tests).
JUnit XML evidence: `app/build/test-results/testDebugUnitTest/` and
`app/build/test-results/testReleaseUnitTest/`. Their limited scope is prompt content;
they do not validate GPS, network failures, persistence, credentials or UI behavior.
There are no existing instrumented tests, and no device tests were run.

The packaged release APK also verifies `com.fieldconsole`, 3.0.0 (3), min 29,
target/compile 34. The merged release manifest excludes the debug preview/test activities;
the exported ProfileInstallReceiver is protected by the DUMP permission.

| APK | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | `c1cdae4d203e7571a5a060de99cf7c5df719047be3226d94bcb9d16d929fd68c` |
| `app/build/outputs/apk/release/app-release.apk` | `64ee93908873999ec914e80646da95acb3db77297c8dfac09d375e7245b0ba3a` |

Final execution status:

| Command (JDK 17) | Result | Evidence |
|---|---|---|
| `assembleDebug --console=plain` | PASS, 1m 6s | `build/audit/assemble-debug-jdk17.log` |
| `assembleRelease test lint --continue --console=plain` | PASS, 2m 2s; 90 tasks, 70 executed | `build/audit/release-test-lint.log` |
| `lintRelease --console=plain` | PASS, 11s; 25 tasks, 4 executed | `build/audit/lint-release.log` |

Debug and release lint each report **0 errors, 4 warnings**: `OldTargetApi`,
`RedundantLabel`, and two `ObsoleteLintCustomCheck` warnings. The Compose runtime
and Lifecycle LiveData lint registries reference incompatible Kotlin analysis APIs
and were skipped. Thus lint success has reduced coverage; it is not a clean bill of
health. Reconcile lint/library compatibility in a later tested tooling change, without
suppressing the warnings. Existing `GradleDependency` and `UnusedResources` ignores
remain unchanged. Full reports: `app/build/reports/lint-results-debug.{html,xml}` and
`app/build/reports/lint-results-release.{html,xml}`.

Other observed nonfatal build warnings: newer SDK XML metadata than AGP understands,
Gradle 9-incompatible deprecated features, and an unstripped DataStore native library.
Do not infer production readiness from release assembly: the recovered release signing
configuration still uses the debug key. No APK was installed or distributed.

## Overpass 406 investigation

**Reproduced cause: the stock HTTP user agent is rejected by the public server.**

Source path: `EmergencyScreen` -> `EmergencyViewModel.load` ->
`EmergencyRepository.refreshNearby` -> `OverpassApi.query` ->
`POST https://overpass-api.de/api/interpreter`.
`AppModule.provideOverpassRetrofit` supplies no custom OkHttp client or identifying
headers; OkHttp 4.12.0 supplies its stock user agent. The query is already a
form-encoded `data` field and explicitly requests JSON through `[out:json]`.

Controlled HTTP probe on 2026-10-03 used the exact emergency query extracted from
source, radius 5000, public sample coordinates 48.2082,16.3738 (not device location),
same URL, POST body, and no explicit Accept header:

| User-Agent (only changed variable) | Result |
|---|---|
| `okhttp/4.12.0` | HTTP 406, Apache HTML rejection |
| `FieldIntelligenceConsole/3.0.0 (baseline audit)` | HTTP 200, Overpass JSON; 776 elements, including four ways |

This isolates request identification from query syntax, Accept negotiation, credentials,
and JSON parsing for this reproduction. It is a desktop HTTP reproduction, not a
capture of the historical device request. The original screenshot/session traffic
was not provided, so other historical server/network conditions cannot be excluded.

The [server operator's 2026-04-19 announcement](https://community.openstreetmap.org/t/overpass-api-performance-issues/140598/74)
requires identifying custom user agents and limits parallelism/request volume.
Use truthful application identification; do not fake browser headers, rotate identities,
or distribute load to evade restrictions. Raw local probe evidence is under ignored
`build/audit/overpass-*`; no coordinates from a user's device were collected.

Proposed correction: a dedicated injected Overpass client with stable application/version
identification, bounded timeouts, single-flight requests, cache freshness, and useful
HTTP error handling. Add request-contract and 406/429/5xx/offline tests. Keep form encoding.
Do not treat missing Accept as the established cause, and do not add blind 406 retries.

## Verified security and persistence findings

| ID / priority | Evidence | Consequence / proposed work |
|---|---|---|
| S1 / high | `AppDatabase.getInstance`: `.fallbackToDestructiveMigration()` | Missing migration paths may erase persisted observations. Remove fallback only with explicit, tested non-destructive paths and retained recovery data. No migration was performed during this audit. |
| S2 / high | `app/build.gradle.kts`: release uses debug signing config | A buildable release APK is not production release readiness. Establish protected release signing outside source before distribution; preserve the actual installed signing identity for upgrades. |
| S3 / medium | Manifest `allowBackup=true`; backup rules exclude only `fic_secure_prefs.xml` | Location/history database is eligible for cloud backup/device transfer. No application-level database encryption is configured. Decide and document the privacy/retention model. The resource claim that location is stored only on-device is too broad. |
| S4 / medium | `ApiKeyStore.getKey/saveKey` and settings/AI ViewModels | AES-256-GCM with Android Keystore and backup exclusion is good. Corrupt ciphertext or invalidated keys can throw without a recoverable UI state; `hasKey` checks presence, not decryptability. Test recovery without logging values. |
| S5 / medium | Manifest CALL_PHONE, `EmergencyScreen` uses only ACTION_DIAL | Direct-call permission is unnecessary for current behavior. Dial/map intents also lack handler failure protection on devices without those apps. |
| S6 / medium | `IntentDispatcher`: prompts copied; browser fallback includes first 500 prompt characters in URL | Precise location/notes can enter clipboard, browser history and external services. Make destination/data disclosure clear and handle sensitive clipboard metadata. This is user-triggered behavior, not evidence of hidden transmission. |
| S7 / medium | Google engine puts key in provider query URL; other engines use authorization headers | Avoid request-URL logging, sanitize errors, restrict provider credentials. No active HTTP logging interceptor or application log calls found in source. Autodesk client-secret storage needs a production authentication decision. |

Positive controls: HTTPS-only configuration, system CA trust only; no permissive trust
manager found; private encrypted credential preferences excluded from both backup formats;
FileProvider is non-exported and restricted to `cache/reports/` with temporary read grants;
launcher is the only explicitly exported component in the application's source manifest.
The merged debug manifest additionally contains exported Compose preview/test activities
and a dependency ProfileInstallReceiver protected by `android.permission.DUMP`, plus an
AndroidX signature-protected dynamic-receiver permission. No background location,
storage, camera, microphone, SMS, or contacts permission is declared in the source manifest.

Credential-pattern scan over the 74 staged files found no high-confidence provider tokens,
private keys, AWS access IDs or GitHub tokens. Manual source review found placeholders and
runtime key references, not embedded credentials. This is not a comprehensive dependency
vulnerability scan or a guarantee about external devices/accounts. No credentials were used
for provider tests, written to reports, or requested from the user.

Database: Room `field_console.db`, schema version 2, tables `field_entries`,
`weather_cache`, `emergency_services`; explicit additive 1->2 migration exists.
`exportSchema=false`; there are no committed schema snapshots or migration tests.
Do not claim migration validation from compilation. Cache delete/insert is not one
transaction, and eviction DAO methods have no callers. No existing device database
was opened, replaced, migrated or deleted.

## Verified functional and architectural findings

| ID / priority | Finding and source evidence |
|---|---|
| F1 / high | ReverseGeocoder API 33+ listener handles success only. `onError` is not overridden and there is no timeout around geocoding. MainViewModel waits for geocoding before showing observations, so callback failure can leave SCAN indefinitely busy. |
| F2 / high | MainScreen accepts coarse OR fine permission; LocationManager requires fine. Approximate-only permission therefore enters a scan that immediately fails. Location request task failures are not observed; LocationResult.Error has no producer. |
| F3 / high | EmergencyRepository filters to nodes and discards all queried hospital ways; it also excludes legitimate latitude-zero nodes. Actual probe contained four hospital ways. Other categories query nodes only; geometry coverage is incomplete. |
| F4 / high | Emergency cache matches scan origins in a +/-0.05-degree box, retaining distances calculated from a different origin. Freshness checks only the first distance-sorted row; stale fallback has no age indicator. Empty successes are not cached. |
| F5 / high | Emergency data sets `isOpen=true` without live evidence (currently not rendered as an open badge); travel time is straight-line distance at assumed 40 km/h, displayed as minutes. UI says no services found even after request failure. Model availability, estimates, empty results and failure separately. |
| F6 / high | EmergencyViewModel.load launches a new indefinite cache collector every time; previous collectors and refresh jobs are not cancelled. Reopening/changing scan can mix results. |
| F7 / high | AI ANALYSIS directly calls IntentDispatcher, never AiAnalysisViewModel. Stored keys do not activate the advertised in-app engines through the current navigation/UI. No provider picker/model setting exists despite comments. Preserve the working external fallback when connecting this integration. |
| F8 / medium | WeatherScreen has no route/caller. FieldReportExporter has DI registration but no UI callers. Overpass logisticsQuery is unused. These are disconnected features, not proof they should be deleted. |
| F9 / medium | Search result clicks call a default empty callback: MainActivity never provides onEntrySelect. MainViewModel.openEntry has no caller. History cards cannot reopen scans. |
| F10 / medium | MainViewModel saves coordinates/note/tag but omits resolved placeName and all extended observation fields. ObservationDialog has no submitting state, allowing duplicate saves. Screen state is not backed by SavedStateHandle. |
| F11 / medium | Google Earth keys route to Google Places and are absent from automatic selection. SolidWorks uses a placeholder tenant URL with no settings field. Autodesk lists account hubs without a spatial association. These are incomplete integrations, not verified live provider features. |
| F12 / medium | Provider clients execute blocking calls on IO, but unsuccessful responses return without `use`/closing bodies. Broad Exception catches swallow coroutine cancellation. Clients are recreated per engine selection; token caching is consequently short-lived. |
| F13 / medium | Weather cache uses locale-dependent keys, stale fallback has no freshness state, and sunrise/sunset parsing ignores the response location timezone. Missing current data can become zero/default values and an EXCELLENT assessment; weather codes 85/86 fall through to EXCELLENT in calm/clear-visibility conditions. AQI fields are never populated. |
| F14 / medium | Geocoder/GPS/user notes are labelled CONFIRMED FACT in prompts without source validation. AI confidence percentages and operational recommendations are requested without a verification layer. Preserve provenance and uncertainty when wiring in-app AI. |
| F15 / medium | Emergency contact catalog contains only seven country mappings and an unsourced international fallback. No source URLs, verification dates, or eligibility metadata are stored. Country-specific numbers/descriptions need authoritative review before safety claims; no calls were placed during the audit. |
| F16 / low | MainScreen routes actions by display text; state collection is not lifecycle-aware; search creates a new Flow on recomposition and lacks debouncing; all history is loaded without pagination. Address incrementally, preserving the eight surfaces. |
| F17 / low | Most UI text is hardcoded despite string resources. Some secondary controls are explicitly 32dp. No UI/accessibility tests verify large fonts, contrast, touch targets, or screen readers. |

Other unused code/dependency candidates verified by reference search: DataStore and
OkHttp logging dependencies, cache eviction methods, several navigation/share helpers,
legacy prompt builder, favorites/tag/priority repository paths. Confirm intended use
before removal; no functionality was removed in this milestone.

The exact Open-Meteo request parameters from source were also probed once using the
same public sample coordinates. HTTP 200 returned the requested current/daily fields,
including moon_phase. There is no verified endpoint rejection to fix here; this does
not validate the disconnected UI or its cache, timezone and unknown-value behavior.

Documentation drift: README says API 26+ although minSdk is 29 and claims immediate
buildability despite missing wrapper components. Architecture claims stateless composables
despite local UI state. Roadmap calls direct Overpass future work although emergency
lookup already exists. Export/share content labels itself v2; OpenAI user agent says 1.0.
lint.xml claims resource shrinking is enabled but both build types disable it.
The unused Kotlin Compose plugin alias refers to 1.9.24 and must not be activated as-is.

## V4 sequence and acceptance gates

1. **Persistence and safety foundations (S1, F1-F6).** Export existing Room schemas,
   add fresh-install and 1->2 migration preservation tests using verified old schemas,
   remove destructive fallback safely, make cache replacement transactional, fix scan
   timeout/permission behavior, and implement Overpass identification and error states.
   Gate: seeded observations survive upgrade; offline/stale/empty/error remain distinct;
   repeated emergency entry cannot display results from a prior location; hospital ways
   and equator locations are covered; all existing tests remain green.
2. **Reconnect existing functionality (F7-F11).** Wire in-app AI with explicit provider
   capability and current external fallback, reopen saved observations from history/search,
   persist place context, and expose existing weather/export functionality where it fits
   the core surfaces. Disable misleading provider readiness rather than claim nonexistent
   integrations. Gate: one end-to-end path per surface, with and without network/keys;
   no package identity change and no lost intent functionality.
3. **Trustworthy data and lifecycle behavior (F4-F6, F12-F15).** Add provenance/freshness,
   correct timezones/unknown values, verify emergency contact sources, close responses,
   propagate cancellation, inject shared testable clients, and prevent overlapping jobs.
   Gate: contract tests for HTTP 406/429/5xx, malformed JSON, timeout, stale cache,
   cancellations, unavailable providers, and changed locale/timezone.
4. **Privacy, release readiness and maintainability (S2-S7, F16-F17).** Make backup and
   outbound data policy explicit, add credential recovery, remove unnecessary permission,
   use secure signing outside Git, address lint with evidence, centralize stable action
   identifiers and user strings, then update documentation and add CI.
   Reconcile the verified skipped Compose/LiveData lint registries before treating lint
   as complete coverage. Gate: debug/release build, unit tests, instrumented migration/UI tests, lint,
   fresh/reinstall/upgrade smoke checks, accessibility checks and external-intent fallback
   checks on API 29 and a modern API. Never use uninstall as an upgrade test.

No dynamic plugin system, remote sync, on-device LLM, broad architecture replacement,
package rename or database redesign is justified by this baseline milestone.

## Reproduction and audit limits

Run from the canonical root with an appropriate JDK and SDK environment:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\jdk-17.0.20.1+1'
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat assembleRelease --console=plain
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat lintRelease --console=plain
```

Keep generated APKs, credentials, signing files and raw build logs outside Git.
Build logs/probe bodies in ignored `build/audit/` are local evidence, not durable
source artifacts. This plan records the durable conclusions. Device behavior,
third-party app launches, GPS accuracy, Keystore hardware backing, database upgrades
and paid/authenticated providers require subsequent targeted runtime verification.

Toolchain reference: [AGP 8.1 compatibility](https://developer.android.com/build/releases/agp-8-1-0-release-notes).

## Milestone change boundary

Audit commit changes only `.gitattributes`, `V4_PLAN.md`, `gradle.properties`,
`gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`,
`gradlew`, and `gradlew.bat`. All app sources, tests, manifests, resources, dependencies,
SDK levels, version and package IDs remain identical to `v3-recovered-baseline`.
Local-only changes are downloaded JDK/SDK packages and ignored build/probe artifacts.
No v4 application implementation was started. Stop after committing this milestone.

## Milestone 2: bounded defect repairs (2026-10-03)

The following sections supersede the first-milestone stop instruction for the repairs
explicitly authorized by the user. The protected tag remains unchanged.

### Checkpoint 1: Overpass request identification and failure semantics

- A dedicated HTTP client identifies every Overpass request centrally using
  `FieldIntelligenceConsole/<versionName> (Android)`; production DI supplies the app's
  existing BuildConfig version. No endpoint or query text was changed for the 406 fix.
- HTTP 400/406/429/5xx, socket/network errors, malformed or missing elements, and
  nonblank Overpass remarks are failures. Raw response bodies are not UI messages.
  Cancellation propagates. Only a successful empty response enables NO RESULTS.
- The UI retains existing cache rows on refresh failure and renders the explicit
  `STALE CACHED SERVICES` warning. The ViewModel state test waits for an actual failed
  refresh and proves row retention, error state, stale warning and offline numbers.
- `OverpassContractTest` exercises the production provider with MockWebServer:
  multiple identified requests, form/query preservation, 406/429/5xx mappings,
  empty success, remark failure, missing elements, malformed JSON, timeout,
  cancellation and failed-refresh cache preservation. `EmergencyFailureStateTest`
  verifies the cache/UI state behavior. Twelve new tests in total.
- Geometry is **not solved at this checkpoint**: node-only filtering and omitted
  way/relation coordinates remain a separate defect. Any later geometry repair must
  have explicit reproduction tests and cannot be credited to this HTTP fix.
- Focused tests passed; final debug/release builds, all unit tests and lint results
  are recorded in `build/milestone2/01-checkpoint-gate.log` (local ignored evidence).
  Gate passed: 14 tests per variant (2 original + 12 new), both APKs assembled,
  lint 0 errors / 4 pre-existing warnings per variant. No new warning suppressions.

### Checkpoint 2: database safety

- History inspection found only the recovered database v2 and its additive 1->2
  migration. No authentic v1 schema export or historical APK exists here. The v1 SQL
  fixture is explicitly reconstructed from those recovered columns/migration statements.
  Supported evidence: that reconstructed v1 path and the actual current v2 schema;
  this is not proof about every historical installation.
- Before removing fallback, migration/reopen tests passed; expanded checks then passed
  for all original fields, UTF-8 note bytes, next generated ID, both added tables,
  all current FieldEntry metadata across reopen, and malformed migration rollback.
  An unsupported v3 test failed against the destructive fallback, demonstrating its
  unsafe behavior. After removing fallback, all four DatabaseSafetyTest cases pass.
- `fallbackToDestructiveMigration` is removed. Missing paths and malformed schemas
  throw explicit open/migration errors; no invented migration or automatic data reset
  is provided. Supporting an unknown installed schema requires its actual evidence.
- Schema version remains **2**, identity hash remains
  `86a44fb821267550e82ce2703802e359`. The generated v2 schema is now tracked in
  `app/schemas/` for future migration review. No columns or table layouts changed.
- All tests create UUID-named `migration-test-*` / `migration-device-test-*` databases.
  They never call the default production singleton, and enforce that cleanup cannot
  target `field_console.db`. **REAL DATABASE TOUCHED: NO.**
- Robolectric API 34 tests are JVM integration evidence, not physical-device validation.
  Two Android instrumentation tests are added for v1 migration and unsupported-version
  preservation, using the same explicitly reconstructed fixture. Device execution is a
  later gate (`connectedDebugAndroidTest` on an isolated test device); compilation alone
  must not be reported as device-test success.
- Evidence: `build/milestone2/02-supported-before-removal.log`,
  `02-destructive-reproduction.log` (expected failure), `02-focused-final.log`, and
  `02-gate.log`. The first full gate hit a Robolectric legacy SQLite WAL-file open
  error in debug (release passed). Test filenames were shortened and a Windows path
  length guard added; production journaling was not changed. The full debug recheck
  and final forced rerun of both suites passed. This host failure remains recorded;
  device execution is still required for stronger validation.
- Final gate (`02-gate-final.log`): 18 tests executed per variant, 0 failures/errors;
  debug/release and instrumentation APKs built; both lint variants passed with the
  same 4 existing warnings. Instrumentation test count: 2 added, **not executed**.

### Checkpoint 3: emergency dialing safety

- Source audit found only ACTION_DIAL call sites, so CALL_PHONE was removed. Both
  emergency-number and facility-phone buttons use one small dialer helper that opens
  ACTION_DIAL, never ACTION_CALL. All invocations remain explicit button actions.
  A missing phone app now shows the number instead of crashing.
- Four focused Robolectric tests verify the merged manifest permission, outgoing dial
  action/number, missing-handler message, and offline catalog availability for every
  supported country plus unknown-country fallback. The numbers themselves are unchanged;
  this does not certify their correctness or local eligibility.
- Focused tests passed. Full gate evidence: `build/milestone2/03-gate.log`.
  Gate passed: 22 tests per variant, both app builds, lint 0 errors / 4 existing warnings.

### Checkpoint 4: bounded reverse geocoding

- Address lookup has a three-second deadline, explicit failure/empty/timeout notices,
  and cancellation propagation. Coordinates, accuracy and timestamp remain available.
  Scan observation and action-matrix headers display the notice; no address is invented.
- API 33+ handles both callbacks; older blocking calls run on a bounded daemon worker.
  Cancellation interrupts best-effort: Android Binder may ignore interruption, in which
  case new requests fail promptly rather than accumulating blocked threads. Device-level
  behavior is still a validation gate, not proven by JVM callback tests.
- Six focused tests cover timeout, preserved coordinates, late/duplicate callbacks,
  cancellation, backend failure, empty result and successful address mapping.
- `build/milestone2/04-focused.log` and `04-gate.log`: focused and full suites passed,
  28 tests per variant, both APK builds passed, lint 0 errors / 4 existing warnings.

### Checkpoint 5: reproduced emergency geometry and cache defects

- Separate from the 406 checkpoint, two failing reproduction tests demonstrated omitted
  equator nodes, ways and relations. The emergency query now uses `nwr` with `out center`;
  mapping accepts valid node coordinates and way/relation centers, including latitude zero,
  and skips absent/out-of-range coordinates. MockWebServer tests cover the wire response.
  Logistics queries are unchanged. Centers are bounding-box centers, not verified entrances;
  the UI states this limitation. Reference: https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL
- Four cache tests failed before repair: neighboring-origin reuse/deletion, loss on failed
  insertion, mixed/future timestamps accepted as fresh, and repeated empty-success requests.
  Origins now match exactly, all rows must be fresh, and replacement uses a Room transaction.
  A SQLite abort trigger proves the old rows survive replacement failure. One bounded
  session-only empty-result cache avoids repeat calls; it is intentionally not durable.
- A state test reproduced uncancelled previous-location work. Refresh and observation now
  share a cancellable lifetime, with serialized repository refreshes. Switching locations
  cancels the old request/collector and prevents their rows from populating the new scan.
  The earlier cached-data/stale-warning failure test still passes.
- Evidence: `05-geometry-red.log`, `05-cache-red.log`, `05-state-red.log` (expected failures),
  `05-focused.log`, `05-gate.log` under `build/milestone2/`. Seven additional tests pass;
  full gate: 35 tests per variant, both APKs built, lint 0 errors / 4 existing warnings.
- Database version stays 2 with unchanged identity hash. Tests use in-memory Room databases;
  the production database is untouched. Full polygon/entrance/routing geometry is not solved.
  Existing heuristic travel times and unverified open-status values remain separate risks.
