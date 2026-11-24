"""Versioned documents and safe, bounded validation."""
from copy import deepcopy
from datetime import datetime, timezone
from importlib.resources import files
import json
import math
import re
from uuid import uuid4
from jsonschema import Draft202012Validator, FormatChecker

APP_VERSION = "4.0.0-beta.7"
MAX_BYTES = 5 * 1024 * 1024
MAX_RECORDS = 5000
FORMATS = FormatChecker()


@FORMATS.checks("date-time")
def _date_time(value):
    if not isinstance(value, str):
        return True  # The JSON type constraint diagnoses this separately.
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:[Zz]|[+-]\d{2}:\d{2})", value):
        return False
    try:
        stamp = datetime.fromisoformat(value.replace("Z", "+00:00").replace("z", "+00:00"))
        return stamp.tzinfo is not None and stamp.utcoffset() is not None
    except ValueError:
        return False


class ValidationError(ValueError):
    """A safe diagnostic: never interpolate private input values."""


def now_iso():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def _walk(value, depth=0):
    if depth > 32:
        raise ValidationError("JSON nesting exceeds 32 levels")
    if isinstance(value, float) and not math.isfinite(value):
        raise ValidationError("Nonfinite numbers are not allowed")
    if isinstance(value, str) and len(value) > 16384:
        raise ValidationError("JSON string exceeds 16384 characters")
    if isinstance(value, dict):
        if len(value) > MAX_RECORDS:
            raise ValidationError("JSON object exceeds 5000 properties")
        for key, item in value.items():
            if not isinstance(key, str):
                raise ValidationError("JSON keys must be strings")
            if len(key) > 16384:
                raise ValidationError("JSON property name exceeds 16384 characters")
            if key.casefold() in {"live_location", "current_presence", "live_tracking", "presence"}:
                raise ValidationError("Live location or presence data is unsupported")
            _walk(item, depth + 1)
    elif isinstance(value, list):
        if len(value) > MAX_RECORDS:
            raise ValidationError("JSON array exceeds 5000 items")
        for item in value:
            _walk(item, depth + 1)
    elif value is not None and not isinstance(value, (str, int, float, bool)):
        raise ValidationError("Unsupported JSON value")


def _validate(document, schema_name):
    _walk(document)
    try:
        encoded = json.dumps(document, ensure_ascii=False, allow_nan=False).encode("utf-8")
    except (ValueError, TypeError, UnicodeError) as exc:
        raise ValidationError("Invalid JSON data") from exc
    if len(encoded) > MAX_BYTES:
        raise ValidationError("Document exceeds 5 MiB limit")
    schema = json.loads(files(__package__).joinpath("schemas", schema_name).read_text("utf-8"))
    error = next(Draft202012Validator(schema, format_checker=FORMATS).iter_errors(document), None)
    if error:
        # Paths can themselves contain imported private strings: do not echo them.
        raise ValidationError(f"Document violates {schema_name} ({error.validator})")
    return document


def validate_network(document):
    _validate(document, "network-v1.schema.json")
    ids = set()
    for entity in document["entities"]:
        if entity["id"] in ids:
            raise ValidationError("Duplicate entity ID")
        ids.add(entity["id"])
        address_ids = [a["id"] for a in entity["addresses"]]
        association_ids = [a["id"] for a in entity["associations"]]
        if len(address_ids) != len(set(address_ids)) or len(association_ids) != len(set(association_ids)):
            raise ValidationError("Duplicate address or association ID")
        for association in entity["associations"]:
            if association["address_id"] is not None and association["address_id"] not in address_ids:
                raise ValidationError("Association refers to a missing address")
            if association["point"] is not None and not has_usable_point(association):
                raise ValidationError("Unresolved or ambiguous points cannot become geographic evidence")
    return document


def has_usable_point(association):
    """A manually verified saved point needs no fabricated geocoder resolution."""
    return association["point"] is not None and (
        association["geocode_status"] == "resolved" or (
            association["geocode_status"] == "unresolved"
            and association["basis"] == "manual_location"
            and association["source"] == "user_observation"
            and association["verified_at"] is not None))


def validate_report(document):
    _validate(document, "field-report-v1.schema.json")
    location = document["location"]
    if (location["latitude"] is None) != (location["longitude"] is None):
        raise ValidationError("Coordinates must both be present or both be unknown")
    # Network is not a standard-report extension; deliberate NETWORK export is separate.
    def check(value):
        if isinstance(value, dict):
            if any(k.casefold() in {"network", "contacts", "network_summary", "network_count", "entities"}
                   for k in value):
                raise ValidationError("NETWORK content is excluded from ordinary field reports")
            for item in value.values():
                check(item)
        elif isinstance(value, list):
            for item in value:
                check(item)
    check(document)
    return document


def new_entity(display_name, kind="person"):
    return {"id": str(uuid4()), "kind": kind, "display_name": display_name,
            "structured_name": dict.fromkeys(("family", "given", "additional", "prefix", "suffix"), ""),
            "organization": "", "role": "", "tags": [], "notes": "", "phones": [], "emails": [],
            "addresses": [], "associations": [], "ancestry": [], "corrections": [], "extensions": {}}


def network_dataset(entities):
    result = {"schema": "fic.network", "schema_version": 1, "app_version": APP_VERSION,
              "exported_at": now_iso(), "entities": deepcopy(list(entities))}
    return validate_network(result)


def make_report(location=None, note="", tag="", providers=None):
    result = {"schema": "fic.field-report", "schema_version": 1, "app_version": APP_VERSION,
              "id": str(uuid4()), "observed_at": now_iso(),
              "location": {"latitude": None, "longitude": None, "accuracy_m": None,
                           "source": "unknown", "address": None, "address_source": "unknown",
                           "resolved_at": None},
              "observation": {"note": note, "tag": tag}, "providers": deepcopy(providers or []),
              "ai_advisory": None, "extensions": {}}
    if location:
        result["location"].update(deepcopy(location))
    return validate_report(result)


def ai_packet(report):
    validate_report(report)
    return {key: deepcopy(report[key]) for key in
            ("schema", "schema_version", "id", "observed_at", "location", "observation", "providers")}


def json_loads(data):
    if len(data) > MAX_BYTES:
        raise ValidationError("Input exceeds 5 MiB limit")
    def unique_object(pairs):
        value = {}
        for key, item in pairs:
            if key in value:
                raise ValidationError("Duplicate JSON property")
            value[key] = item
        return value
    try:
        document = json.loads(data.decode("utf-8-sig"), object_pairs_hook=unique_object,
                              parse_constant=lambda _: (_ for _ in ()).throw(ValidationError("Nonfinite number")))
        _walk(document)
        return document
    except ValidationError:
        raise
    except (UnicodeError, ValueError, RecursionError) as exc:
        raise ValidationError("Malformed UTF-8 JSON") from exc
