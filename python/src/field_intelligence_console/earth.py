"""Portable queries over the same versioned Earth records exported by the client."""
from datetime import datetime, timezone
from importlib.resources import files
import json
import math
from pathlib import Path
import shutil
import subprocess
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from .models import ValidationError

ASSETS = files("field_intelligence_console").joinpath("earth_client")
USGS = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson"
KINDS = {"OBSERVED", "REPORTED", "PREDICTED", "SIMULATED", "HISTORICAL", "UNKNOWN"}


def coordinates(lat, lon):
    if not all(type(v) in (int, float) and math.isfinite(v) for v in (lat, lon)):
        raise ValidationError("Expected finite numeric coordinates")
    if abs(lat) > 90 or abs(lon) > 180:
        raise ValidationError("Coordinates outside WGS84 range")
    return lat, lon


def distance_km(origin, destination):
    a, b = map(math.radians, coordinates(*origin))
    c, d = map(math.radians, coordinates(*destination))
    h = math.sin((c-a)/2)**2 + math.cos(a)*math.cos(c)*math.sin((d-b)/2)**2
    return 12742.0176 * math.asin(math.sqrt(min(1, max(0, h))))


def client_url(lat, lon, language="en"):
    coordinates(lat, lon)
    page = Path(str(ASSETS.joinpath("index.html"))).resolve()
    if not page.is_file():
        raise ValidationError("Bundled Earth client unavailable")
    params = {"lat":lat, "lon":lon}
    if language:
        params["lang"] = language
    return page.as_uri() + "#" + urlencode(params)


def select(document, origin, radius_km=None, *, min_magnitude=None, since=None):
    coordinates(*origin)
    if radius_km is not None and (not math.isfinite(radius_km) or radius_km < 0):
        raise ValidationError("Radius must be finite and nonnegative")
    if min_magnitude is not None and not math.isfinite(min_magnitude):
        raise ValidationError("Minimum magnitude must be finite")
    if since is not None and (not isinstance(since, datetime) or since.utcoffset() is None):
        raise ValidationError("Use a timezone-aware observation cutoff")
    if not isinstance(document, dict) or document.get("schema") != "fic.earth-keypoints" or document.get("version") != 1:
        raise ValidationError("Unsupported Earth document")
    rows = document.get("records")
    if not isinstance(rows, list) or len(rows) > 10000:
        raise ValidationError("Invalid record list")
    filtered, seen = [], set()
    for row in rows:
        if not isinstance(row, dict) or row.get("kind") not in KINDS:
            raise ValidationError("Invalid keypoint kind")
        if not all(isinstance(row.get(k), str) and row[k] for k in
                   ("id", "category", "provider", "source", "label", "retrieved_at", "freshness", "verification")):
            raise ValidationError("Incomplete keypoint provenance")
        if row["id"] in seen or not isinstance(row.get("metadata"),dict) or not isinstance(row.get("measurements"),dict):
            raise ValidationError("Invalid keypoint or duplicate identity")
        seen.add(row["id"])
        for value in row["measurements"].values():
            if value is not None and (not isinstance(value,dict) or type(value.get("value")) not in (int,float)
                                      or not math.isfinite(value["value"]) or not isinstance(value.get("unit"),str)):
                raise ValidationError("Invalid measurement")
        distance = distance_km(origin, (row.get("latitude"),row.get("longitude")))
        if radius_km is not None and distance > radius_km:
            continue
        magnitude = row["measurements"].get("magnitude")
        if min_magnitude is not None and (not magnitude or magnitude["value"] < min_magnitude):
            continue
        if since is not None:
            stamp = row.get("observed_at")
            if not stamp:
                continue
            try:
                observed = datetime.fromisoformat(stamp)
                if observed.utcoffset() is None or observed < since:
                    continue
            except (ValueError, TypeError) as exc:
                raise ValidationError("Invalid observation timestamp") from exc
        filtered.append(row)
    # Pass windows are specific to their original observer.
    same_place = document.get("selection") == {"lat":origin[0], "lon":origin[1]}
    return {**document, "selection":{"lat":origin[0],"lon":origin[1]}, "radius_km":radius_km,
            "records":filtered, "matching_count":len(filtered), "passes":document.get("passes",[]) if same_place else []}


