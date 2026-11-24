"""Offline country estimates for explicitly selected coordinates and verified national numbers."""
from functools import lru_cache
from importlib.resources import files
import json
import math


@lru_cache
def _data(name):
    return json.loads(files(__package__).joinpath("regional_data", name).read_bytes())


def interior_country(latitude, longitude):
    """Conservative interior lookup. Missing cells require manual confirmation."""
    if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
        raise ValueError("Invalid coordinates")
    if latitude == 90 or longitude == 180:
        return None
    row = math.floor((latitude + 90) * 5)
    col = math.floor((longitude + 180) * 5)
    rows = _data("country-grid-v1.json")
    if row >= len(rows):
        return None
    for start, stop, code in rows[row]:
        if start <= col < stop:
            return code
    return None


def emergency_services(country):
    code = country.strip().upper()
    return [record.copy() for record in _data("emergency-v1.json")["records"]
            if record["country"] == code and record["status"] == "verified"
            and record["region"] is None and record["scope"] == "national"]
