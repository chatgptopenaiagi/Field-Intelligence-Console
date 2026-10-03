"""Bounded offline VCF/CSV/FIC exchange. Imported text is data, never instructions."""
from dataclasses import dataclass, field
from datetime import datetime
import csv
import hashlib
import io
import json
import unicodedata
from .models import (MAX_BYTES, MAX_RECORDS, ValidationError, new_entity,
                     network_dataset, validate_network, json_loads, now_iso)

MAX_PROPERTY_BYTES = 16384
CSV_COLUMNS = ("display_name", "kind", "organization", "role", "phone", "email", "street",
               "neighborhood", "locality", "region", "postal_code", "country", "label", "notes")


@dataclass(frozen=True)
class Issue:
    record: int
    code: str
    message: str


@dataclass
class Preview:
    entities: list
    issues: list
    source_id: str
    source_name: str
    format: str
    warnings: list = field(default_factory=list)
    columns: list = field(default_factory=list)

    @property
    def valid_count(self):
        return len(self.entities)

    @property
    def invalid_count(self):
        return len({issue.record for issue in self.issues})


@dataclass
class ExportPreview:
    data: bytes
    warnings: list
    count: int


def normalized(value):
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


def normalize_phone(value):
    """No inferred country. Preserve + only when it was explicitly supplied."""
    text = value[4:] if value.lower().startswith("tel:") else value
    # Extensions and ambiguous text remain searchable rather than guessed as E.164.
    return "".join(ch for ch in text if ch.isdigit() or ch in "+;=#*").strip()


def _hash(data):
    return hashlib.sha256(data).hexdigest()


def _entity_id(source_id, record_id):
    return _hash((source_id + ":" + record_id).encode("utf-8"))


def _split(value, delimiter, quoted=False, preserve_escape=True):
    result, part, escape, quote = [], [], False, False
    for char in value:
        if escape:
            if preserve_escape:
                part.append("\\")
            part.append(char)
            escape = False
        elif char == "\\":
            escape = True
        elif char == '"' and quoted:
            quote = not quote
            part.append(char)
        elif char == delimiter and not quote:
            result.append("".join(part))
            part = []
        else:
            part.append(char)
    if escape:
        raise ValidationError("Trailing vCard escape")
    if quote:
        raise ValidationError("Unclosed vCard parameter quote")
    result.append("".join(part))
    return result


def _unescape(value):
    result, escape = [], False
    for char in value:
        if escape:
            if char in "nN":
                result.append("\n")
            elif char in "\\,;:":
                result.append(char)
            else:
                raise ValidationError("Unsupported vCard escape")
            escape = False
        elif char == "\\":
            escape = True
        else:
            result.append(char)
    if escape:
        raise ValidationError("Trailing vCard escape")
    return "".join(result)


def _property(line):
    quote, escape, index = False, False, None
    for i, char in enumerate(line):
        if escape:
            escape = False
        elif char == "\\":
            escape = True
        elif char == '"':
            quote = not quote
        elif char == ":" and not quote:
            index = i
            break
    if index is None:
        raise ValidationError("Missing vCard property delimiter")
    head, value = line[:index], line[index + 1:]
    parts = _split(head, ";", quoted=True)
    name = parts[0].split(".")[-1].upper()
    if not name or not all(c.isascii() and (c.isalnum() or c == "-") for c in name):
        raise ValidationError("Invalid vCard property name")
    params = {}
    for parameter in parts[1:]:
        if "=" not in parameter:
            raise ValidationError("Legacy bare vCard parameter unsupported")
        key, val = parameter.split("=", 1)
        params.setdefault(key.upper(), []).extend(
            item.strip('"').replace("^n", "\n").replace("^'", '"').replace("^^", "^")
            for item in _split(val, ",", quoted=True))
    if "ENCODING" in params:
        raise ValidationError("Embedded/legacy encoded vCard properties unsupported")
    if params.get("CHARSET", ["UTF-8"])[0].upper() not in {"UTF-8", "UTF8"}:
        raise ValidationError("Only UTF-8 vCards are supported")
    return name, params, value


