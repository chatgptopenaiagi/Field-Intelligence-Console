"""Inventory failures expose code locations, not private inputs or exception text."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

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

    def test_only_current_package_graph_is_inventoried(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            folder = root / "example.synthetic" / "runtime" / "1.0" / "hash"
            folder.mkdir(parents=True)
            with zipfile.ZipFile(folder / "runtime-1.0.jar", "w") as archive:
                archive.writestr("META-INF/LICENSE", "Synthetic test notice")
            (folder / "runtime-1.0.pom").write_text(
                "<project><licenses><license><name>MIT</name></license></licenses></project>", "utf-8")
            metadata = root / "debug-artifact-dependencies.xml"
            metadata.write_text('<dependencies><compile roots="not:packaged:1@jar"/>'
                                '<package roots="example.synthetic:runtime:1.0@jar">'
                                '<dependency name="example.synthetic:runtime:1.0@jar"/>'
                                '</package></dependencies>', "utf-8")
            components, notices, unresolved = inventory.android_inventory(metadata, root)
            self.assertEqual(["runtime"], [item["name"] for item in components])
            self.assertEqual([], unresolved)
            self.assertTrue(any("Synthetic test notice" in notice for notice in notices))
            self.assertEqual(inventory.sha(folder / "runtime-1.0.jar"), components[0]["hashes"][0]["content"])
            # Missing current metadata must fail even if an obsolete model exists.
            metadata.rename(root / "debug-mainArtifact-dependencies.xml")
            with self.assertRaises(FileNotFoundError):
                inventory.android_inventory(metadata, root)


if __name__ == "__main__":
    unittest.main()
