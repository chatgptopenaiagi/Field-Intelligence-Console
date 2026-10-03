"""Inventory failures expose code locations, not private inputs or exception text."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release_inventory", Path(__file__).parents[1] / "release_inventory.py")
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


class InventoryDiagnostics(unittest.TestCase):
    def test_missing_metadata_identifies_stage_without_private_path(self):
        with tempfile.TemporaryDirectory() as directory:
            private_input = Path(directory) / "synthetic-private-input.xml"
            inventory.STAGE = "android"
            try:
                inventory.android_inventory(private_input, Path(directory))
            except OSError as error:
                diagnostic = inventory.failure_diagnostic(error)
            else:
                self.fail("Missing metadata must fail")
            self.assertEqual("android", diagnostic["stage"])
            self.assertEqual("FileNotFoundError", diagnostic["error_type"])
            self.assertTrue(any(frame["function"] == "android_inventory" for frame in diagnostic["code_locations"]))
            self.assertNotIn(directory, json.dumps(diagnostic))
            self.assertNotIn(private_input.name, json.dumps(diagnostic))

    def test_exception_payload_is_never_returned(self):
        diagnostic = inventory.failure_diagnostic(ValueError("synthetic-secret-value"))
        self.assertNotIn("synthetic-secret-value", json.dumps(diagnostic))
        self.assertEqual("FAIL", diagnostic["status"])


if __name__ == "__main__":
    unittest.main()
