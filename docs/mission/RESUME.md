# Resume

Read MISSION.md, STATUS.md, DECISIONS.md, TEST_MATRIX.md and BLOCKERS.md.
Verify actual Git status/branch/HEAD and `v3-recovered-baseline^{commit}`. Do not reset.
Current mission starts at e1f8c90; completed earlier Android repairs remain intact.

Coordinator owns Git integration and shared contracts/platform sequencing.
Current delegated ownership (verify live agents before edits):
- All three workers finished and released file ownership; verify no new running turn
  before edits. Windows compact Save repair and Android nameless save were tested.
- coordinator: Git/SDK/Gradle/release integration, finalizer, ledgers. Release workflow
  and candidate pipeline handed back; root added mandatory artifact scan before upload.
Do not overlap writes. Check live agents/processes before resuming.

Next: fix actual CI failures, integrate pending release tools/docs, audit/push clean
candidate, execute tooling/release_candidate.ps1 -Tag v4.0.0-beta.1 -Stage All
-Python C:/Python314/python.exe -DeviceSerial emulator-5580 with a fresh ignored
output directory. No release tag exists yet; create only after final local gates pass.
Root-created FIC_MISSION_API36 emulator uses serial5580,
ignored .local/avd, not any other session's AVD. No physical device used.
CI: Python37108131598PASS; Android37108131575failedhelp exit, fixedin2b1bca6,
rerun37108194996PASS; Windows37108131565failednativeSave after2checks, fix8b23e14,
rerun37108858293active.
Review current run states, not these stale hints. Actual6emulator tests nowpassed
(3migration+3Compose),92JVMpervariant beforelast5newtests. Expectedfinalcount97;
sevenfocusedtests+debugbuildpassedaftertheseedits. All prior binaries are preliminary dirty builds.
Then final candidate packaging, manual artifact review, explicit tag/push, private
draft upload/download hash verification, publication/readback and final report.
Do not stop after a milestone. Preliminary binaries are not final artifacts.
Known installed working tools: JDK17 under USERPROFILE/.jdks/jdk-17.0.20.1+1,
Gradle wrapper8.7/AGP8.5.2, Python3.14.7 x64 with Tk9.0.4, gh2.100.0.
Tool paths are hints: verify before use. Detailed evidence belongs in .local/evidence/.