def earthquake_records(data, received):
    if not isinstance(data,dict) or not isinstance(data.get("features"),list):
        raise ValidationError("Invalid USGS feed")
    records = []
    for item in data["features"]:
        geom, prop = item["geometry"], item["properties"]
        if geom.get("type") != "Point":
            raise ValidationError("Invalid USGS geometry")
        lon, lat, *depth = geom["coordinates"]
        coordinates(lat, lon)
        mag = prop.get("mag")
        if mag is None or (type(mag) in (int,float) and mag < 2.5):
            continue
        if type(mag) not in (int,float) or not math.isfinite(mag) or mag > 10:
            raise ValidationError("Invalid USGS magnitude")
        stamp = prop.get("time")
        observed = datetime.fromtimestamp(stamp/1000, timezone.utc) if stamp is not None else None
        uid = item.get("id")
        if not uid:
            raise ValidationError("USGS event identifier unavailable")
        records.append(dict(id="earthquakes:"+uid, category="earthquakes", label=prop.get("place") or uid,
            latitude=lat, longitude=lon, observed_at=observed.isoformat() if observed else None, valid_at=None,
            retrieved_at=received.isoformat(), provider="USGS", source="https://earthquake.usgs.gov/earthquakes/eventpage/"+uid,
            kind="REPORTED", verification="provider-reported", metadata={},
            freshness=("unknown" if observed is None else "older-than-24h" if (received-observed).total_seconds()>86400 else "within-24h"),
            measurements={"magnitude":{"value":mag,"unit":"magnitude"},
                          "depth":{"value":depth[0],"unit":"km"} if depth and depth[0] is not None else None}))
    return records


def query(source, origin, radius_km=None, satellite="25544"):
    coordinates(*origin)
    if source == "aviation":
        rows = json.loads(ASSETS.joinpath("aviation.json").read_text("utf-8"))
    elif source == "earthquakes":
        req = Request(USGS, headers={"User-Agent":"FieldIntelligenceConsole", "Accept":"application/geo+json"})
        with urlopen(req, timeout=15) as response:
            data = response.read(4*1024*1024+1)
        if len(data)>4*1024*1024:
            raise ValidationError("USGS response exceeds size limit")
        rows = earthquake_records(json.loads(data), datetime.now(timezone.utc))
    else:
        catalog = json.loads(ASSETS.joinpath("sources.json").read_text("utf-8"))["feeds"]
        if source not in catalog:
            raise ValidationError("Unknown Earth feed")
        node = shutil.which("node")
        if not node:
            raise ValidationError("This adapter needs Node 22+ on PATH. Offline exports and the native feeds work with Python alone.")
        try:
            process = subprocess.run([node, str(ASSETS.joinpath("adapter.cjs")), source,
                str(origin[0]), str(origin[1]), satellite], capture_output=True, timeout=25)
        except subprocess.TimeoutExpired as exc:
            raise ValidationError("Earth provider timed out") from exc
        if process.returncode:
            raise ValidationError("Earth provider unavailable (UNKNOWN). Verify Node 22+, connectivity and regional coverage.")
        document = json.loads(process.stdout)
        return select(document, origin, radius_km)
    feed = json.loads(ASSETS.joinpath("sources.json").read_text("utf-8"))["feeds"][source]
    context = {key:feed[key] for key in ("description","coverage","limitations")}
    return select({"schema":"fic.earth-keypoints","version":1,"category":source,
                   "checked_at":datetime.now(timezone.utc).isoformat(),
                   "records":[{**row, **context} for row in rows]},origin,radius_km)
