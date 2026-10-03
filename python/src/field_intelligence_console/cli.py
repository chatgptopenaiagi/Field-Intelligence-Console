"""Explicit-input/output CLI. Diagnostics contain counts, never contact values."""
import argparse
import json
from pathlib import Path
import sys
from . import (ValidationError, parse_import, export_preview, Store, query_network,
               validate_network, validate_report)
from .models import json_loads, MAX_BYTES


def _read(path):
    with Path(path).open("rb") as stream:
        value = stream.read(MAX_BYTES + 1)
    if len(value) > MAX_BYTES:
        raise ValidationError("Input exceeds 5 MiB limit")
    return value


def _write(path, data):
    # Refuse overwrite rather than replacing unrelated files; deliberate exports only.
    with Path(path).open("xb") as stream:
        stream.write(data)


def parser():
    result = argparse.ArgumentParser(prog="fic", description="Offline FIC validation, conversion and saved-association search")
    result.add_argument("--version", action="version", version="4.0.0b2")
    commands = result.add_subparsers(dest="command", required=True)
    validate = commands.add_parser("validate", help="Validate an explicit FIC JSON file")
    validate.add_argument("input")
    preview = commands.add_parser("preview", help="Parse only; print sanitized counts and diagnostics")
    preview.add_argument("input")
    preview.add_argument("--format", choices=("vcf", "csv", "json"), required=True)
    convert = commands.add_parser("convert", help="Deliberately convert selected valid records to a new file")
    convert.add_argument("input")
    convert.add_argument("--from", dest="input_format", choices=("vcf", "csv", "json"), required=True)
    convert.add_argument("--to", dest="output_format", choices=("vcf", "csv", "json"), required=True)
    convert.add_argument("--output", required=True)
    convert.add_argument("--select", nargs="+", required=True, help="Entity IDs from an application preview, or explicit ALL_VALID")
    convert.add_argument("--accept-loss", action="store_true", help="Acknowledge VCF/CSV loss and spreadsheet transformation")
    imp = commands.add_parser("import", help="Atomic selected import into an explicit local database")
    imp.add_argument("input")
    imp.add_argument("--format", choices=("vcf", "csv", "json"), required=True)
    imp.add_argument("--database", required=True)
    imp.add_argument("--select", nargs="+", required=True)
    search = commands.add_parser("search", help="Local search; result contact data requires explicit output file")
    search.add_argument("--database", required=True)
    search.add_argument("--text", default="")
    search.add_argument("--scope", choices=("building", "street", "neighborhood", "city", "region", "country", "radius"))
    search.add_argument("--place", help="Explicit JSON file with selected place")
    search.add_argument("--radius", type=float, default=1000)
    search.add_argument("--output", required=True)
    return result


def main(argv=None):
    args = parser().parse_args(argv)
    try:
        if args.command == "validate":
            document = json_loads(_read(args.input))
            if document.get("schema") == "fic.network":
                validate_network(document)
            else:
                validate_report(document)
            print("VALID")
        elif args.command == "search":
            if not Path(args.database).is_file():
                raise ValidationError("Local database does not exist")
            with Store(args.database) as store:
                entities = store.list_entities(args.text)
                if args.scope:
                    place = json_loads(_read(args.place)) if args.place else None
                    result = query_network(entities, args.scope, place, args.radius)
                    entities = result["matches"]
                    print(result["state"])
                _write(args.output, export_preview(entities, "json").data)
                print(json.dumps({"exported": len(entities)}))
        else:
            fmt = args.input_format if args.command == "convert" else args.format
            preview = parse_import(_read(args.input), fmt)
            counts = {"valid": preview.valid_count, "invalid": preview.invalid_count, "warnings": preview.warnings,
                      "issues": [{"record": i.record, "code": i.code, "message": i.message} for i in preview.issues]}
            print(json.dumps(counts))
            if args.command == "preview":
                return 0 if preview.valid_count else 2
            selected = [e["id"] for e in preview.entities] if args.select == ["ALL_VALID"] else args.select
            if set(selected) - {e["id"] for e in preview.entities}:
                raise ValidationError("Selected record does not exist")
            if args.command == "import":
                with Store(args.database) as store:
                    print(json.dumps(store.import_preview(preview, selected)))
            else:
                output = export_preview([e for e in preview.entities if e["id"] in selected], args.output_format)
                print(json.dumps({"warnings": output.warnings}))
                if output.warnings and not args.accept_loss:
                    raise ValidationError("Lossy export requires --accept-loss after reviewing warnings")
                _write(args.output, output.data)
        return 0
    except ValidationError as exc:
        print("ERROR: " + str(exc), file=sys.stderr)
        return 2
    except (OSError, TypeError, ValueError):
        # Never leak input values, file paths or a SQLite document through diagnostics.
        print("ERROR: Operation failed; verify the explicit input/output and local access", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
