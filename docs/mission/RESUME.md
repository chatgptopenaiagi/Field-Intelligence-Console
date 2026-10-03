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

Candidate1 at 6f224c2 failed only local Windows native second-editor opening after
three checks; Android97/variant +6 emulator, Python45/fresh-wheel and Windows6/28
passed. All three GitHub target workflows passed this SHA. No release tag exists.
Next: commit reviewed driver/metadata gate repair and ledger, rerun full clean
All gate into a NEW output directory, audit/push exact SHA and rerun target CI.
Command: tooling/release_candidate.ps1 -Tag v4.0.0-beta.1 -Stage All
-Python C:/Python314/python.exe -DeviceSerial emulator-5580 -OutputRoot <new ignored dir>.
Root-owned FIC_MISSION_API36 emulator serial5580 uses .local/avd; do not touch others.
No physical device used. Detailed candidate1 logs: .local/release-4.0.0-beta.1-candidate-1.
Then final artifact/context review, explicit annotated tag/push, release workflow,
private draft upload/download hash verification, publication/readback and final report.
Do not stop after a milestone or publish failed/preliminary candidate artifacts.
Known installed working tools: JDK17 under USERPROFILE/.jdks/jdk-17.0.20.1+1,
Gradle wrapper8.7/AGP8.5.2, Python3.14.7 x64 with Tk9.0.4, gh2.100.0.
Tool paths are hints: verify before use. Detailed evidence belongs in .local/evidence/.
