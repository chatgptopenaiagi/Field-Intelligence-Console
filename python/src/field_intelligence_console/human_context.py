"""Offline question catalog and consent-scoped prompt composition."""
from copy import deepcopy
from datetime import datetime
from functools import cache
from importlib.resources import files
import json
import math

LANGUAGES = ("ro", "de", "en")
MAX_BYTES = 64 * 1024


@cache
def _catalog():
    asset = files(__package__).joinpath("human_context_data", "cognitive_questions.json")
    return json.loads(asset.read_text(encoding="utf-8"))


def categories():
    return deepcopy(_catalog()["categories"])


def questions(category=None):
    return [deepcopy(q) for q in _catalog()["questions"] if category is None or q["category"] == category]


def question(identifier):
    for item in _catalog()["questions"]:
        if item["id"] == identifier:
            return deepcopy(item)
    raise KeyError(identifier)


def localized(key, language):
    return _catalog()["text"][key][language]


def reviewable(text):
    try:
        return bool(text.strip()) and len(text.encode("utf-8")) <= MAX_BYTES
    except UnicodeEncodeError:
        return False


def _number(value):
    return isinstance(value, (float, int)) and not isinstance(value, bool) and math.isfinite(value)


def _timestamp(value):
    if isinstance(value, str) and "T" in value:
        try:
            if datetime.fromisoformat(value).tzinfo:
                return value
        except ValueError:
            pass
    return None


def compose(identifier=None, language="en", context=None, *, disclosure="none", custom=None,
            observation="", earth_excerpt="", target_language=""):
    """Return a draft. Only explicit text arguments can contribute observation/provider text."""
    if disclosure not in ("none", "region", "exact") or language not in LANGUAGES:
        raise ValueError("Unsupported disclosure or language")
    item = question(identifier) if identifier else None
    ctx = context or {}
    def t(key):
        return localized(key, language)
    request = custom if custom is not None else item["question"][language] if item else t("default")
    if not request.strip():
        raise ValueError("Empty question")
    lines = ["FIELD INTELLIGENCE CONSOLE", "", (identifier + " · " if item else "") + request.strip(), ""]
    def field(key, value):
        lines.append(t(key) + " · " + (str(value) if value is not None and value != "" else t("unknown")))
    origin = ctx.get("origin", "unknown")
    lat, lon = ctx.get("latitude"), ctx.get("longitude")
    valid = _number(lat) and _number(lon) and -90 <= lat <= 90 and -180 <= lon <= 180
    if origin not in ("manual", "historical", "device") or not valid:
        origin = "unknown"
    field("location", t("none" if disclosure == "none" else origin))
    detected = override = None
    if disclosure != "none":
        detected = ctx.get("detected") if origin != "unknown" else None
        override = ctx.get("override")
        if disclosure == "exact":
            field("coords", f"{float(lat)}, {float(lon)}" if origin != "unknown" else None)
            acc = ctx.get("accuracy")
            measured = origin in ("device", "historical") and not ctx.get("manual_origin")
            field("accuracy", f"{float(acc)} m" if measured and _number(acc) and acc > 0 else None)
            field("measured", _timestamp(ctx.get("measured")) if measured else None)
            field("geocoded", ctx.get("geocoded") if origin != "unknown" else None)
        field("area", ctx.get("area") if origin != "unknown" else None)
        field("detected", detected)
        field("override", override)
    conflict = detected and override and detected.upper() != override.upper()
    if conflict:
        lines.append(t("mismatch"))
    country = "" if conflict else (override or detected or "").upper()
    if identifier == "Q08" and country in ("RO", "DE", "AT"):
        field("service", "SMURD" if country == "RO" else "Rettungsdienst")
    if identifier == "Q30":
        field("target", target_language.strip() or t("askTarget"))
    lines.extend(["", t("rules")])
    if item:
        cat = next(c for c in _catalog()["categories"] if c["id"] == item["category"])
        lines.append(cat["rules"][language])
    for key, value in (("notes", observation), ("earth", earth_excerpt)):
        if value.strip():
            lines.append("")
            field(key, value.strip())
    return "\n".join(lines)
