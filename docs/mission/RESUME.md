# Resume

Read MISSION.md, STATUS.md, DECISIONS.md, TEST_MATRIX.md and BLOCKERS.md.
Verify actual Git status/branch/HEAD and `v3-recovered-baseline^{commit}`. Do not reset.
Current mission starts at e1f8c90; completed earlier Android repairs remain intact.

Coordinator owns Git integration and shared contracts/platform sequencing.
Current delegated ownership (verify live agents before edits):
- All agents completed and released ownership. Root owns Git/SDK/final builds.
- Native driver repair passed three consecutive fresh-profile seven-check runs;
  seven focused tests passed. No unproven production focus change retained.
Do not overlap writes. Check live agents/processes before resuming.

Candidate4 at0359591 passed full clean local All and actual final APK SAF -> fresh
wheel -> packaged EXE -> second fresh wheel exact round trip. Evidence lives in
.local/release-4.0.0-beta.1-candidate-4 and final-roundtrip-candidate4.
Hosted Android37111296019 boot/build/unit/lint passed; three migration tests passed,
three UI tests timed out at10s. Software CI now explicitly allows bounded60s waits,
with sanitized state diagnostics and unchanged assertions. Local default10s gate
passed97 unit tests per variant/six device tests/assemblies/lint after this change.
NEXT: audit/push this fix and rerun hosted Android with emulator=true. Inspect state
diagnostics if it fails; do not infer that delay is the cause or claim a PASS.
Then freeze source, full clean All, exactSHA three-platform CI, final metadata and
roundtrip checks, annotated tag and verified private prerelease. No tag/release yet.
Root owns emulator5580; no physical device used. Agents released file ownership.
Known installed working tools: JDK17 under USERPROFILE/.jdks/jdk-17.0.20.1+1,
Gradle wrapper8.7/AGP8.5.2, Python3.14.7 x64 with Tk9.0.4, gh2.100.0.
Tool paths are hints: verify before use. Detailed evidence belongs in .local/evidence/.

Latest: candidate5 atd9ac982 full local All/roundtrip PASS, exactSHA Win/Python CI
PASS. Hosted37113156492 failed APK install before instrumentation, cause unavailable
from old diagnostic upload. Next retry preserves install/emulator failure output.
Defer another full three-platform candidate rebuild until hosted runtime resolves.
No tag/release exists; source/test behavior unchanged by diagnostic-only repair.

CURRENT NEXT ACTION: private beta1 draft/tag exist at e6ad925 and all11 assets match,
but combined release37116367024 failed inventory. Earlier no-tag/runtime-pending
notes are historical. Run the new manual inventory-ci workflow after reviewing and
pushing its diagnostic-only commit. Inspect actual stage/type/code location, repair
only proven cause, then select a new coordinated prerelease/tag (never retarget beta1).
Three target CI workflows and full local candidate6 already passed at e6ad925.
No emulator/build is running locally. Root owns all files; agents released ownership.

NEXT AFTER INVENTORY REPAIR: full clean beta2 candidate at new HEAD using
.local/release-4.0.0-beta.2-candidate-1, isolated FIC_MISSION_API34/emulator5580.
Owned emulator launcher33652 is active; verify identity. Inventory hosted reproduction
proved obsolete AGP XML path; new exact current model path is selected, with nine
focused tooling tests passing. Run fresh hosted inventory plus normal three target
CI gates before tagging/publishing. Final source is not beta1/e6ad925. Retain failed
beta1 draft/tag, its actual receipts and the reconstructed-v1 limitations.
