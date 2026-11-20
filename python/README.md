# Field Intelligence Console Python core

Offline report and saved-contact association models, import/export, SQLite storage,
geographic queries and a CLI. Saved addresses are **not current physical presence**.
No implicit network, contacts, telemetry or AI calls. Requires Python 3.11 or newer;
the mission's local compatibility evidence is recorded in docs/mission/M2_CORE.md.

Install the wheel: `python -m pip install field_intelligence_console-4.0.0b6-py3-none-any.whl`.
Remove it: `python -m pip uninstall field-intelligence-console`. The caller owns its
explicit local database/export files; uninstall does not delete those files.

```console
fic preview synthetic.vcf --format vcf
fic convert synthetic.vcf --from vcf --to json --select ALL_VALID --output selected.json
fic validate selected.json
fic import selected.json --format json --database local.sqlite --select ALL_VALID
fic search --database local.sqlite --text Example --output matches.json
```

Conversion/import never overwrite an output file. CLI ALL_VALID is an explicit
selection; applications use preview record IDs. Preview is read-only. VCF/CSV export
requires `--accept-loss`; canonical FIC JSON retains original values and provenance.
Malformed cards produce a visible summary; no card is imported without selection.

Public APIs are exported from `field_intelligence_console`; the complete field/API
contract and limits are in shared/CONTRACT.md. Packaged schemas and synthetic fixtures
are available through `importlib.resources.files('field_intelligence_console')`.

Text vCard 3.0/4.0 UTF-8 is supported, including byte unfolding, multiple contacts,
typed phone/email/address fields, structured names and escaped separators. Unknown
properties remain inert extensions. Legacy 2.1/quoted-printable/binary encodings are
diagnosed, not silently decoded. PHOTO/URL/KEY are never fetched or executed.

CSV format and mapping are documented in shared/CONTRACT.md. CSV export is deliberately
lossy and spreadsheet-neutralizes formula-like values with an apostrophe, including
phone numbers beginning with +. FIC JSON preserves the exact original phone value.
VCF/CSV do not preserve FIC geography/provenance; review warnings before saving.

The store uses unencrypted SQLite in a caller-selected OS-protected directory. No
destructive fallback. Deletion does not guarantee forensic erasure or removal from
independent backups/exports. Tests use disposable synthetic stores only.

Run `fic diagnostics` to check version and bundled schemas offline without opening user data.
