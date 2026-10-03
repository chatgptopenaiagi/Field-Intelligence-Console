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

Candidate3 at8e47a77 passed full clean local All and actual final APK SAF -> fresh
wheel -> packaged EXE -> second fresh wheel exact round trip. Artifacts/evidence in
.local/release-4.0.0-beta.1-candidate-3 and .local/evidence/final-roundtrip-candidate3.
Workflow diagnostics fixed shared AVD home. Google APIs image left6687MiB free vs
7372.8MiB required; attempted2GiB config cannot override emulator6GiB floor. Now
fresh CI installs official API34 AOSP default image (~2.52GiB smaller expanded),
without deleting other tools or changing host permissions. Hosted run37111296019
is pending. Fix any actual failure; then freeze final source, full clean All gate,
exactSHA Python/Windows/Android CI, metadata/roundtrip rerun, annotated tag/release.
No tag/release exists. Root emulator5580 remains running; no physical device used.
Agents finished/released ownership; verify live state. Preserve all failure evidence.
Do not turn setup failure into test PASS or stop at an intermediate milestone.
Known installed working tools: JDK17 under USERPROFILE/.jdks/jdk-17.0.20.1+1,
Gradle wrapper8.7/AGP8.5.2, Python3.14.7 x64 with Tk9.0.4, gh2.100.0.
Tool paths are hints: verify before use. Detailed evidence belongs in .local/evidence/.
