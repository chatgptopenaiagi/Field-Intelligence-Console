import unittest
from field_intelligence_console.regional import emergency_services, interior_country


class RegionalDataTest(unittest.TestCase):
    def test_interior_and_border(self):
        for coords, expected in [((48.21,16.37),"AT"),((52.52,13.40),"DE"),
                                 ((44.43,26.10),"RO"),((50.85,4.35),"BE"),
                                 ((48.14,17.10),None),((0,0),None)]:
            with self.subTest(coords=coords):
                self.assertEqual(interior_country(*coords), expected)
        with self.assertRaises(ValueError):
            interior_country(float("nan"), 2)

    def test_national_records_only(self):
        self.assertEqual(emergency_services("ZZ"), [])
        self.assertEqual(emergency_services(""), [])
        for iso in ("AT","RO","DE","BE"):
            for record in emergency_services(iso):
                self.assertEqual(record["country"],iso)
                self.assertEqual(record["scope"],"national")
                self.assertTrue(record["source"].startswith("https://"))
                self.assertEqual(record["status"],"verified")
        self.assertEqual({r["number"] for r in emergency_services("RO")},{"112"})
        self.assertNotIn("133",[r["number"] for r in emergency_services("DE")])
