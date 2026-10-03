# Resume

Read MISSION.md and the current entries at the end of STATUS.md, TEST_MATRIX.md,
DECISIONS.md and BLOCKERS.md. Earlier entries are historical, not current gates.
Canonical root F:/FieldIntelligenceConsole; branch v4-development. Verify status,
HEAD, worktrees and protected v3-recovered-baseline^{commit}=e45f9b8; never reset.
Mission starting commit e1f8c90. Protected beta1 tag peels to e6ad925; never move it.

Current: beta2 version4.0.0-beta.2 / Python4.0.0b2 / Androidcode5 / Windows4.0.0.2.
Private GitHub repo chatgptopenaiagi/Field-Intelligence-Console exists. Beta1 remains
an unpublished draft because its combined release inventory gate failed. Cause was
obsolete AGP XML path; fixed51dd5f0 and fresh hosted inventory37118686336 passed.
Three target CI runs at51dd also passed. No beta2 tag yet.

Local candidate1 Android97/variant+six runtime and Python45/cleanwheel passed;
packaged Windows28 checks passed but native input failed. Foreground loss was proven.
Quiet-desktop preflight now refuses busy input before opening/typing. Do not compete
with another session. New owned-HWND capture/null-focus/input guards committed;
Windows now requires14focused/28packaged/7native checks. Local busy desktop is NOT a
waived gate. Use isolated hosted runner and exact final APK/EXE/wheel validation.

Retained debug keystore securely provisioned as private repo Actions secret.
Release-only restore step and both-APK public certificate pin committed; never log
secret bytes or replace local identity. Only trusted release workflow references it.
Finalization installs candidate wheel in isolated venv for release guard tests.

NEXT: finish hosted exchange review/tests, audit new reachable history, commit/push
explicit reviewed branch, run exact-source target CI. Freeze clean candidate, create
NEW annotated beta2 tag, audit/push it, run combined hosted release. Download all11
verified assets and inspect exact hashes/signing/source/SBOM/privacy. Bind actual
Android SAF exports to distributed APK hash (re-execute on owned emulator if needed).
Dispatch synthetic hosted wheel/EXE/wheel exchange, then create/download-verify/publish
PRIVATE experimental beta2 release only when every required gate passes. Write final
report and handoff, verify refs/status, then mark persistent goal complete.

Ownership: root owns Git/SDK/final builds/signing. Agent python_core temporarily owns
ONLY tooling/hosted_exchange.py, tooling/tests/test_hosted_exchange.py and exchange-ci.yml;
verify release of ownership before edits. Audit agent read-only. Android agent verifies
ignored synthetic SAF receipt only. No local native GUI/build currently running.
Owned emulator FIC_MISSION_API34 on5580 launcher33652 remains active; verify process
identity before stopping. No real personal databases/contacts or physical device used.
Actual SAF evidence .local/evidence/beta2-android-ui; detailed build/failed native logs
under ignored .local/evidence and .local/release-4.0.0-beta.2-candidate-1.
Tools: verify JDK17 USERPROFILE/.jdks/jdk-17.0.20.1+1, wrapper8.7/AGP8.5.2,
Python3.14.7 x64 C:/Python314/python.exe, gh2.100.0. No global tool/security changes.
