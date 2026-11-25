from datetime import datetime, timezone
import json
import unittest
from unittest.mock import patch

from field_intelligence_console import earth
from field_intelligence_console.models import ValidationError


class EarthTests(unittest.TestCase):
    def test_archive_and_export(self):
        doc = earth.query("aviation", (21.7, -85.75), 1)
        self.assertEqual(len(doc["records"]), 1)
        row = doc["records"][0]
        self.assertEqual(row["kind"], "HISTORICAL")
        self.assertEqual(row["measurements"]["geopotential_height"]["unit"], "m")
        self.assertIsNone(row["metadata"]["speed"])
        self.assertEqual(earth.select(doc, (0, 0), 0)["records"], [])
        doc["records"].append(row)
        with self.assertRaises(ValidationError):
            earth.select(doc, (0, 0))

    def test_selected_point_and_passes(self):
        self.assertLess(earth.distance_km((0, 179.9), (0, -179.9)), 23)
        for value in (91, float("nan"), None, False):
            with self.assertRaises(ValidationError):
                earth.coordinates(value, 0)
        doc = {"schema":"fic.earth-keypoints","version":1,"records":[],
               "selection":{"lat":1,"lon":2},"passes":[{"first_sample":"synthetic"}]}
        self.assertEqual(earth.select(doc,(1,2))["passes"], doc["passes"])
        self.assertEqual(earth.select(doc,(1,3))["passes"], [])

    def test_reported_quake(self):
        feature = {"id":"test","properties":{"mag":3,"time":0,"place":"Synthetic"},
                   "geometry":{"type":"Point","coordinates":[13,48,None]}}
        rows = earth.earthquake_records({"features":[feature]},datetime.now(timezone.utc))
        self.assertEqual((rows[0]["latitude"], rows[0]["longitude"]), (48,13))
        self.assertEqual(rows[0]["freshness"], "older-than-24h")
        self.assertIsNone(rows[0]["measurements"]["depth"])
        doc = {"schema":"fic.earth-keypoints", "version":1, "records":rows}
        self.assertEqual(earth.select(doc,(48,13),min_magnitude=3)["matching_count"],1)
        self.assertEqual(earth.select(doc,(48,13),min_magnitude=4)["records"],[])
        self.assertEqual(earth.select(doc,(48,13),since=datetime.now(timezone.utc))["records"],[])
        feature["geometry"]["coordinates"] = [999,48,None]
        with self.assertRaises(ValidationError):
            earth.earthquake_records({"features":[feature]},datetime.now(timezone.utc))

    def test_optional_runtime_and_catalog(self):
        with patch.object(earth.shutil, "which", return_value=None), self.assertRaises(ValidationError):
            earth.query("satellites", (0, 0))
        catalog=json.loads(earth.ASSETS.joinpath("sources.json").read_text("utf-8"))
        self.assertFalse(catalog["key_required"])
        self.assertIn("lon=0",earth.client_url(0,0))
