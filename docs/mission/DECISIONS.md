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
