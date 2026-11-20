# MISSION 001 decisions — 2026-11-20

- Current explicit public-release instruction supersedes old private-only mission;
  preserve original repository and complete contextual review before visibility change.
- Beta1/beta2 tags remain immutable. New beta3 maps to Python4.0.0b3, Androidcode6,
  Windows4.0.0.3. Existing schemas and application IDs remain unchanged.
- Keep Kotlin/Compose and Python/Tk/PyInstaller; retain cumulative recovered features.
- Beta2 install failure is concrete package-service transport evidence. Use package
  readiness and official ADB no-streaming, with bounded transient-only retry.
  https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/client/commandline.cpp
- Public visibility also exposes Actions history/logs; include them in review:
  https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility
- All runtime tests use explicit isolated emulators and synthetic profiles/data.
  Existing emulator5556 is unrelated. No desktop focus takeover for native tests.
- Requested APK/EXE filenames carry BETA; versions/hashes/source remain in metadata,
  tag, wheel name, manifest, checksums and source archive.

# Decisions ledger

- Preserve Android root and package IDs. Add shared/, python/, windows/, tooling/.
- Choose application prerelease 4.0.0-beta.1 (Python4.0.0b1, Windows4.0.0.1,
  Android code4) if tag conflict check remains clear at freeze. Data schemas independent.
- NETWORK is saved association, never current presence. Offline import/query first;
  no network details/counts enter normal reports or AI prompts.
- Selected/implemented Python standard-library Tk/ttk + PyInstaller: Python3.14.7
  x64/Tk9.0.4, one real native GUI, no server. Packaged/native/restart gates executed.
- Restore skipped lint registries with compatible AGP8.5.2/Gradle8.7/JDK17; retain
  advisory old-target/version warnings without suppression. No broad dependency rewrite.
- Root owns final Git/release integration. New annotated release tag is created only
  after clean local gates; never retarget an existing tag to repair a candidate.
- Runtime evidence is target-specific: emulator x86_64 and Windows native GUI tested;
  static Android ARM ABI presence is not physical-device proof.
- A successful database commit is not proof that a native editor has closed or
  restored keyboard focus. Keep native interaction gates and verify close/activation
  state before the next action; do not promote a partial or intermittent GUI run.
- Release finalization independently rejects skipped Python tests, in addition to
  the upstream test gate. The Windows gate requires the complete focused UI regression set.
- Finalization checks embedded EXE file/product versions and wheel package, dependency,
  license and both schema resources against the coordinated prerelease mapping.
- Installed avdmanager --help also returns 1 for valid usage (17 lines); remove
  that help-only fatal CI probe before enabling hosted runtime. Actual SDK install,
  AVD creation, boot and instrumentation remain mandatory failure gates.
- Hosted runtime run37110135912 proved /dev/kvm unavailable to the job. Attempt the
  [official emulator -accel off fallback](https://developer.android.com/studio/run/emulator-commandline)
  when KVM is inaccessible; no chmod, virtualization or host-policy change. Boot is
  bounded to 900 seconds with process/ADB checks; instrumentation remains bounded
  and mandatory. Software mode is recorded and is not presumed to pass.
- Hosted startup diagnostics proved a separate AVD lookup mismatch; explicitly share
  ANDROID_AVD_HOME between creator/emulator. Next failure showed default userdata
  requires 7372.8 MiB with only6687 MiB free. The attempted2GiB setting was ineffective
  because [official emulator code](https://android.googlesource.com/platform/external/qemu/+/emu-master-dev/android-qemu2-glue/main.cpp)
  forces6GiB for API>=24; it has been removed. Official archive inventories show
  API34 AOSP default expands to1,764,599,215 bytes versus Google APIs4,469,083,531.
  Install only the smaller AOSP image in fresh CI, retain all six tests and record
  available storage. This does not validate Google Play services or physical GPS.

- Preserve annotated beta.1 and its unpublished draft after hosted inventory failure.
  Use a new beta.2 candidate (Androidcode5/Python4.0.0b2/Windows4.0.0.2), with no
  package/schema identity change. Historical fixture app versions stay beta.1.
  Do not promote old binaries or claim the new candidate passed before rebuilding.
