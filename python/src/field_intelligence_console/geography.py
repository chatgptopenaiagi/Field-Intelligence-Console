"""Saved geographic associations, never current physical presence."""
import math
from .models import ValidationError, network_dataset, has_usable_point
from .exchange import normalized


def _coordinate(lat, lon):
    if isinstance(lat, bool) or isinstance(lon, bool) or not isinstance(lat, (float, int)) or not isinstance(lon, (float, int)):
        raise ValidationError("Coordinates must be numbers")
    if not math.isfinite(lat) or not math.isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
        raise ValidationError("Coordinates are nonfinite or out of range")


def distance_m(lat1, lon1, lat2, lon2):
    _coordinate(lat1, lon1)
    _coordinate(lat2, lon2)
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    hav = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 6371008.8 * 2 * math.asin(math.sqrt(min(1.0, max(0.0, hav))))


def query_network(entities, scope, place, radius_m=1000):
    entities = list(entities)
    def result(state, matches=None, excluded=0, explanation=""):
        matches = matches or []
        return {"state": state, "matches": matches, "count": len(matches), "excluded": excluded,
                "explanation": explanation or "Saved geographic associations are not current physical presence."}
    if not entities:
        return result("EMPTY_STORE")
    if not isinstance(place, dict):
        return result("LOCATION_UNAVAILABLE")
    fields = {"building": ("street", "locality", "country"), "street": ("street", "locality", "country"),
              "neighborhood": ("neighborhood", "locality", "country"), "city": ("locality", "country"),
              "region": ("region", "country"), "country": ("country",)}
    if scope not in {*fields, "radius"}:
        return result("QUERY_FAILED", explanation="Unsupported geographic scope")
    try:
        network_dataset(entities)
        if scope == "radius":
            if place.get("latitude") is None or place.get("longitude") is None:
                return result("LOCATION_UNAVAILABLE")
            _coordinate(place["latitude"], place["longitude"])
            if isinstance(radius_m, bool) or not isinstance(radius_m, (int, float)) or not math.isfinite(radius_m) or radius_m < 0:
                return result("QUERY_FAILED", explanation="Radius must be finite and nonnegative")
        elif any(not isinstance(place.get(key), str) or not place[key].strip() for key in fields[scope]):
            return result("INSUFFICIENT_ADDRESS_DATA", explanation="This text scope requires qualified address fields, not a guessed boundary.")
        matches, eligible, seen = [], 0, set()
        for entity in entities:
            matched, usable = False, False
            if scope == "radius":
                for association in entity["associations"]:
                    point = association["point"]
                    if not has_usable_point(association) or point["precision"] not in {"building", "street"}:
                        continue
                    usable = True
                    distance = distance_m(place["latitude"], place["longitude"], point["latitude"], point["longitude"])
                    if distance <= radius_m + 1e-7:
                        matched = True
            else:
                for address in entity["addresses"]:
                    if any(not address.get(key, "").strip() for key in fields[scope]):
                        continue
                    if scope == "building" and not any(a["address_id"] == address["id"] and has_usable_point(a)
                        and a["point"]["precision"] == "building" for a in entity["associations"]):
                        continue
                    usable = True
                    if all(normalized(address[key]) == normalized(place[key]) for key in fields[scope]):
                        matched = True
            eligible += bool(usable)
            if matched and entity["id"] not in seen:
                matches.append(entity)
                seen.add(entity["id"])
        explanation = ("Distance is to a saved building/street point, not to a person's present location or travel route. Coarser centroids are excluded."
                       if scope == "radius" else "Text-only saved address matches; these are not confirmed geographic boundary or current-presence matches.")
        return result("MATCHES_FOUND" if matches else "NO_MATCHES" if eligible else "INSUFFICIENT_ADDRESS_DATA",
                      matches, len(entities) - eligible, explanation)
    except (ValidationError, TypeError, KeyError):
        return result("QUERY_FAILED", explanation="Invalid geographic data; no results inferred")