def _label(params):
    types = [part.casefold() for value in params.get("TYPE", []) for part in value.split(",")]
    for known in ("home", "work", "organization"):
        if known in types:
            return known
    return ",".join(types) or "unspecified"


def _address(entity, parts, label="unspecified", original=None):
    parts = list(parts) + [""] * (7 - len(parts))
    aid = entity["id"] + "-address-" + str(len(entity["addresses"]))
    address = dict(zip(("po_box", "extended", "street", "locality", "region", "postal_code", "country"), parts))
    address.update(id=aid, original=original or ", ".join(p for p in parts if p), label=label, neighborhood="")
    entity["addresses"].append(address)
    entity["associations"].append({"id": entity["id"] + "-association-" + str(len(entity["associations"])),
        "address_id": aid, "basis": "saved_address", "source": "imported_contact", "temporal_status": "saved",
        "point": None, "geocode_status": "unresolved", "provider": None, "resolved_at": None,
        "verified_at": None, "normalized_address": None})
    return address


def _ancestry(entity, source_id, record_id, format, updated_at=None):
    entity["ancestry"].append({"source_id": source_id, "record_id": record_id, "format": format,
                                "imported_at": now_iso(), "updated_at": updated_at})


def _parse_card(lines, source_id):
    if len(lines) > 1000:
        raise ValidationError("vCard exceeds 1000 properties")
    properties = [_property(line) for line in lines]
    versions = [value for name, _, value in properties if name == "VERSION"]
    if len(versions) != 1 or versions[0] not in {"3.0", "4.0"}:
        raise ValidationError("Only vCard 3.0 and 4.0 are supported")
    names = [value for name, _, value in properties if name == "FN"]
    if len(names) != 1 or not names[0].strip():
        raise ValidationError("vCard requires one nonblank FN")
    uids = [value for name, _, value in properties if name == "UID"]
    if len(uids) > 1:
        raise ValidationError("Duplicate vCard UID")
    record_id = _unescape(uids[0]) if uids and uids[0] else _hash("\n".join(lines).encode("utf-8"))
    if len(record_id) > 256:
        record_id = _hash(record_id.encode("utf-8"))
    entity = new_entity(_unescape(names[0]))
    entity["id"] = _entity_id(source_id, record_id)
    unknown, warnings, updated = [], [], None
    for raw, (name, params, value) in zip(lines, properties):
        if name in {"VERSION", "FN", "UID"}:
            continue
        if name == "N":
            components = [_unescape(p) for p in _split(value, ";")]
            if len(components) != 5:
                raise ValidationError("N must contain five structured components")
            entity["structured_name"] = dict(zip(entity["structured_name"], components))
        elif name == "KIND":
            if value == "org":
                entity["kind"] = "organization"
            elif value not in {"individual", ""}:
                unknown.append(raw)
                warnings.append("Unsupported vCard KIND retained as inert extension")
        elif name == "ORG":
            entity["organization"] = " / ".join(_unescape(p) for p in _split(value, ";"))
            if ";" in value:
                unknown.append(raw)
        elif name in {"TITLE", "ROLE"}:
            entity["role"] = _unescape(value)
        elif name == "NOTE":
            entity["notes"] += ("\n" if entity["notes"] else "") + _unescape(value)
        elif name in {"TEL", "EMAIL"}:
            val = _unescape(value)
            entity["phones" if name == "TEL" else "emails"].append({"value": val,
                "normalized": normalize_phone(val) if name == "TEL" else normalized(val), "label": _label(params)})
        elif name == "ADR":
            components = [_unescape(p) for p in _split(value, ";")]
            if len(components) != 7:
                raise ValidationError("ADR must contain seven structured components")
            _address(entity, components, _label(params), params.get("LABEL", [None])[0])
        elif name == "CATEGORIES":
            entity["tags"].extend(_unescape(p) for p in _split(value, ","))
        elif name == "REV":
            try:
                stamp = datetime.fromisoformat(value.replace("Z", "+00:00"))
                if stamp.tzinfo is None:
                    raise ValueError()
                updated = stamp.isoformat().replace("+00:00", "Z")
            except ValueError:
                unknown.append(raw)
                warnings.append("Unqualified source update date retained without inventing a timezone")
        else:
            if value.lower().startswith("data:"):
                raise ValidationError("Embedded vCard data URIs are unsupported")
            unknown.append(raw)
            if name in {"PHOTO", "URL", "KEY", "GEO", "LOGO", "SOUND", "SOURCE", "AGENT"}:
                warnings.append("External references/embedded content retained as inert text only; never fetched")
    if unknown:
        entity["extensions"]["vcard_properties"] = unknown
    _ancestry(entity, source_id, record_id, "vcf", updated)
    validate_network(network_dataset([entity]))
    return entity, warnings


