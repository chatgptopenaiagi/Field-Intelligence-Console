# Resume

Read MISSION.md, STATUS.md, DECISIONS.md, TEST_MATRIX.md and BLOCKERS.md.
Verify actual Git status/branch/HEAD and `v3-recovered-baseline^{commit}`. Do not reset.
Current mission starts at e1f8c90; completed earlier Android repairs remain intact.

Coordinator owns Git integration and shared contracts/platform sequencing.
Delegated ownership initially:
- upload_audit: tooling/audit_upload.py, docs/mission/UPLOAD_AUDIT.md only.
- android_safety: manifest, backup XML, emergency repository/screen, focused safety
  tests and docs/mission/M0_SAFETY.md; owns current Gradle execution.
Do not overlap writes. Check live agents/processes before resuming.

Next: complete M0; review full-history audit before push; implement shared contracts
and Python core, then Android NETWORK and Windows GUI. Do not stop after a milestone.
Known installed working tools: JDK17 under USERPROFILE/.jdks/jdk-17.0.20.1+1,
Gradle wrapper8.7/AGP8.1.4, Python3.14 x64 with Tk9.0, gh2.100.0.
Tool paths are hints: verify before use. Detailed evidence belongs in .local/evidence/.
