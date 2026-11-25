import unittest
from collections import Counter
from field_intelligence_console import human_context as hc


class HumanContextTests(unittest.TestCase):
    def test_all_ids_and_translations(self):
        rows = hc.questions()
        self.assertEqual([q["id"] for q in rows], [f"Q{i:02}" for i in range(1, 36)])
        self.assertEqual(len(hc.categories()), 7)
        self.assertEqual(set(Counter(q["category"] for q in rows).values()), {5})
        for group in hc.categories():
            self.assertEqual(len(hc.questions(group["id"])), 5)
        for row in rows:
            self.assertEqual(set(row["languages"]), set(hc.LANGUAGES))
            self.assertEqual(len(set(row["question"].values())), 3)
            for lang in hc.LANGUAGES:
                self.assertTrue(row["title"][lang].strip())
                self.assertIn(row["question"][lang], hc.compose(row["id"], lang))
        with self.assertRaises(KeyError):
            hc.question("Q36")
        with self.assertRaises(ValueError):
            hc.compose(language="es")

    def test_disclosure_and_provenance(self):
        point = dict(origin="device", latitude=41.123456, longitude=12.789123,
                     accuracy=4.5, measured="2001-02-03T04:05:06Z", detected="RO", override="AT",
                     geocoded="Synthetic private street", area="Synthetic region", note="PRIVATE", contacts=["PRIVATE"])
        for mode in ("none", "region", "exact"):
            draft = hc.compose("Q08", "ro", point, disclosure=mode)
            self.assertNotIn("PRIVATE", draft)
            self.assertEqual("41.123456" in draft, mode == "exact")
            self.assertEqual(point["geocoded"] in draft, mode == "exact")
            self.assertEqual(point["area"] in draft, mode != "none")
            self.assertEqual("Țările diferă" in draft, mode != "none")
            self.assertNotIn("SMURD", draft)
        for country, term in (("RO", "SMURD"), ("DE", "Rettungsdienst"), ("AT", "Rettungsdienst")):
            text = hc.compose("Q08", "de", dict(point, detected=country, override=None), disclosure="region")
            self.assertIn(term, text)
        past = hc.compose("Q16", "en", dict(point, origin="historical"), disclosure="exact")
        self.assertIn("Historical location", past)
        self.assertIn(point["measured"], past)
        for origin, manual_origin in (("manual", False), ("historical", True)):
            text = hc.compose("Q16", context=dict(point, origin=origin, manual_origin=manual_origin), disclosure="exact")
            self.assertNotIn("4.5 m", text)
            self.assertNotIn(point["measured"], text)
        for lat in (None, True, 91, float("nan")):
            text = hc.compose(context=dict(point, latitude=lat), disclosure="exact")
            self.assertIn("Latitude, longitude · Unknown", text)
            self.assertNotIn("private street", text)
        text = hc.compose(context=dict(point, accuracy=-1, measured="yesterday"), disclosure="exact")
        self.assertIn("Reported accuracy · Unknown", text)
        self.assertIn("Measurement time · Unknown", text)

    def test_explicit_text_and_limits(self):
        sample = "Știință & 東京?\n" * 550
        draft = hc.compose("Q30", "ro", observation=sample, target_language="italiano",
                           earth_excerpt="USGS · synthetic · 2001-02-03T04:05:06Z")
        for value in (sample.strip(), "italiano", "USGS", "2001-02-03"):
            self.assertIn(value, draft)
        self.assertTrue(hc.reviewable(draft))
        self.assertFalse(hc.reviewable("ă" * 32769))
        self.assertFalse(hc.reviewable(" "))
        self.assertIn("Ask the user", hc.compose("Q30"))
        self.assertIn("Synthetic free question?", hc.compose(custom="Synthetic free question?"))
