# Release artifact audit

`tooling/audit_artifacts.py` scans only explicitly allowlisted files. It reads ZIP
source archives, WHL/APK/JAR and tar/gzip sdist contents, and PyInstaller EXE CArchive/PYZ contents,
including Python constants and code filenames. It never runs embedded code or
extracts archive paths into the filesystem. The PyInstaller reader layout was
inspected from installed 6.22.3 source before using its metadata API.

Bounds: 1 GiB per asset/aggregate expanded scan, 64 MiB per entry, 20,000 entries,
300:1 expansion ratio, four nested archive levels, bounded TOCs and code-object walks.
Tar/gzip containers are additionally capped at 64 MiB compressed and expanded before
parsing PAX/member metadata. Links, devices and unsafe paths are flagged without
following or extracting them; concatenated gzip streams are rejected as unreviewable.
Unsupported, encrypted or oversized content prevents a clean automated result.

Detection covers bundled databases/profiles, environment files, credential stores,
private-key material, strong token shapes, selected credential assignments, contact
data filenames outside documented fixture paths, unsafe archive paths and private
absolute Python code filenames. Reports contain finding categories and entry hashes,
never candidate secret strings or private member paths. Canonical project build
paths are counted separately without printing them.

Synthetic fixtures and upstream notices are classified separately, not treated as
blanket permission to include private data. Pattern scans cannot prove that every
personal value or unknown credential format is absent. A finding requires local
review; even a clean scan returns **REQUIRES_REVIEW**, with exact asset hashes.
The coordinator must record contextual review for the final release candidate;
the scanner never self-authorizes publication or reports PASS_REVIEWED.

Six synthetic self-tests verify secret detection and redaction, database detection,
fixture/notice classification, decompression limits, private code-path detection,
tar traversal/links/devices and gzip expansion limits:
`.venv-windows/Scripts/python.exe tooling/audit_artifacts.py --self-test`.
Actual preliminary and final scan receipts belong under ignored `.local/evidence/`.
Final artifacts must be rescanned after any rebuild; earlier hashes are not evidence
for changed release assets.

Preliminary execution: **six self-tests passed**. Explicit APK, portable EXE, wheel
and coordinator-generated source ZIP were inspected successfully with **zero
findings and zero incomplete entries**; status remains **REQUIRES_REVIEW**.
Receipt: `.local/evidence/artifact-audit-preliminary-four-assets.json`.
The EXE inspection traversed actual embedded PYZ/code objects, rather than merely
searching the compressed outer executable. This preliminary scan is not final
release evidence; source binding is checked separately by clean-build gate receipts.
The actual preliminary `field_intelligence_console-4.0.0b1.tar.gz` sdist was also
scanned with zero findings/incomplete coverage; receipt:
`.local/evidence/artifact-audit-preliminary-sdist.json`.
