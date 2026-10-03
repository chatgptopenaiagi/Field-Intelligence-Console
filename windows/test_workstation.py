import json
from importlib.resources import files
import tempfile
import threading
import time
import tkinter as tk
import unittest
from unittest.mock import patch
from pathlib import Path
from fic_windows import Workstation, coordinates, external
from geocoding_dialog import GeocodingDialog


class WorkstationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="FIC-synthetic-unit-")
        self.root = tk.Tk()
        self.app = Workstation(self.root, Path(self.directory.name))
        self.entity = json.loads(files("field_intelligence_console").joinpath("fixtures", "network-v1.json").read_text())["entities"][0]
        self.app.store.save_entity(self.entity)
        self.app.refresh_network()

    def tearDown(self):
        self.app.close()
        self.directory.cleanup()

    def pump(self, predicate):
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            self.root.update()
            if predicate():
                return
            time.sleep(0.01)
        self.fail("Bounded UI callback did not finish")

    def test_coordinate_unknown_zero_and_nonfinite(self):
        self.assertEqual((None, None), coordinates("", ""))
        self.assertEqual((0, 0), coordinates("0", "0"))
        for lat, lon in (("nan", "0"), ("0", "inf"), ("91", "0"), ("0", "181"), ("", "1")):
            with self.assertRaises(ValueError):
                coordinates(lat, lon)

    def test_external_action_allowlist(self):
        with patch("fic_windows.webbrowser.open", return_value=True) as browser:
            with self.assertRaises(ValueError):
                external("file:///untrusted")
            self.assertEqual(0, browser.call_count)
            external("https://www.openstreetmap.org/?mlat=0&mlon=0")
            self.assertEqual(1, browser.call_count)

    def test_edit_address_invalidates_derived_point(self):
        self.app.edit_contact(self.entity)
        self.app.editor_vars["street"].set("Changed Synthetic Street")
        self.app.save_contact_button.invoke()
        saved = self.app.store.get_entity(self.entity["id"])
        self.assertEqual("Changed Synthetic Street", saved["addresses"][0]["street"])
        self.assertEqual(self.entity["addresses"][0]["original"], saved["addresses"][0]["original"])
        self.assertIsNone(saved["associations"][0]["point"])
        self.assertEqual("unresolved", saved["associations"][0]["geocode_status"])

    def test_compact_editor_keyboard_focus_and_save(self):
        self.app.edit_contact()
        self.app.editor_window.geometry("900x500")
        self.root.update()
        self.app.editor_window.focus_force()
        self.root.update()
        self.assertLessEqual(
            self.app.save_contact_button.winfo_rooty() + self.app.save_contact_button.winfo_height(),
            self.app.editor_window.winfo_rooty() + self.app.editor_window.winfo_height())
        self.app.editor_window.event_generate("<Alt-n>")
        self.root.update()
        self.app.display_name_entry.delete(0, "end")
        self.app.display_name_entry.insert(0, "Synthetic keyboard contact")
        self.app.display_name_entry.event_generate("<Control-Return>")
        self.root.update()
        saved = self.app.store.list_entities("Synthetic keyboard contact")
        self.assertEqual(1, len(saved))
        self.assertIsNone(self.app.editor_window)

    def test_geocoding_requires_consent_then_candidate_selection(self):
        class Fake:
            calls = []
            def lookup(self, address, key, *, consent=False, cancel=None):
                self.calls.append((address, key, consent))
                return [{"formatted": "Synthetic derived address", "latitude": 0, "longitude": 0,
                         "precision": "street", "provider": "OpenCage", "resolved_at": "2026-10-03T00:00:00Z",
                         "attribution": "Synthetic provider fixture"}]
        fake = Fake()
        dialog = GeocodingDialog(self.app, self.entity, fake)
        self.assertFalse(fake.calls)
        dialog.addresses.selection_set(self.entity["addresses"][0]["id"])
        dialog.key.set("synthetic-test-key")
        with patch("geocoding_dialog.messagebox.askyesno", return_value=False):
            dialog.send_button.invoke()
        self.assertFalse(fake.calls)
        before = len(self.entity["associations"])
        with patch("geocoding_dialog.messagebox.askyesno", return_value=True):
            dialog.send_button.invoke()
        self.pump(lambda: bool(dialog.candidates))
        self.assertEqual("", dialog.key.get())
        self.assertEqual(1, len(fake.calls))
        self.assertTrue(fake.calls[0][2])
        self.assertEqual(self.entity["addresses"][0]["original"], fake.calls[0][0])
        self.assertEqual(before, len(self.app.store.get_entity(self.entity["id"])["associations"]))
        dialog.matches.selection_set("0")
        dialog.save_button.invoke()
        saved = self.app.store.get_entity(self.entity["id"])
        self.assertEqual(before + 1, len(saved["associations"]))
        self.assertEqual("external_provider", saved["associations"][-1]["source"])
        self.assertEqual("saved", saved["associations"][-1]["temporal_status"])

    def test_cancel_discards_late_geocoder_result(self):
        entered = threading.Event()
        class Fake:
            def lookup(self, address, key, *, consent=False, cancel=None):
                entered.set()
                cancel.wait(1)
                return [{"formatted": "Synthetic late result", "latitude": 0, "longitude": 0,
                         "precision": "street", "provider": "OpenCage", "resolved_at": "2026-10-03T00:00:00Z"}]
        dialog = GeocodingDialog(self.app, self.entity, Fake())
        dialog.addresses.selection_set(self.entity["addresses"][0]["id"])
        dialog.key.set("synthetic-test-key")
        with patch("geocoding_dialog.messagebox.askyesno", return_value=True):
            dialog.send_button.invoke()
        self.assertTrue(entered.wait(1))
        dialog.cancel()
        self.pump(lambda: dialog.state.get().startswith("Cancelled."))
        self.assertFalse(dialog.candidates)
        self.assertEqual(self.entity, self.app.store.get_entity(self.entity["id"]))
        dialog.close()


if __name__ == "__main__":
    unittest.main()
