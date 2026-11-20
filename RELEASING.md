# Public BETA release process

Only the original `chatgptopenaiagi/Field-Intelligence-Console` is an authorized
delivery target. The owner's 2026-11-20 Mission 001 authorizes public visibility
after review of reachable source/history, existing release assets and Actions logs.
Build and test while private; change visibility only after that review and verify
anonymous access after publication. Preserve `v3-recovered-baseline`. Never force
push, move a release tag, overwrite published assets or publish to PyPI.

1. Finish logical repairs, run focused gates and commit. Review complete reachable
   history with `tooling/audit_upload.py` plus manual review of categories, binaries,
   identities and fixture provenance. A clean tree is not a history audit.
2. Push only the explicit reviewed development ref; read back its remote SHA.
   Run/read actual Android, Python and Windows workflows and repair failures. A local
   build or workflow YAML is not a GitHub CI pass. Do not increase spending limits.
3. Freeze a clean candidate commit. Run the shared platform gates and release guard
   tests. Android must run both unit variants/assemblies/lint and the isolated emulator
   tests. Windows must run the packaged EXE, GUI/native interaction and restart in a
   clean synthetic profile. Python must install its wheel outside the checkout.
4. Use the local `tooling/release_candidate.ps1` pipeline to collect the exact tested
   APK/EXE/WHL/sdist and `git archive` source ZIP. No dirty development override is
   accepted for release. Build scripts and output receipts are the executable
   command reference. Optional real-provider credentials/physical phone absence do
   not authorize fabricating runtime evidence.
5. Generate SBOM, third-party notices and artifact inventory from the actual resolved
   Android graph, installed wheel and Windows packager inputs. Review artifacts for
   secrets/private data. `tooling/finalize_release.py` verifies clean same-SHA receipts,
   counts, artifact hashes, package identity, source ZIP commit and inventory before
   writing notes, manifest and checksums. Checksums omit themselves; the manifest
   does not hash itself. Byte reproducibility is not claimed.
6. After focused checks pass, create a new annotated version tag on that commit. Verify
   no existing tag/release collision; never retarget. Review tag/source history and
   explicitly push the branch/tag, then verify remote refs. A planned local version
   name is not evidence that its tag already exists.
7. `.github/workflows/release.yml` is manual/tag-triggered, with write permission only
   for its draft-release job. The coordinator may use the authorized existing `gh`
   client for delivery after the same gates. Use `gh release create --verify-tag
   --draft --prerelease`, an explicit asset allowlist and notes file. Do not publish
   on ordinary development commits or create a stable release.
8. Download every uploaded asset into a separate verification directory, compare
   bytes/SHA-256 to the local candidate, then publish the draft as a public BETA
   prerelease. Read back repository visibility, release/tag/source SHA, draft/prerelease
   flags, asset names/sizes and returned download URLs. No missing-platform release.

The same release script may run on isolated hosted runners when the local desktop
is busy. Local native-input refusal remains a recorded failure; it is never treated
as a pass. Hosted builds must pass all fourteen Windows focused tests, twenty-eight
packaged GUI checks and seven native-input checks, plus the Android and wheel gates.
The release-only signing step restores the retained testing key from the original
repository Actions secret, then removes only its owned runner copy. It requires
an annotated candidate tag and a tag push or dispatch on that exact tag. Repository
secrets are not intrinsically workflow-restricted: only this trusted tag workflow
references it; ordinary/PR workflows do not. Both APK certificates must match the
public fingerprint in shared/release-signing.json. No replacement key is accepted.
The exchange workflow accepts only explicitly reviewed synthetic SAF exports,
binds them to the actual distributed APK hash, and tests the exact released wheel
and EXE on an isolated runner. Publication still requires asset/download review.

Version mapping: FIC `4.0.0-beta.3`; Python `4.0.0b3`; Android code6; Windows numeric
file version4.0.0.3. Shared data contracts v1 and Room v3 are independent. Android
debug/testing signing and Windows unsigned status must be explicit in every release.
Never uninstall user data to bypass a signing mismatch or disable security warnings.

Record actual results in mission ledgers and `FIC_FINAL_REPORT.md`. Final report-only
commits may follow a release; clearly distinguish ending branch SHA from artifact
source SHA. Preserve local-only logs separately; no personal database/contact export
belongs in an artifact, Git history, CI upload or release evidence.
