# M0 residual Android safety repairs

Starting repair baseline: `e1f8c90`; existing Overpass, Room, dialer, geocoder and
cache tests retained. No production database or device was accessed.

## Changes and evidence

- Six focused emergency tests cover radius inclusion/exclusion, area centers,
  invalid scan coordinates, poles/antimeridian, legacy caches and unknown status.
  Before repair, four of the first five tests failed (`radius-red.log`). Radius is
  explicitly 5 km to the returned point, including approximate way/relation centers;
  it is not a proof that a large polygon is wholly inside/outside the search area.
- Recomputed cached distances and response distances enforce the same radius.
  Valid zero coordinates remain valid. Haversine numerical rounding is bounded.
  Cached rows are sanitized for presentation without deleting original rows.
- Removed fabricated 40 km/h travel estimates and open-now=true from new results.
  Existing schema fields stay for compatibility, storing 0/false as unverified
  placeholders; UI explicitly says travel time/open-now unknown. A 24/7 source tag
  is labeled as a tag, not live availability. Schema remains v2.
- The stale-cache ViewModel test now uses a cached facility at the scan location,
  replacing its physically inconsistent default (0,0) fixture. Its preservation
  and visible stale-warning assertions remain unchanged.
- Disabled automatic backup and excluded all five app storage domains used by
  this app in legacy, cloud and device-transfer rules. Two tests inspect merged
  manifest and packaged XML. No device-protected storage is used. Newer device_*
  domains are rejected by the current AGP lint and are not declared; any future
  direct-boot storage requires revisiting this policy. No lint suppression added.
  User-selected exports remain the deliberate recovery path. This does not remove
  previously created backups or claim protection against a compromised OS.
- Two offline catalog tests lock the corrected US human Poison Help description
  and the UK NPIS healthcare-professional-only access label. Numbers unchanged;
  no calls placed. The whole worldwide catalog has not been medically certified.

Official sources consulted 2026-10-03:
- [Android backup policy](https://developer.android.com/identity/data/autobackup)
- [US Poison Help](https://poisonhelp.hrsa.gov/contact-us)
- [NPIS contact/access](https://www.npis.org/Contact.html)
- [NPIS public advice scope](https://www.npis.org/Healthcare.html)

## Executed validation

JDK 17, Gradle wrapper 8.7, AGP 8.1.4, compile/target 34, Windows host.

`gradlew.bat testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease lintDebug lintRelease --continue --console=plain`

Final gate: **PASS**, 48 tests per variant, zero failures/errors/skips. Both APKs
assembled. Lint: zero errors, four preexisting warnings each (OldTargetApi,
RedundantLabel, two ObsoleteLintCustomCheck library registries). Ten new tests.
No device runtime validation occurred. Release remains debug signed.

Ignored logs: `.local/evidence/m0/radius-red.log`, `radius-green.log`,
`full-gate.log` (intermediate expected fixture/lint failures), `full-gate-final.log`.

## Remaining scope

Existing geocoder uses bounded single-worker scheduling and three-second caller
timeout with safe late callbacks and cancellation tests. A blocked platform service
may outlive timeout; JVM evidence is not device proof. No duplicate client introduced.

API-key decryption can throw after damaged data or invalidated Keystore material;
provider configuration needs an explicit unavailable/re-entry state, not silent
credential deletion. No stored keys were read. The two skipped lint registries need
a coordinated compatible lint/toolchain change, not diagnostic suppression.

No Git staging/commit performed by the safety worker. Coordinator owns integration.