def _vcf(data, preview):
    # Unfold bytes BEFORE UTF-8 decoding so a legal fold inside a multibyte sequence works.
    raw_lines = data.replace(b"\r\n", b"\n").split(b"\n")
    unfolded = []
    for line in raw_lines:
        if line.startswith((b" ", b"\t")):
            if not unfolded:
                raise ValidationError("vCard begins with an orphan continuation")
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
        if len(unfolded[-1]) > MAX_PROPERTY_BYTES:
            raise ValidationError("vCard property exceeds 16384 bytes")
    card, index = None, 0
    for raw in unfolded:
        try:
            line = raw.decode("utf-8-sig")
        except UnicodeError as exc:
            raise ValidationError("Malformed UTF-8 vCard") from exc
        if not line:
            continue
        if line.upper() == "BEGIN:VCARD":
            if card is not None:
                preview.issues.append(Issue(index, "malformed", "Unclosed vCard before next record"))
            index += 1
            if index > MAX_RECORDS:
                raise ValidationError("More than 5000 records")
            card = []
        elif line.upper() == "END:VCARD":
            if card is None:
                preview.issues.append(Issue(index, "malformed", "END without BEGIN"))
                continue
            try:
                entity, warnings = _parse_card(card, preview.source_id)
                preview.entities.append(entity)
                preview.warnings.extend(warnings)
            except ValidationError as exc:
                preview.issues.append(Issue(index, "invalid_card", str(exc)))
            card = None
        elif card is not None:
            card.append(line)
        else:
            preview.issues.append(Issue(index, "malformed", "Text outside a vCard record"))
    if card is not None:
        preview.issues.append(Issue(index, "malformed", "Unclosed final vCard"))
    if index == 0:
        raise ValidationError("No vCard records")


