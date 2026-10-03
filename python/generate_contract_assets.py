"""Maintainer helper: generate reviewed schemas and synthetic vectors, never user data."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "shared"
PACKAGE = ROOT / "python/src/field_intelligence_console"
TEXT = {"type": "string", "maxLength": 16384}
ID = {"type": "string", "minLength": 1, "maxLength": 256}
DATE = {"type": "string", "format": "date-time", "maxLength": 64}
NULL_DATE = {"anyOf": [DATE, {"type": "null"}]}


def enum(*values):
    return {"enum": list(values)}


def nullable(schema):
    return {"anyOf": [schema, {"type": "null"}]}


def obj(properties, required=None):
    return {"type": "object", "properties": properties,
            "required": list(properties) if required is None else required,
            "additionalProperties": True}


def arr(item, maximum=1000):
    return {"type": "array", "items": item, "maxItems": maximum}


POINT = obj({"latitude": {"type": "number", "minimum": -90, "maximum": 90},
             "longitude": {"type": "number", "minimum": -180, "maximum": 180},
             "precision": enum("building", "street", "locality", "region", "country", "approximate")})
ADDRESS = obj({"id": ID, **{key: TEXT for key in ("original", "label", "po_box", "extended", "street",
                "neighborhood", "locality", "region", "postal_code", "country")}})
ASSOCIATION = obj({"id": ID, "address_id": nullable(ID),
    "basis": enum("saved_address", "manual_location", "historical_observation"),
    "source": enum("imported_contact", "user_observation", "external_provider"),
    "temporal_status": enum("saved", "historical", "unknown"), "point": nullable(POINT),
    "geocode_status": enum("unresolved", "resolved", "ambiguous", "failed"),
    "provider": nullable(TEXT), "resolved_at": NULL_DATE, "verified_at": NULL_DATE,
    "normalized_address": nullable(TEXT)})
ANCESTRY = obj({"source_id": ID, "record_id": ID, "format": TEXT, "imported_at": DATE,
                "updated_at": NULL_DATE})
ENTITY = obj({"id": ID, "kind": enum("person", "organization"), "display_name": TEXT,
    "structured_name": obj({key: TEXT for key in ("family", "given", "additional", "prefix", "suffix")}),
    "organization": TEXT, "role": TEXT, "tags": arr(TEXT), "notes": TEXT,
    "phones": arr(obj({"value": TEXT, "normalized": TEXT, "label": TEXT})),
    "emails": arr(obj({"value": TEXT, "normalized": TEXT, "label": TEXT})),
    "addresses": arr(ADDRESS), "associations": arr(ASSOCIATION), "ancestry": arr(ANCESTRY),
    "corrections": arr({"type": "object"}), "extensions": {"type": "object"}})
NETWORK = obj({"schema": {"const": "fic.network"}, "schema_version": {"const": 1},
    "app_version": TEXT, "exported_at": DATE, "entities": arr(ENTITY, 5000)})
REPORT = obj({"schema": {"const": "fic.field-report"}, "schema_version": {"const": 1},
    "app_version": TEXT, "id": ID, "observed_at": DATE,
    "location": obj({"latitude": nullable(POINT["properties"]["latitude"]),
        "longitude": nullable(POINT["properties"]["longitude"]),
        "accuracy_m": nullable({"type": "number", "minimum": 0}),
        "source": enum("device", "manual", "report", "unknown"), "address": nullable(TEXT),
        "address_source": enum("device_geocoder", "user", "external_provider", "unknown"),
        "resolved_at": NULL_DATE}),
    "observation": obj({"note": TEXT, "tag": TEXT}),
    "providers": arr(obj({"provider": TEXT, "state": enum("success", "empty", "offline", "failure", "unavailable"),
        "freshness": enum("fresh", "stale", "unknown"), "fetched_at": NULL_DATE, "data": {},
        "error": nullable(TEXT)})),
    "ai_advisory": nullable(obj({"provider": TEXT, "generated_at": DATE, "text": TEXT})),
    "extensions": {"type": "object"}})


def write(relative, data):
    for base in (SHARED, PACKAGE):
        path = base / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data, encoding="utf-8", newline="\n")


def main():
    for name, schema in (("network-v1", NETWORK), ("field-report-v1", REPORT)):
        schema["$schema"] = "https://json-schema.org/draft/2020-12/schema"
        schema["$id"] = "https://fieldconsole.example/schemas/" + name
        write("schemas/" + name + ".schema.json", json.dumps(schema, ensure_ascii=False, indent=2) + "\n")
    timestamp = "2026-10-03T12:00:00Z"
    entity = {"id": "synthetic-alex-001", "kind": "person", "display_name": "Alex Éxample",
        "structured_name": {"family": "Éxample", "given": "Alex", "additional": "", "prefix": "", "suffix": ""},
        "organization": "Synthetic Research Cooperative", "role": "Synthetic coordinator", "tags": ["synthetic"],
        "notes": "Synthetic test record. Saved address is not current presence.",
        "phones": [{"value": "+1 202 555 0100", "normalized": "+12025550100", "label": "work"},
                   {"value": "020 7946 0000", "normalized": "02079460000", "label": "home"}],
        "emails": [{"value": "alex@example.org", "normalized": "alex@example.org", "label": "work"}],
        "addresses": [{"id": "synthetic-address-001", "original": "1 Example Street, Example City, Example Country",
            "label": "work", "po_box": "", "extended": "", "street": "1 Example Street", "neighborhood": "Example District",
            "locality": "Example City", "region": "Example Region", "postal_code": "00000", "country": "Example Country"}],
        "associations": [{"id": "synthetic-association-001", "address_id": "synthetic-address-001", "basis": "manual_location",
            "source": "user_observation", "temporal_status": "saved", "point": {"latitude": 0.0, "longitude": 0.0, "precision": "building"},
            "geocode_status": "resolved", "provider": None, "resolved_at": None, "verified_at": timestamp, "normalized_address": None}],
        "ancestry": [{"source_id": "synthetic-fixture", "record_id": "synthetic-alex-001", "format": "manual", "imported_at": timestamp, "updated_at": None}],
        "corrections": [], "extensions": {"synthetic": True, "unknown_future_label": "preserve me"}}
    dataset = {"schema": "fic.network", "schema_version": 1, "app_version": "4.0.0-beta.1", "exported_at": timestamp, "entities": [entity]}
    write("fixtures/network-v1.json", json.dumps(dataset, ensure_ascii=False, indent=2) + "\n")
    report = {"schema": "fic.field-report", "schema_version": 1, "app_version": "4.0.0-beta.1", "id": "synthetic-report-001",
        "observed_at": timestamp, "location": {"latitude": 0.0, "longitude": 0.0, "accuracy_m": 12.5, "source": "manual",
        "address": None, "address_source": "unknown", "resolved_at": None}, "observation": {"note": "Synthetic observation", "tag": "test"},
        "providers": [{"provider": "synthetic-" + state, "state": state, "freshness": freshness, "fetched_at": timestamp if freshness != "unknown" else None,
            "data": [] if state == "empty" else None, "error": "Synthetic failure" if state == "failure" else None}
            for state, freshness in (("success", "stale"), ("empty", "fresh"), ("offline", "unknown"), ("failure", "unknown"))],
        "ai_advisory": None, "extensions": {"synthetic": True}}
    write("fixtures/field-report-v1.json", json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    write("fixtures/contacts.vcf", "BEGIN:VCARD\nVERSION:4.0\nUID:synthetic-alex-001\nFN:Alex Éxample\nN:Éxample;Alex;;;\nORG:Synthetic Research Cooperative\nTEL;TYPE=work:tel:+1-202-555-0100\nTEL;TYPE=home:020 7946 0000\nEMAIL;TYPE=work:alex@example.org\nADR;TYPE=work:;;1 Example Street;Example City;Example Region;00000;Example Country\nADR;TYPE=home:;;2 Other Street;Other City;;00001;Example Country\nNOTE:Synthetic record\\nSaved association only\nX-SYNTHETIC:retained unknown value\nEND:VCARD\nBEGIN:VCARD\nVERSION:3.0\nUID:synthetic-org-002\nFN:Synthetic Organisation\nN:;;;;\nORG:Synthetic Organisation\nEMAIL:office@example.org\nEND:VCARD\n")
    write("fixtures/contacts.csv", 'display_name,kind,organization,phone,email,street,locality,country,notes\n"Synthetic, CSV",person,Synthetic Team,+12025550101,csv@example.org,3 Example Street,Example City,Example Country,"Unicode: café"\n')


if __name__ == "__main__":
    main()
