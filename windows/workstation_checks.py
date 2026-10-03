"""Synthetic package and actual Tk-widget checks; receipts contain counts, not records."""
from importlib.resources import files
import json
import platform
import sqlite3
import ssl
import socket
import sys
import tkinter as tk
from unittest.mock import patch
from field_intelligence_console import Store, parse_import, export_preview, validate_network, validate_report


def fixture(name):
    return files("field_intelligence_console").joinpath("fixtures", name).read_bytes()


def run_checks(args):
    from fic_windows import Workstation, atomic_write
    args.profile.mkdir(parents=True, exist_ok=True)
    marker = args.profile / "FIC-SYNTHETIC-TEST-PROFILE"
    if any(args.profile.iterdir()) and not marker.exists():
        raise ValueError("Test mode refuses a nonempty profile without its synthetic-test marker.")
    marker.write_text("Synthetic tests only; no personal data.\n", encoding="utf-8")
    checks = []
    app = root = None
    receipt = {"status": "FAIL", "mode": "ui" if args.ui_test else "self-test",
               "frozen": bool(getattr(sys, "frozen", False)), "checks": checks,
               "runtime": {"python": platform.python_version(), "architecture": platform.machine(),
                           "openssl": ssl.OPENSSL_VERSION, "sqlite": sqlite3.sqlite_version}}
    def check(name, condition):
        if not condition:
            raise AssertionError(name)
        checks.append(name)
    try:
        with patch.object(socket.socket, "connect", side_effect=AssertionError("Unexpected outbound connection")):
            with Store(args.profile / "diagnostics.sqlite3") as store:
                preview = parse_import(fixture("contacts.vcf"), "vcf", source_name="Synthetic shared fixture")
                check("VCF Unicode and multiple values", preview.valid_count == 2 and len(preview.entities[0]["phones"]) == 2 and len(preview.entities[0]["addresses"]) == 2)
                before = len(store.list_entities())
                store.preview_counts(preview)
                check("Core preview is read-only", len(store.list_entities()) == before)
                store.import_preview(preview, [e["id"] for e in preview.entities])
                check("Repeated source import is idempotent", store.import_preview(preview, [e["id"] for e in preview.entities])["inserted"] == 0)
                for fmt in ("json", "vcf", "csv"):
                    exported = export_preview(store.list_entities(), fmt)
                    check(f"{fmt} export and parse", parse_import(exported.data, fmt).valid_count == 2)
                validate_report(json.loads(fixture("field-report-v1.json")))
                check("Bundled FieldReport fixture validates", True)
            if args.ui_test:
                root = tk.Tk()
                receipt["runtime"].update(tcl=str(root.tk.call("info", "patchlevel")), tk=str(root.tk.call("package", "require", "Tk")))
                app = Workstation(root, args.profile)
                root.update()
                check("Actual Tk window rendered", root.winfo_viewable() == 1)
                with patch("fic_windows.messagebox.askyesno", return_value=True), patch("fic_windows.messagebox.showerror", side_effect=lambda title, text, **_: (_ for _ in ()).throw(AssertionError(title + ": " + text))):
                    path = args.profile / "synthetic-contacts.vcf"
                    atomic_write(path, fixture("contacts.vcf"))
                    app.tabs.select(app.network_tab)
                    start_count = len(app.store.list_entities())
                    with patch("fic_windows.filedialog.askopenfilename", return_value=str(path)):
                        app.import_button.invoke()
                    root.update()
                    check("UI import preview rendered without mutation", app.preview_window.winfo_viewable() and len(app.store.list_entities()) == start_count)
                    app.cancel_import_button.invoke()
                    check("UI cancel leaves store unchanged", len(app.store.list_entities()) == start_count)
                    app.prepare_import(path, "vcf")
                    app.confirm_import_button.invoke()
                    imported_count = len(app.store.list_entities())
                    check("UI confirm persists contacts", imported_count >= 2)
                    app.prepare_import(path, "vcf")
                    app.confirm_import_button.invoke()
                    check("UI repeated import has no duplication", len(app.store.list_entities()) == imported_count)
                    app.network_search.set("Synthetic Organisation")
                    check("UI search filters organization", len(app.network_tree.get_children()) >= 1)
                    app.network_search.set("")
                    first = app.network_tree.get_children()[0]
                    app.network_tree.selection_set(first)
                    overview = str(app.network_tree.item(first, "values"))
                    check("Overview excludes phones and notes", "+1-202" not in overview and "Saved association only" not in overview)
                    app.edit_selected()
                    root.update()
                    app.editor_vars["role"].set("Synthetic edited role")
                    app.save_contact_button.invoke()
                    check("UI edit preserved", app.store.get_entity(first)["role"] == "Synthetic edited role")
                    app.tabs.select(app.place_tab)
                    for key, value in {"latitude": "0", "longitude": "0", "locality": "Example City", "country": "Example Country", "street": "1 Example Street"}.items():
                        app.place_vars[key].set(value)
                    app.save_place_button.invoke()
                    app.tabs.select(app.network_tab)
                    app.scope.set("city")
                    app.query_button.invoke()
                    check("UI qualified city query", app.last_query["state"] == "MATCHES_FOUND")
                    check("Saved association disclaimer visible", "Saved" in app.query_state.get() or "saved" in app.query_state.get())
                    app.refresh_network()
                    app.network_tree.selection_set(app.network_tree.get_children())
                    for fmt in ("json", "vcf", "csv"):
                        output = args.profile / ("synthetic-export." + fmt)
                        app.export_format.set(fmt)
                        with patch("fic_windows.filedialog.asksaveasfilename", return_value=str(output)):
                            app.export_button.invoke()
                        check(f"UI deliberate {fmt} export", output.exists() and parse_import(output.read_bytes(), fmt).valid_count == imported_count)
                    canonical = json.loads((args.profile / "synthetic-export.json").read_bytes())
                    validate_network(canonical)
                    check("Canonical source ancestry retained", all(e["ancestry"] for e in canonical["entities"]))
                    report_path = args.profile / "synthetic-report.json"
                    atomic_write(report_path, fixture("field-report-v1.json"))
                    app.import_report(report_path)
                    report_id = app.report_tree.get_children()[0]
                    app.report_tree.selection_set(report_id)
                    app.show_report()
                    check("UI report shows provider state and observation", "Provider results" in app.report_detail.get("1.0", "end"))
                    app.use_report_place()
                    check("Report location remains labeled", app.place_origin == "Report-derived place")
                    app.theme.set("High contrast")
                    app.text_size.set("14")
                    app.apply_theme()
                    app.tabs.select(app.network_tab)
                    app.refresh_network()
                    root.update()
                count = len(app.store.list_entities())
                app.store.close()
                app.store = Store(args.profile / "fic-workstation.sqlite3")
                check("Database reopen preserves UI imports", len(app.store.list_entities()) == count)
                check("Preferences persist", json.loads(app.prefs_file.read_text())["theme"] == "High contrast")
                if args.interop_input:
                    # Separate isolated store prevents synthetic fixture edits from hiding ID collisions.
                    app.store.close()
                    app.store = Store(args.profile / "interop.sqlite3")
                    app.refresh_network()
                    original = json.loads(args.interop_input.read_bytes())
                    validate_network(original)
                    app.prepare_import(args.interop_input, "json")
                    app.confirm_import_button.invoke()
                    app.network_tree.selection_set([entity["id"] for entity in original["entities"]])
                    app.export_format.set("json")
                    with patch("fic_windows.messagebox.askyesno", return_value=True), patch("fic_windows.filedialog.asksaveasfilename", return_value=str(args.interop_output)):
                        app.export_button.invoke()
                    returned = json.loads(args.interop_output.read_bytes())
                    validate_network(returned)
                    check("External canonical input through packaged UI round-trips entities exactly",
                          sorted(original["entities"], key=lambda e: e["id"]) == sorted(returned["entities"], key=lambda e: e["id"]))
                if args.interop_report_input:
                    original = json.loads(args.interop_report_input.read_bytes())
                    validate_report(original)
                    with patch("fic_windows.messagebox.askyesno", return_value=True), patch("fic_windows.filedialog.asksaveasfilename", return_value=str(args.interop_report_output)):
                        app.import_report(args.interop_report_input)
                        app.report_tree.selection_set(original["id"])
                        app.export_report()
                    returned = json.loads(args.interop_report_output.read_bytes())
                    validate_report(returned)
                    check("External report input through packaged UI round-trips every field", original == returned)
            checks.append("No socket connection attempted during offline workflows")
        receipt["status"] = "PASS"
    except Exception as exc:
        receipt["failure_type"] = type(exc).__name__
        receipt["failure_check"] = str(exc)[:180] if isinstance(exc, AssertionError) else "Operation failed; inspect isolated development log"
    finally:
        receipt["check_count"] = len(checks)
        destination = args.evidence or args.profile / "test-receipt.json"
        atomic_write(destination, json.dumps(receipt, indent=2).encode("utf-8"))
    if app and args.stay_open and receipt["status"] == "PASS":
        app.status.set("Synthetic UI checks passed. Native interaction inspection; synthetic data only.")
        root.mainloop()
    elif app:
        app.close()
    elif root:
        root.destroy()
    return 0 if receipt["status"] == "PASS" else 1