def _csv(data, preview, mapping):
    try:
        text = data.decode("utf-8-sig")
        reader = csv.DictReader(io.StringIO(text, newline=""), strict=True)
        if not reader.fieldnames or len(reader.fieldnames) != len(set(reader.fieldnames)):
            raise ValidationError("CSV requires unique headers")
        if len(reader.fieldnames) > 100:
            raise ValidationError("CSV exceeds 100 columns")
        preview.columns = list(reader.fieldnames)
        mapping = mapping or {key: key for key in CSV_COLUMNS if key in reader.fieldnames}
        if any(k not in CSV_COLUMNS or v not in reader.fieldnames for k, v in mapping.items()):
            raise ValidationError("CSV mapping references an unsupported or missing column")
        if not any(key in mapping for key in ("display_name", "phone", "email", "organization")):
            raise ValidationError("Map a name, phone, email or organization column before importing")
        for index, row in enumerate(reader, 1):
            if index > MAX_RECORDS:
                raise ValidationError("More than 5000 records")
            try:
                if None in row or any(value is None for value in row.values()):
                    raise ValidationError("CSV row has the wrong number of columns")
                if any(len(v.encode("utf-8")) > MAX_PROPERTY_BYTES for v in row.values()):
                    raise ValidationError("CSV cell exceeds 16384 bytes")
                item = {key: row.get(mapping.get(key), "") for key in CSV_COLUMNS}
                if not any(item[key].strip() for key in ("display_name", "phone", "email", "organization")):
                    raise ValidationError("CSV record has no name, phone, email or organization")
                entity = new_entity(item["display_name"], item["kind"] or "person")
                entity["id"] = _entity_id(preview.source_id, str(index))
                for key in ("organization", "role", "notes"):
                    entity[key] = item[key]
                for key, plural in (("phone", "phones"), ("email", "emails")):
                    if item[key]:
                        entity[plural].append({"value": item[key], "normalized": normalize_phone(item[key]) if key == "phone" else normalized(item[key]),
                                               "label": item["label"] or "unspecified"})
                if any(item[key] for key in ("street", "neighborhood", "locality", "region", "postal_code", "country")):
                    address = _address(entity, ["", "", item["street"], item["locality"], item["region"], item["postal_code"], item["country"]],
                                       item["label"] or "unspecified")
                    address["neighborhood"] = item["neighborhood"]
                unmapped = {key: val for key, val in row.items() if key not in mapping.values()}
                if unmapped:
                    entity["extensions"]["csv_unmapped"] = unmapped
                    preview.warnings.append("Unmapped CSV columns retained in canonical JSON extensions")
                _ancestry(entity, preview.source_id, str(index), "csv")
                validate_network(network_dataset([entity]))
                preview.entities.append(entity)
            except ValidationError as exc:
                preview.issues.append(Issue(index, "invalid_row", str(exc)))
    except (UnicodeError, csv.Error) as exc:
        raise ValidationError("Malformed UTF-8 CSV") from exc


def parse_import(data, format, source_name="Selected file", mapping=None):
    if not isinstance(data, bytes):
        raise ValidationError("Import requires bytes")
    if len(data) > MAX_BYTES:
        raise ValidationError("Input exceeds 5 MiB limit")
    if b"\x00" in data:
        raise ValidationError("NUL/binary content is unsupported")
    fmt = format.lower().lstrip(".")
    if fmt in {"json", "fic", "fic.json"}:
        fmt = "json"
    preview = Preview([], [], _hash(data), source_name, fmt)
    if fmt == "vcf":
        _vcf(data, preview)
    elif fmt == "csv":
        _csv(data, preview, mapping)
    elif fmt == "json":
        document = json_loads(data)
        if not isinstance(document, dict) or not isinstance(document.get("entities"), list):
            raise ValidationError("Network dataset requires an entities array")
        if len(document["entities"]) > MAX_RECORDS:
            raise ValidationError("More than 5000 records")
        envelope = dict(document, entities=[])
        validate_network(envelope)
        seen = set()
        for index, entity in enumerate(document["entities"], 1):
            try:
                validate_network(dict(envelope, entities=[entity]))
                if entity["id"] in seen:
                    raise ValidationError("Duplicate entity ID within dataset")
                seen.add(entity["id"])
                preview.entities.append(entity)
            except ValidationError as exc:
                preview.issues.append(Issue(index, "invalid_entity", str(exc)))
        if set(document) - {"schema", "schema_version", "app_version", "exported_at", "entities"}:
            preview.warnings.append("Dataset envelope extensions are not stored as contact fields; retain the canonical file if needed")
    else:
        raise ValidationError("Supported formats: vcf, csv, json")
    preview.warnings = list(dict.fromkeys(preview.warnings))
    return preview


def _escape(value):
    return value.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")


def _fold(line):
    # RFC limit is octets; never split inside UTF-8 encoded character on export.
    lines, part, length = [], [], 0
    for char in line:
        size = len(char.encode("utf-8"))
        if length + size > 75:
            lines.append("".join(part))
            part, length = [" "], 1
        part.append(char)
        length += size
    lines.append("".join(part))
    return "\r\n".join(lines)


