# Cognitive keypoints

`fic.earth-keypoints` version 1 is a standalone JSON envelope for Earth Engine results. Existing report and network exchanges keep their original schemas. The Earth client collects results in memory. An export is an explicit action.

| Field | Meaning |
| --- | --- |
| `selection`, `radius_km`, `category` | Query location in decimal WGS84 degrees, optional spherical distance filter and selected subject. |
| `records`, `matching_count` | The UI exports the displayed records, up to 80. Its matching count includes all qualifying records before that display limit. The headless adapter can return the full bounded source response. |
| `id`, `category`, `label` | Source-specific identity, category and display label. IDs are unique within a result. |
| `latitude`, `longitude` | Geographic point. USGS GeoJSON arrives in longitude/latitude order. For area warnings the point is the user query and metadata records its role. |
| `observed_at`, `valid_at`, `retrieved_at` | Provider observation/report time, forecast validity time, and retrieval or archive date. A missing time is null or absent. A retrieval date cannot supply a missing measurement time. |
| `provider`, `source`, `verification` | Named source, original reference and basis for the record. Provider-reported data and model output carry distinct labels. |
| `kind`, `freshness` | Kind supports OBSERVED, REPORTED, PREDICTED, SIMULATED, HISTORICAL and UNKNOWN. Freshness is evaluated at retrieval. The current feeds emit reported, historical and predicted records. |
| `measurements`, `metadata` | Measurements pair a numeric value with its unit. Null denotes missing data. Metadata retains qualifications such as orbital element epoch or archive flight identity. |
| `passes` | Predicted intervals for the original satellite observer with first and last samples and peak sampled elevation in degrees. Selecting a different observer from an exported file clears these intervals. |
| `checked_at` | Optional query completion time. An archived observation keeps its original retrieval time when opened offline. |
| `description`, `coverage`, `limitations` | Optional per-record source context added in Mission 005. Compatible v1 readers can still open beta9 documents without these fields. |

Python exposes observation-time and magnitude selection through `earth.select(..., since=..., min_magnitude=...)` and the CLI options `--since` and `--min-magnitude`. The time cutoff requires an explicit UTC offset. A geographic selection recomputes the count of matching records in the supplied export.

The Python reader rejects unsupported envelope versions, invalid coordinates, duplicate record IDs, invalid measurement types and incomplete provenance. It can read every category through `fic earth export`. All content inside records remains data. Reopening an export cannot launch a provider or execute a command.

The globe and Python use the same mean Earth radius for spherical distances, including across the antimeridian. Radius results describe straight-line surface distances. Route geometry, road access and travel times require a routing provider. [Engine behavior and CLI](COGNITIVE_EARTH_ENGINE.md)
