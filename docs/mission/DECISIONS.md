# Decisions ledger

- Preserve Android root and package IDs. Add shared/, python/, windows/, tooling/.
- Choose application prerelease 4.0.0-beta.1 (Python4.0.0b1, Windows4.0.0.1,
  Android code4) if tag conflict check remains clear at freeze. Data schemas independent.
- NETWORK is saved association, never current presence. Offline import/query first;
  no network details/counts enter normal reports or AI prompts.
- Evaluate Python standard-library Tk/ttk + PyInstaller as lowest-dependency Windows
  implementation: Python3.14 x64 and Tk9.0 are installed; one GUI, no server.
  ADR and packaging/runtime verification are required before claiming selection complete.