def _safe_csv(value):
    # Always quote CSV cells AND neutralize formula prefixes after whitespace.
    # Deliberate display transformation, never change the original entity.
    if value.lstrip().startswith(("=", "+", "-", "@")) or value.startswith(("\t", "\r", "\n")):
        return "'" + value
    return value


def export_preview(entities, format):
    entities = list(entities)
    document = network_dataset(entities)
    fmt = format.lower().lstrip(".")
    if fmt in {"json", "fic", "fic.json"}:
        data = (json.dumps(document, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")
        if len(data) > MAX_BYTES:
            raise ValidationError("Export exceeds 5 MiB; select fewer records")
        return ExportPreview(data, [], len(entities))
    warnings = ["Format does not retain FIC source ancestry, corrections, geographic associations or extension semantics; use FIC JSON for canonical exchange."]
    if fmt == "vcf":
        lines = []
        for entity in entities:
            lines.extend(["BEGIN:VCARD", "VERSION:4.0", "UID:" + _escape(entity["id"]), "FN:" + _escape(entity["display_name"]),
                "N:" + ";".join(_escape(entity["structured_name"][key]) for key in ("family", "given", "additional", "prefix", "suffix"))])
            if entity["kind"] == "organization":
                lines.append("KIND:org")
            for key, prop in (("organization", "ORG"), ("role", "ROLE"), ("notes", "NOTE")):
                if entity[key]:
                    lines.append(prop + ":" + _escape(entity[key]))
            if entity["tags"]:
                lines.append("CATEGORIES:" + ",".join(_escape(tag) for tag in entity["tags"]))
            for key, prop in (("phones", "TEL"), ("emails", "EMAIL")):
                for item in entity[key]:
                    label = item["label"] if item["label"] in {"home", "work"} else ""
                    lines.append(prop + (";VALUE=text" if prop == "TEL" else "") + (";TYPE=" + label if label else "") + ":" + _escape(item["value"]))
            for address in entity["addresses"]:
                label = address["label"] if address["label"] in {"home", "work"} else ""
                lines.append("ADR" + (";TYPE=" + label if label else "") + ":" + ";".join(_escape(address[key]) for key in
                    ("po_box", "extended", "street", "locality", "region", "postal_code", "country")))
            lines.append("END:VCARD")
        warnings.append("VCF export omits custom labels, neighborhood and unknown original properties; original values remain in FIC JSON.")
        data = ("\r\n".join(_fold(line) for line in lines) + "\r\n").encode("utf-8")
    elif fmt == "csv":
        buffer = io.StringIO(newline="")
        writer = csv.DictWriter(buffer, fieldnames=CSV_COLUMNS, quoting=csv.QUOTE_ALL)
        writer.writeheader()
        for entity in entities:
            row = {key: entity.get(key, "") for key in ("display_name", "kind", "organization", "role", "notes")}
            row.update(phone=entity["phones"][0]["value"] if entity["phones"] else "",
                       email=entity["emails"][0]["value"] if entity["emails"] else "")
            address = entity["addresses"][0] if entity["addresses"] else {}
            row.update({key: address.get(key, "") for key in CSV_COLUMNS if key not in row})
            writer.writerow({key: _safe_csv(val) for key, val in row.items()})
        warnings.append("CSV contains only the first phone, email and address; tags/structured names are omitted. Formula-like values (including + phone numbers) are prefixed with an apostrophe for spreadsheet safety.")
        data = buffer.getvalue().encode("utf-8-sig")
    else:
        raise ValidationError("Supported export formats: vcf, csv, json")
    if len(data) > MAX_BYTES:
        raise ValidationError("Export exceeds 5 MiB; select fewer records")
    return ExportPreview(data, warnings, len(entities))
