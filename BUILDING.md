# Building and validating FIC

Keep Android at repository root; `python/`, `windows/`, `shared/` and `tooling/`
extend the recovered project. Use project-local Python environments and the wrapper.
Never substitute a real contact database for synthetic fixtures.

Android requires JDK17, Gradle8.7 wrapper, AGP8.5.2, SDK platform34/Build Tools34.0.0.
Set JAVA_HOME and ANDROID_HOME to existing installations. Do not commit local paths
or signing keys. From the repository root:

```powershell
./gradlew.bat testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease lint
pwsh -File tooling/android_gate.ps1
```

On Linux use `./gradlew`. The gate script requires PowerShell7.2+ and validates both
variants, packages, permissions/signatures and lint. To execute instrumentation on
an explicitly owned isolated emulator, pass `-DeviceSerial emulator-<port>`.
It never selects a physical device implicitly. Unit/Robolectric integration evidence
and emulator instrumentation are distinct from physical Android-device proof.

Python core uses a `src` layout and bundled schemas/fixtures:

```powershell
python -m venv .venv-core
.venv-core/Scripts/python.exe -m pip install -e ./python build
.venv-core/Scripts/python.exe -m unittest discover -s python/tests -v
pwsh -File tooling/python_gate.ps1
```

The shared gate builds WHL/sdist, installs the wheel in a fresh environment outside
the checkout, removes PYTHONPATH and verifies real CLI/workflows. Review each script's
parameter help for explicit interpreter/output paths. Do not publish to PyPI.

Windows packaging requires Windows x64, tested Python3.14/Tk9 and pinned development
dependencies in `windows/requirements-build.txt` (see actual Windows build files if
the name changes). Run `tooling/windows_gate.ps1`; it uses the same tested wheel,
builds a real portable PyInstaller EXE, validates PE/runtime startup, packaged Tk
workflows and native UI/restart evidence in a synthetic clean profile. The user EXE
bundles its runtime. Source execution alone is not the packaging gate.

Gates default to clean tracked source. Explicit development-only dirty switches are
for preliminary iteration, never final release provenance. Detailed local logs are
under ignored `.local/evidence/`; sanitized summaries live in `docs/mission/`.
CI workflows build the real targets; actual run URLs/results, not workflow existence,
establish a GitHub CI pass. No signing secret is needed for the labeled testing APK.
