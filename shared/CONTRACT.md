# FIC exchange contracts v1

Frozen for the experimental 4.0.0-beta.1 release; data version is independent of app version.
The same v1 contracts apply to beta.2. Synthetic beta.1 fixture application versions
are intentionally retained as backward-exchange test vectors.
All JSON is UTF-8, finite JSON numbers only. Dates are ISO 8601 with explicit timezone.
Network JSON uses `schema: "fic.network"`, `schema_version: 1`, `app_version`,
`exported_at`, `entities`. Unknown entity/nested properties survive canonical JSON round trips;
unknown schema versions and live/current-presence semantics are rejected.
Unknown dataset-envelope properties receive a visible import warning because the
contact store persists entities, not arbitrary file envelopes. Retain the original
canonical file explicitly if those envelope extensions are needed.

An entity has `id`, `kind` (`person`/`organization`), `display_name`,
`structured_name` (`family`, `given`, `additional`, `prefix`, `suffix`),
`organization`, `role`, `tags`, `notes`, `phones`, `emails`, `addresses`,
`associations`, `ancestry`, `corrections`, `extensions`.
Phone/email entries: `{value, normalized, label}` (original never overwritten).
Addresses: `{id, original, label, po_box, extended, street, neighborhood, locality,
region, postal_code, country}`. Unknown strings use `""`, unknown objects use null.
Associations: `{id, address_id, basis, source, temporal_status, point,
geocode_status, provider, resolved_at, verified_at, normalized_address}`.
`basis`: saved_address/manual_location/historical_observation.
`source`: imported_contact/user_observation/external_provider.
`temporal_status`: saved/historical/unknown. Never current presence.
`point`: null or `{latitude, longitude, precision}`; latitude [-90,90], longitude
[-180,180]. `precision`: building/street/locality/region/country/approximate.
`geocode_status`: unresolved/resolved/ambiguous/failed.
An unresolved point is usable only for `basis: manual_location`,
`source: user_observation` and non-null `verified_at`: the user explicitly saved
that association without geocoding. This never becomes current-presence evidence.
Ambiguous/failed geocoder candidates cannot become a trusted saved point. Radius
still requires building/street precision; manual city centroids remain excluded.
`address_id`, provider, dates and normalized_address may be null.
Ancestry: `{source_id, record_id, format, imported_at, updated_at}`; updated_at nullable.
Corrections: arbitrary JSON objects; never executable instructions.

FieldReport: `schema: "fic.field-report"`, `schema_version: 1`, `app_version`,
`id`, `observed_at`, `location`, `observation`, `providers`, `ai_advisory`, `extensions`.
Location: `{latitude, longitude, accuracy_m, source, address, address_source, resolved_at}`;
latitude/longitude both null or both valid, accuracy_m null or nonnegative,
source device/manual/report/unknown; address null or string,
address_source device_geocoder/user/external_provider/unknown; resolved_at nullable.
Observation: `{note, tag}`. Provider: `{provider, state, freshness, fetched_at, data, error}`;
state success/empty/offline/failure/unavailable, freshness fresh/stale/unknown,
fetched_at nullable date, data arbitrary JSON or null, error nullable string.
AI advisory null or `{provider, generated_at, text}`; explicitly advisory, never observations.
Ordinary reports and AI packets contain no NETWORK entity data or counts.

## Bounds and compatibility

Maximum input 5 MiB; 5,000 records; 16,384 UTF-8 bytes per unfolded VCF property;
1,000 properties per card; JSON depth 32 and 5,000 items/properties per array/object.
JSON string/property names are bounded to 16,384 Unicode characters.
Text vCard 3.0/4.0 supported, UTF-8 only.
Legacy v2.1 and legacy quoted-printable/embedded binary encodings are diagnosed.
Unknown VCF lines retained as inert `extensions.vcard_properties`; references never fetched.
CSV columns: display_name, kind, organization, role, phone, email, street,
neighborhood, locality, region, postal_code, country, label, notes.
Mapping is canonical-column -> input-column. CSV keeps one primary phone/email/address;
export preview reports loss. Spreadsheet-dangerous values receive an apostrophe in CSV,
including + phone numbers; original values remain unchanged in FIC JSON.

## Python API (public)

`parse_import(data: bytes, format: str, source_name="Selected file", mapping=None) -> Preview`
Preview: `.entities`, `.issues` (Issue: record, code, message), `.source_id`,
`.source_name`, `.format`, `.valid_count`, `.invalid_count`, `.warnings`, `.columns`.
`new_entity(display_name, kind="person") -> dict`; `network_dataset(entities) -> dict`.
`validate_network(dict)`, `validate_report(dict)` raise safe `ValidationError`.
`export_preview(entities, format) -> ExportPreview`: `.data: bytes`, `.warnings`, `.count`.
`Store(path)` context manager: `.preview_counts(preview)`, `.import_preview(preview,
selected_ids) -> {inserted, duplicates}`, `.list_entities(search="")`, `.get_entity(id)`,
`.save_entity(entity)`, `.delete_entity(id)`, `.save_report(report)`,
`.list_reports(search="")`, `.get_report(id)`, `.delete_report(id)`, `.close()`.
Import requires explicit selected IDs, never auto-commits a preview.
`query_network(entities, scope, place, radius_m=1000) -> dict` returns
`{state, matches, count, excluded, explanation}`; matches are entity dicts, counts deduplicated.
Place keys: latitude, longitude, street, neighborhood, locality, region, country.
Scope: building/street/neighborhood/city/region/country/radius.
`distance_m(lat1, lon1, lat2, lon2)` uses spherical great-circle distance.
`make_report(location=None, note="", tag="", providers=None) -> dict`;
`ai_packet(report) -> dict` explicitly allowlists report fields and strips extensions.
No API here performs network requests.
