# Android NETWORK and report workflows

Implemented local manual creation/edit/delete, typed multiple contact values and
addresses, source ancestry, geographic scopes, selected VCF/CSV/JSON import/export,
CSV mapping, review/cancel/atomic confirmation and duplicate preview. Optional phone
Contacts import is explicit/read-only; denied/revoked permission leaves file import
available. Optional address geocoding requires a separate outbound preview and tap.
Known Network is reached from Tools or the Action Matrix; initial SCAN stays simple.

Field report exchange uses the shared schema, local searchable library, scoped
export preview and report-derived place selection. NETWORK records/counts are not
included by default. Unknown address, provider failure, empty and stale states stay
distinct. Existing Action Matrix and observation history remain intact.

Evidence before final candidate freeze:
- 11 focused forward-geocoder, phone-provider-fake and report tests passed.
- Full integrated debug/release JVM suite: 92 tests each, zero failures/skips;
  debug/release assembly and lint passed (0 errors, 4 advisory warnings).
- First full version-bump run failed one hardcoded old User-Agent assertion;
  corrected it to expect the actual BuildConfig version and reran successfully.
- Isolated Android API36 x86_64 emulator: 3 migration + 2 real Compose tests passed.
  UI tests use random isolated databases, prove preview/cancel/import/repeat/search,
  manual creation and reopen. They do not substitute for document-picker evidence.
- Normal APK UI document picker imported the shared synthetic VCF; explicit selected
  export preview and Android SAVE produced `device-network.json`. Normal report
  picker/import confirmation/export produced `device-report.json` with stale,
  successful-empty, offline and failure states visibly preserved.
- Small-screen inspection found a clipped JSON import control; changed its row to
  FlowRow. Final installed-APK visual recheck remains part of the candidate gate.

Local-only evidence: `.local/evidence/android-ui/` (XML UI snapshots, build/test logs,
synthetic device exports), `.local/evidence/m3/` (migration/core results).
No physical device, personal contacts, real emergency calls or production user data
were used. Emulator observations are not physical GPS or ARM64 runtime proof.
