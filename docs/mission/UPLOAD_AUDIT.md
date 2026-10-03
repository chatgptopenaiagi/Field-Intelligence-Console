# Initial history upload review

Date: 2026-10-03. Decision: **reviewed history eligible for upload to the intended
private FIC repository**, subject to coordinator verification of destination and
exact refs. This is not evidence that a repository was created or a push occurred.

## Exact reviewed scope

| Ref | Resolved commit |
|---|---|
| `v4-development` | `e1f8c90fcca5b7581d51bbad8a2d8efd37874706` |
| `v3-recovered-baseline` | `e45f9b863892b1796774a8132a213ea99c4518d2` |

The baseline is currently a lightweight tag. Resolution used `^{commit}`; the
helper also inventories annotated tag objects when supplied. No ref was changed.
All reachable commits were enumerated, with every tree inspected for blob paths,
including historical paths absent from HEAD. Counts: **8 commits, 127 distinct
blobs, 95 distinct paths, 700,666 total distinct-blob bytes**.

## Findings and review

- No provider-token, private-key, credential-literal or authenticated-URL pattern
  candidates were found in these reachable text blobs. Credential-bearing code
  was reviewed: provider keys are runtime inputs, the Keystore example is an
  abbreviated placeholder, and the CAD tenant is an explicit placeholder.
- No tracked database, contact import, key store, APK, executable, raw runtime
  log or exported operational report exists anywhere in the reviewed path history.
  The reconstructed SQL file defines a table only. Schema JSON contains structure,
  not observations. Migration-test inserted rows are explicit synthetic fixtures.
- Coordinate candidates belong to synthetic tests. The original recovered prompt
  tests use public city example coordinates and a landmark note. V4_PLAN labels
  the Overpass probe coordinates as public samples, not device position; it records
  aggregate results rather than raw nearby-service payloads or personal history.
- The historical V4_PLAN versions contain generic local JDK/SDK installation paths
  using the standard Administrator account. These are ordinary tool-path/build
  documentation, not personal contact identifiers, observed location history,
  environment dumps or private operational evidence. Future documentation should
  prefer environment-variable examples. The protected baseline/history was not
  rewritten to cosmetically remove these paths.
- Emergency phone literals are the application's public emergency-number catalog,
  not imported personal contacts. Their real-world correctness is a separate
  product-safety review, not established by this upload audit.
- All eight commit author/committer identities are the explicit Codex noreply
  identity. No personal author email, secret-bearing message, symlink or submodule
  was found in scope.
- The sole binary blob is the 43,453-byte Gradle wrapper JAR. Its SHA-256 is
  `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`, matching the
  official wrapper hash already verified and recorded by the build audit.
- Sixteen scanner finding groups were reviewed: synthetic coordinate/SQL fixtures
  and repeated historical tool paths. No upload blocker was identified in this scope.

## Repeatable tool and evidence

From the canonical repository root:

```powershell
python tooling/audit_upload.py v4-development v3-recovered-baseline --output .local/evidence/upload-audit/history.json
```

The standard-library-only tool emits categories, blob IDs, paths, line numbers,
sizes and hashes, never candidate secret values. It intentionally returns
`REQUIRES_HUMAN_REVIEW`; pattern scanning cannot authorize a push automatically.
Its local receipt inventories every distinct blob. The helper was exercised in
an isolated synthetic Git repository: a credential-like literal deleted from HEAD
was still detected in history, its value was absent from the report, and an
annotated tag resolved and was inventoried correctly. That check passed.

This review does not certify arbitrary encoding, ignored local files, generated
release artifacts or future commits. Repeat the scan and review new blobs/metadata
on the exact candidate before each first push of a ref. The helper and this document
were uncommitted additions at review time; the coordinator must include them and
other new mission changes in that subsequent candidate review. Do not infer
absence of sensitive history merely from a clean tree or ignore rules.
