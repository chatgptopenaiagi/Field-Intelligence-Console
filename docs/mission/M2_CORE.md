# M2 shared contracts and Python core

Implemented schema-version-1 NetworkDataset and FieldReport, shared synthetic vectors
and identical packaged resources. Python package is `field-intelligence-console`
4.0.0b1, application exchange version 4.0.0-beta.1. Local tested interpreter:
CPython 3.14.7 x64. Declared minimum is 3.11; other interpreter runs remain a CI gate.

Core functionality: bounded text vCard 3/4, CSV column mapping, canonical JSON,
per-record safe diagnostics, explicit selected atomic imports, content/source IDs,
idempotence, ancestry-preserving edits, scoped deletion, local search, report library,
qualified geographic scopes and precision-aware radius queries. Unknown entity
properties survive JSON exchange; unknown dataset-envelope fields produce warnings.
VCF/CSV exports show format loss; spreadsheet formula neutralization does not alter
canonical phone/original values. No socket is opened by import/browsing.

Optional OpenCage adapter is entirely separate: consent required, per-call in-memory
key, address-only request, attribution, 1 request/second process limit, 10-second
caller deadline, at most one retry, bounded Retry-After and 256-entry session cache.
It returns candidates without automatic selection. A bounded daemon worker prevents
unbounded resource growth if OS networking ignores cancellation; timeout does not
guarantee an already-sent request stops at the provider. No real key/request used in
development. Sources reviewed: https://opencagedata.com/api and /terms on 2026-10-03.

Validation so far: **45 tests passed**, zero failures/skips, command:
`.venv-core/Scripts/python.exe -m unittest discover -s python/tests -v`.
Evidence: ignored `.local/evidence/python-core-tests.log`.
Tests cover parser folding/Unicode/escapes/legacy/malformed/bounds/inert references,
CSV formulas, original values, duplicates/names, JSON unknowns/future versions/live
presence rejection, timestamps, coordinates, poles/dateline/inclusive radii,
insufficient data, transactional rollback, unsupported store fail-closed, persistence,
report privacy and provider consent/cancel/cache/retry/error behavior.

First focused run exposed absent optional JSON-Schema date-format implementation;
fixed with explicit standard-library timezone/date validation, then reran successfully.
Preliminary WHL and sdist were built, not final release artifacts. Fresh-wheel gate
helper `python/verify_wheel.py` creates an isolated environment outside the checkout
and exercises resources, import, duplicate handling, reopen, geography, report and CLI.
It **passed all five checks**: clean installation, installed workflows, CLI validate,
CLI conversion and CLI search. PYTHONPATH was removed and import origin asserted to
be site-packages. Evidence: `.local/evidence/python-wheel-clean.log`; build evidence:
`.local/evidence/python-package.log`. Preliminary files are under
`.local/evidence/python-dist/`; they are ignored and not source commits.
Final candidate artifacts must be rebuilt by coordinator after source freeze.

Cross-platform QA added a shared corpus of **35 invalid-contract mutations** (one
parameterized test) covering required fields, numeric strings/booleans, nulls,
coordinates, dates/timezones, Unicode, source/precision enums and NETWORK privacy.
The fresh installed-wheel check also executes these mutations, independently of
editable/source imports. Manual user-verified points are usable without pretending
that a geocoder resolved them: unresolved + manual_location + user_observation +
valid verified_at is accepted. Ambiguous/failed/external unresolved candidates remain
excluded; coarse manual centroids remain excluded from meter-level radius results.
Phone-only CSV records can remain nameless and searchable without invented placement.
Actual Android-codec -> installed-wheel -> Windows exchange gate is implemented in
`python/verify_interop.py`; its final receipt is pending Android export generation.

Limitations: only documented UTF-8 text vCard subset supported; no automatic fuzzy
merge, inferred phone region, centroid proximity, current-presence inference, cloud
sync or production database encryption. SQLite deletion is not forensic erasure.
