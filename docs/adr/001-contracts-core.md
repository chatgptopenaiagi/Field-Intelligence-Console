# ADR 001: Shared contracts and local Python core

Accepted 2026-10-03. Android remains at repository root. Shared JSON Schemas and
synthetic fixtures live in shared; the wheel packages copies and tests verify equality.
Schema version 1 is independent of 4.0.0-beta.1 / Python 4.0.0b1 app versions.

Python uses src layout, stdlib parsing/SQLite plus jsonschema validation. A bounded
stateful vCard parser implements the documented text subset of RFC 2426/6350,
rather than claiming universal vCard support. Unknown fields remain inert; external
references and blobs are never fetched. Explicit preview/selection precedes atomic
import. Content-derived source IDs and per-source record IDs support repeat imports,
without fuzzy name/address merging. FIC JSON is canonical; VCF/CSV report format loss.

SQLite stores validated JSON documents and separate ancestry uniqueness records;
transactions protect import and edits. No destructive migration or cloud service.
Storage is not encrypted; OS account protections apply. Deletion is logical and is
not a promise of forensic erasure or removal from independent exported/backed-up files.

Geographic associations represent saved evidence, never presence. Text matches are
qualified by locality/country. Radius considers resolved building/street saved points
only, not city centroids. Unsupported precision produces an explicit insufficient-data
state. Providers remain optional and import never geocodes.

References consulted: RFC 2426 and RFC 6350 (RFC Editor), official Python Packaging
User Guide, jsonschema validation documentation. No third-party parser code copied.
