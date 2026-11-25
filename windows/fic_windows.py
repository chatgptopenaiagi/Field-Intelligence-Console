"""Native offline FIC workstation; no implicit external actions."""
from __future__ import annotations
import argparse
import copy
import json
import math
import os
import sys
from pathlib import Path
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import tkinter.font as tkfont
from urllib.parse import quote
import uuid
import webbrowser
from field_intelligence_console import (
    Store, ValidationError, export_preview, make_report, new_entity,
    parse_import, query_network, validate_report, now_iso,
)

VERSION = "4.0.0-beta.11.dev1"
MAX_BYTES = 5 * 1024 * 1024
SCOPES = ("street", "radius", "neighborhood", "city", "region", "country", "building")
ADDRESS_FIELDS = ("street", "neighborhood", "locality", "region", "postal_code", "country")


def read_bounded(path):
    with Path(path).open("rb") as stream:
        data = stream.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("File exceeds the 5 MiB import limit.")
    return data


def atomic_write(path, data):
    path = Path(path)
    temporary = path.with_name(path.name + ".fic-" + uuid.uuid4().hex + ".tmp")
    try:
        with temporary.open("xb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def coordinates(lat, lon):
    if not str(lat).strip() and not str(lon).strip():
        return None, None
    lat, lon = float(lat), float(lon)
    if not math.isfinite(lat) or not math.isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
        raise ValueError("Coordinates must be finite and within geographic bounds.")
    return lat, lon


def external(uri):
    if not uri.startswith(("https://www.openstreetmap.org/", "mailto:", "tel:")):
        raise ValueError("Unsupported external action.")
    if not webbrowser.open(uri, new=2):
        raise ValueError("No system application accepted this action.")


def source_label(entity):
    ancestry = entity.get("ancestry", [])
    if not ancestry:
        return "User-created; date unknown"
    source = ancestry[0]
    updated = "source updated " + source["updated_at"] if source.get("updated_at") else "source age unknown"
    return f"{source.get('format', 'unknown')}; imported {source.get('imported_at') or 'unknown'}; {updated}"


class Workstation:
    def __init__(self, root, profile):
        self.root, self.profile = root, Path(profile)
        self.profile.mkdir(parents=True, exist_ok=True)
        self.store = Store(self.profile / "fic-workstation.sqlite3")
        self.prefs_file = self.profile / "preferences.json"
        try:
            prefs = json.loads(self.prefs_file.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            prefs = {}
        self.place = prefs.get("place", {})
        self.place_origin = prefs.get("place_origin", "No place selected")
        self.analysis_language = prefs.get("analysis_language", "en")
        self.preview = self.preview_window = self.editor_window = None
        self.status = tk.StringVar(value="Offline ready. Nothing is transmitted automatically.")
        root.title(f"Field Intelligence Console {VERSION}")
        root.geometry("1120x780")
        root.minsize(800, 560)
        root.protocol("WM_DELETE_WINDOW", self.close)
        ttk.Label(root, text="FIELD INTELLIGENCE CONSOLE · BETA", font=("Segoe UI", 18, "bold")).pack(anchor="w", padx=16, pady=(12, 0))
        ttk.Label(root, text=f"ONE TAP. INSTANT AWARENESS.  |  {VERSION}").pack(anchor="w", padx=16, pady=6)
        self.tabs = ttk.Notebook(root)
        self.tabs.pack(fill="both", expand=True, padx=12)
        self.reports_tab, self.network_tab, self.place_tab, self.settings_tab = [ttk.Frame(self.tabs, padding=10) for _ in range(4)]
        for frame, title in zip((self.reports_tab, self.network_tab, self.place_tab, self.settings_tab),
                                ("Reports / History", "KNOWN NETWORK", "Selected place", "Settings / Diagnostics")):
            self.tabs.add(frame, text=title)
        ttk.Label(root, textvariable=self.status, wraplength=1000).pack(fill="x", padx=16, pady=8)
        self.build_reports()
        self.build_network()
        self.build_place(prefs)
        self.build_settings(prefs)
        self.apply_theme(False)
        self.refresh_reports()
        self.refresh_network()
        root.bind("<Control-f>", self.focus_search)
        root.bind("<Control-i>", lambda _: self.select_import())
        root.bind("<Control-n>", lambda _: self.edit_contact())

    def guard(self, name, action):
        try:
            return action()
        except Exception as exc:
            safe = str(exc)[:300] if isinstance(exc, (ValueError, ValidationError)) else "Operation could not finish. Existing local records were retained."
            self.status.set(f"{name}: {safe}")
            messagebox.showerror(name, safe, parent=self.root)
            return None

    @staticmethod
    def button(parent, text, callback):
        button = ttk.Button(parent, text=text, command=callback)
        button.pack(side="left", padx=3, pady=3)
        return button

    @staticmethod
    def table(parent, columns, widths):
        frame = ttk.Frame(parent)
        frame.pack(fill="both", expand=True, pady=8)
        tree = ttk.Treeview(frame, columns=columns, show="headings", selectmode="extended", height=6)
        for name, width in zip(columns, widths):
            tree.heading(name, text=name)
            tree.column(name, width=width, minwidth=70)
        scroll = ttk.Scrollbar(frame, command=tree.yview)
        tree.configure(yscrollcommand=scroll.set)
        tree.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        return tree

    def focus_search(self, _=None):
        entry = self.network_search_entry if self.tabs.select() == str(self.network_tab) else self.report_search_entry
        entry.focus_set()
        entry.selection_range(0, "end")
        return "break"

    def build_reports(self):
        bar = ttk.Frame(self.reports_tab)
        bar.pack(fill="x")
        self.report_search = tk.StringVar()
        self.report_search_entry = ttk.Entry(bar, textvariable=self.report_search, width=25)
        self.report_search_entry.pack(side="left")
        self.report_search.trace_add("write", lambda *_: self.refresh_reports())
        self.button(bar, "Import report", self.select_report)
        self.button(bar, "New observation", self.new_report)
        self.button(bar, "Export selected", self.export_report)
        self.button(bar, "Delete selected", self.delete_reports)
        self.report_tree = self.table(self.reports_tab, ("Observed", "Tag", "Location source", "Observation"), (180, 90, 130, 450))
        self.report_tree.bind("<<TreeviewSelect>>", lambda _: self.show_report())
        self.report_tree.bind("<Return>", lambda _: self.use_report_place())
        self.report_detail = tk.Text(self.reports_tab, height=11, wrap="word", state="disabled")
        self.report_detail.pack(fill="x")
        bar = ttk.Frame(self.reports_tab)
        bar.pack(fill="x")
        self.button(bar, "Use report's place", self.use_report_place)
        self.button(bar, "Open selected place in map", self.open_map)

    def refresh_reports(self):
        if not hasattr(self, "report_tree"):
            return
        self.report_tree.delete(*self.report_tree.get_children())
        for report in self.store.list_reports(self.report_search.get()):
            self.report_tree.insert("", "end", iid=report["id"], values=(report["observed_at"], report["observation"].get("tag", ""),
                report["location"].get("source", "unknown"), report["observation"].get("note", "")[:120]))

    def selected_report(self):
        selected = self.report_tree.selection()
        return self.store.get_report(selected[0]) if selected else None

    def show_report(self):
        report = self.selected_report()
        if not report:
            return
        location = report["location"]
        text = [f"Observed: {report['observed_at']}", f"Location source: {location.get('source', 'unknown')} (no assumed desktop GPS)",
                f"Coordinates: {location.get('latitude')} / {location.get('longitude')}; accuracy: {location.get('accuracy_m')} m",
                f"Address: {location.get('address') or 'Unavailable'}; provenance: {location.get('address_source', 'unknown')}",
                f"Observation: {report['observation'].get('note', '')}", "Provider results:"]
        for provider in report.get("providers", []):
            text.append(f"  {provider.get('provider')}: {provider.get('state')} / {provider.get('freshness')}; fetched {provider.get('fetched_at') or 'unknown'}")
            if provider.get("data") is not None:
                text.append("  " + json.dumps(provider["data"], ensure_ascii=False)[:2000])
            if provider.get("error"):
                text.append("  Failure: " + str(provider["error"])[:500])
        if not report.get("providers"):
            text.append("  No provider results supplied.")
        if report.get("ai_advisory"):
            advisory = report["ai_advisory"]
            text.append(f"AI ADVISORY — {advisory.get('provider')}: {advisory.get('text', '')}")
        self.report_detail.configure(state="normal")
        self.report_detail.delete("1.0", "end")
        self.report_detail.insert("1.0", "\n".join(text))
        self.report_detail.configure(state="disabled")

    def select_report(self):
        name = filedialog.askopenfilename(parent=self.root, title="Open FIC FieldReport", filetypes=[("FIC JSON", "*.json")])
        if name:
            self.guard("Import report", lambda: self.import_report(Path(name)))

    def import_report(self, path):
        report = json.loads(read_bounded(path))
        validate_report(report)
        if not messagebox.askyesno("Review report", f"One report observed {report['observed_at']}\nLocation source: {report['location'].get('source')}\nImport into the local library?", parent=self.root):
            return False
        self.store.save_report(report)
        self.refresh_reports()
        self.status.set("Report imported locally; no NETWORK contents included.")
        return True

    def new_report(self):
        dialog = tk.Toplevel(self.root)
        dialog.title("New local observation")
        dialog.transient(self.root)
        ttk.Label(dialog, text="Uses selected manual/report coordinates; missing location stays unknown.").pack(padx=12, pady=10)
        note = tk.Text(dialog, width=60, height=8, wrap="word")
        note.pack(padx=12)
        tag = tk.StringVar()
        ttk.Label(dialog, text="Tag").pack(anchor="w", padx=12)
        ttk.Entry(dialog, textvariable=tag).pack(fill="x", padx=12)
        def save():
            location = {"latitude": self.place.get("latitude"), "longitude": self.place.get("longitude"), "accuracy_m": None,
                        "source": "manual" if self.place_origin == "Manual place" else "report" if self.place else "unknown",
                        "address": None, "address_source": "unknown", "resolved_at": None}
            self.store.save_report(make_report(location=location, note=note.get("1.0", "end-1c"), tag=tag.get()))
            self.refresh_reports()
            dialog.destroy()
        ttk.Button(dialog, text="Save observation", command=lambda: self.guard("Save report", save)).pack(pady=10)

    def export_report(self):
        report = self.selected_report()
        if not report:
            self.status.set("Select one report to export.")
            return
        if messagebox.askyesno("Export preview", "Export this report, its location and observations?\nNETWORK details and counts are excluded.", parent=self.root):
            name = filedialog.asksaveasfilename(parent=self.root, defaultextension=".json", filetypes=[("FIC JSON", "*.json")])
            if name:
                self.guard("Export report", lambda: atomic_write(name, json.dumps(report, ensure_ascii=False, allow_nan=False, indent=2).encode("utf-8")))

    def delete_reports(self):
        selected = self.report_tree.selection()
        if selected and messagebox.askyesno("Delete reports", f"Delete {len(selected)} selected reports? This cannot be undone.", parent=self.root):
            for identifier in selected:
                self.store.delete_report(identifier)
            self.refresh_reports()

    def use_report_place(self):
        report = self.selected_report()
        if not report:
            self.status.set("Select a report first.")
            return
        self.place = {key: report["location"].get(key) for key in ("latitude", "longitude")}
        point = report["location"]
        self.place.update(accuracy=point.get("accuracy_m"), manual_origin=point.get("source") == "manual",
                          geocoded=point.get("address") if point.get("address_source") == "device_geocoder" else None,
                          measured=report.get("observed_at") if point.get("source") == "device" else None)
        self.place_origin = "Report-derived place"
        self.sync_place()
        self.save_preferences()
        self.tabs.select(self.place_tab)
        self.status.set("Report-derived place selected. Structured address fields are not guessed.")

    def build_network(self):
        ttk.Label(self.network_tab, text="People, organizations and contacts associated with this place.").pack(anchor="w")
        ttk.Label(self.network_tab, text="SAVED GEOGRAPHIC ASSOCIATION IS NOT CURRENT PHYSICAL PRESENCE.").pack(anchor="w")
        self.area_label = tk.StringVar(value=self.area_text())
        ttk.Label(self.network_tab, textvariable=self.area_label, wraplength=1000).pack(anchor="w")
        bar = ttk.Frame(self.network_tab)
        bar.pack(fill="x")
        self.network_search = tk.StringVar()
        self.network_search_entry = ttk.Entry(bar, textvariable=self.network_search, width=25)
        self.network_search_entry.pack(side="left")
        self.network_search.trace_add("write", lambda *_: self.refresh_network())
        self.import_button = self.button(bar, "Import VCF / CSV / JSON", self.select_import)
        self.add_button = self.button(bar, "Add contact", self.edit_contact)
        self.button(bar, "Open details / Edit", self.edit_selected)
        self.button(bar, "Delete selected", self.delete_contacts)
        bar = ttk.Frame(self.network_tab)
        bar.pack(fill="x")
        self.scope = tk.StringVar(value="city")
        ttk.Label(bar, text="Saved associations: ").pack(side="left")
        ttk.Combobox(bar, textvariable=self.scope, values=SCOPES, state="readonly", width=15).pack(side="left")
        self.query_button = self.button(bar, "Query selected place", self.run_query)
        self.button(bar, "Show all saved records", self.refresh_network)
        self.button(bar, "Optional address geocoding", self.open_geocoding)
        self.query_state = tk.StringVar(value="EMPTY STORE")
        ttk.Label(self.network_tab, textvariable=self.query_state, wraplength=1000).pack(anchor="w", pady=5)
        self.network_tree = self.table(self.network_tab, ("Name / Organization", "Kind", "Association / precision", "Source / age"), (260, 90, 320, 300))
        self.network_tree.bind("<Double-1>", lambda _: self.edit_selected())
        self.network_tree.bind("<Return>", lambda _: self.edit_selected())
        bar = ttk.Frame(self.network_tab)
        bar.pack(fill="x")
        self.export_format = tk.StringVar(value="json")
        ttk.Combobox(bar, textvariable=self.export_format, values=("json", "vcf", "csv"), state="readonly", width=8).pack(side="left")
        self.export_button = self.button(bar, "Preview export of selected records", self.export_contacts)
        ttk.Label(bar, text="Phones and private notes appear only in opened details.").pack(side="left", padx=8)

    def display_entities(self, entities):
        self.network_tree.delete(*self.network_tree.get_children())
        for entity in entities:
            labels = sorted({a.get("label", "unspecified") for a in entity.get("addresses", [])})
            associations = sorted({f"{a.get('basis', 'unknown')} / {(a.get('point') or {}).get('precision', 'text-only')}" for a in entity.get("associations", [])})
            evidence = ", ".join(labels) + ": " + ", ".join(associations) if associations else "No geographic association"
            name = entity["display_name"] + (f" / {entity['organization']}" if entity.get("organization") else "")
            self.network_tree.insert("", "end", iid=entity["id"], values=(name, entity.get("kind", "person"), evidence, source_label(entity)))

    def refresh_network(self):
        if not hasattr(self, "network_tree"):
            return
        entities = self.store.list_entities(self.network_search.get())
        self.display_entities(entities)
        self.query_state.set("EMPTY STORE — Import VCF, Import CSV or Add contact." if not self.store.list_entities()
                             else f"{len(entities)} saved records displayed. Search results are not physical presence.")

    def run_query(self):
        def query():
            radius = float(self.radius.get())
            if not math.isfinite(radius) or radius < 0:
                raise ValueError("Radius must be finite and nonnegative.")
            result = query_network(self.store.list_entities(self.network_search.get()), self.scope.get(), self.place, radius_m=radius)
            self.last_query = result
            self.display_entities(result["matches"])
            count = str(result["count"]) if result["state"] in ("MATCHES_FOUND", "NO_MATCHES", "EMPTY_STORE") else "count unavailable"
            self.query_state.set(f"{result['state']} — {count} saved associations; {result.get('excluded', 0)} records lack this scope's evidence. {result.get('explanation', '')} Place: {self.place_origin}.")
            return result
        result = self.guard("Geographic query", query)
        if result is None:
            self.query_state.set("QUERY FAILED — local records remain available.")

    def select_import(self):
        name = filedialog.askopenfilename(parent=self.root, title="Local contact import preview", filetypes=[("Contact files", "*.vcf *.csv *.json")])
        if name:
            self.guard("Import preview", lambda: self.prepare_import(Path(name), Path(name).suffix.lower().lstrip(".")))

    def prepare_import(self, path, fmt, mapping=None):
        self.pending_import = (path, fmt)
        preview = parse_import(read_bounded(path), fmt, source_name=Path(path).name, mapping=mapping)
        self.show_preview(preview)
        return preview

    def show_preview(self, preview):
        self.cancel_preview()
        self.preview = preview
        self.query_state.set("IMPORTING — preview only; no store mutation.")
        dialog = tk.Toplevel(self.root)
        self.preview_window = dialog
        dialog.title("Review contact import — confirm selected records")
        dialog.geometry("940x610")
        dialog.transient(self.root)
        dialog.protocol("WM_DELETE_WINDOW", self.cancel_preview)
        counts = self.store.preview_counts(preview)
        ttk.Label(dialog, text=f"Source: {preview.source_name}\nValid: {preview.valid_count}  Invalid: {preview.invalid_count}  Duplicate source records: {counts['duplicates']}\nSelect valid rows to import. Original file not retained; no geocoding or transmission.", wraplength=900).pack(anchor="w", padx=10, pady=8)
        if preview.format == "csv":
            bar = ttk.Frame(dialog)
            bar.pack(fill="x", padx=10)
            self.csv_mapping = tk.StringVar(value=", ".join(f"{name}={name}" for name in preview.columns))
            ttk.Label(bar, text="Mapping canonical=input: ").pack(side="left")
            ttk.Entry(bar, textvariable=self.csv_mapping).pack(side="left", fill="x", expand=True)
            self.button(bar, "Reparse", self.reparse_mapping)
            ttk.Label(dialog, text="Available columns: " + ", ".join(preview.columns), wraplength=900).pack(anchor="w", padx=10)
            ttk.Label(dialog, text="Canonical columns: display_name, kind, organization, role, phone, email, street, neighborhood, locality, region, postal_code, country, label, notes", wraplength=900).pack(anchor="w", padx=10)
        self.preview_tree = self.table(dialog, ("Name", "Kind", "Addresses", "Source"), (300, 90, 100, 300))
        for entity in preview.entities:
            self.preview_tree.insert("", "end", iid=entity["id"], values=(entity["display_name"], entity.get("kind"), len(entity.get("addresses", [])), source_label(entity)))
        self.preview_tree.selection_set(self.preview_tree.get_children())
        issues = [f"Record {issue.record}: {issue.code} — {issue.message}" for issue in preview.issues] + list(preview.warnings)
        ttk.Label(dialog, text="\n".join(issues[:12]) or "No parser diagnostics. Unknown properties are inert; external references are never fetched.", wraplength=900).pack(anchor="w", padx=10, pady=5)
        bar = ttk.Frame(dialog)
        bar.pack(fill="x", padx=10, pady=10)
        self.confirm_import_button = self.button(bar, "Confirm selected valid records", lambda: self.guard("Import", self.confirm_import))
        self.cancel_import_button = self.button(bar, "Cancel — store unchanged", self.cancel_preview)
        dialog.bind("<Escape>", lambda _: self.cancel_preview())

    def reparse_mapping(self):
        def reparse():
            mapping = {}
            for item in self.csv_mapping.get().split(","):
                if item.strip():
                    key, value = item.strip().split("=", 1)
                    mapping[key.strip()] = value.strip()
            self.prepare_import(*self.pending_import, mapping=mapping)
        self.guard("CSV mapping", reparse)

    def confirm_import(self):
        selected = list(self.preview_tree.selection())
        if not selected:
            raise ValueError("Select at least one valid record or cancel.")
        result = self.store.import_preview(self.preview, selected)
        self.cancel_preview()
        self.status.set(f"Atomic local import: {result['inserted']} inserted; {result['duplicates']} duplicates retained without overwriting.")
        return result

    def cancel_preview(self):
        if self.preview_window:
            self.preview_window.destroy()
        self.preview_window = self.preview = None
        self.refresh_network()

    def edit_selected(self):
        selected = self.network_tree.selection()
        if selected:
            self.edit_contact(self.store.get_entity(selected[0]))
        else:
            self.status.set("Select one contact to open private details.")

    def open_geocoding(self):
        selected = self.network_tree.selection()
        if not selected:
            self.status.set("Select one contact, then deliberately choose an address to disclose.")
            return
        def open_dialog():
            from geocoding_dialog import GeocodingDialog
            GeocodingDialog(self, self.store.get_entity(selected[0]))
        self.guard("Optional geocoding", open_dialog)

    def edit_contact(self, entity=None):
        if self.editor_window:
            self.editor_window.lift()
            return
        entity = copy.deepcopy(entity) if entity else new_entity("New contact")
        dialog = tk.Toplevel(self.root)
        self.editor_window = dialog
        dialog.title("Contact details — saved record, not current presence")
        dialog.geometry("920x740")
        dialog.transient(self.root)
        focus_job = None
        def close():
            if focus_job is not None:
                dialog.after_cancel(focus_job)
            dialog.destroy()
            self.editor_window = None
        dialog.protocol("WM_DELETE_WINDOW", close)
        tabs = ttk.Notebook(dialog)
        tabs.pack(fill="both", expand=True, padx=10, pady=10)
        basic_page, advanced = ttk.Frame(tabs), ttk.Frame(tabs, padding=10)
        canvas = tk.Canvas(basic_page, highlightthickness=0)
        scroll = ttk.Scrollbar(basic_page, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=scroll.set)
        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        basic = ttk.Frame(canvas, padding=10)
        window_id = canvas.create_window((0, 0), window=basic, anchor="nw")
        basic.bind("<Configure>", lambda _: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind("<Configure>", lambda event: canvas.itemconfigure(window_id, width=event.width))
        dialog.bind("<MouseWheel>", lambda event: canvas.yview_scroll(-int(event.delta / 120), "units"))
        tabs.add(basic_page, text="Contact / saved address")
        tabs.add(advanced, text="All values / provenance")
        address = (entity.get("addresses") or [{}])[0]
        values = {key: entity.get(key, "") for key in ("display_name", "organization", "role")}
        values.update(kind=entity.get("kind", "person"), phone=(entity.get("phones") or [{}])[0].get("value", ""),
                      email=(entity.get("emails") or [{}])[0].get("value", ""), label=address.get("label", "unspecified"))
        values.update({key: address.get(key, "") for key in ADDRESS_FIELDS})
        self.editor_vars = {}
        for row, (key, value) in enumerate(values.items()):
            label = "Display name (Alt+N)" if key == "display_name" else key.replace("_", " ").title()
            ttk.Label(basic, text=label).grid(row=row, column=0, sticky="w", pady=3)
            variable = tk.StringVar(value=value)
            self.editor_vars[key] = variable
            widget = ttk.Combobox(basic, textvariable=variable, values=("person", "organization"), state="readonly") if key == "kind" else ttk.Entry(basic, textvariable=variable, width=60)
            widget.grid(row=row, column=1, sticky="ew", pady=3)
            if key == "display_name":
                self.display_name_entry = widget
        basic.columnconfigure(1, weight=1)
        ttk.Label(basic, text="Private notes").grid(row=len(values), column=0, sticky="nw")
        self.editor_notes = tk.Text(basic, height=4, wrap="word")
        self.editor_notes.insert("1.0", entity.get("notes", ""))
        self.editor_notes.grid(row=len(values), column=1, sticky="ew")
        ttk.Label(advanced, text="Canonical entity JSON supports multiple typed phones/addresses, manual points and ancestry.\nSaving this tab uses its validated JSON. Imported text never authorizes external actions.", wraplength=840).pack(anchor="w")
        self.editor_json = tk.Text(advanced, wrap="none")
        self.editor_json.insert("1.0", json.dumps(entity, ensure_ascii=False, indent=2))
        self.editor_json.pack(fill="both", expand=True)
        precision_bar = ttk.Frame(advanced)
        precision_bar.pack(fill="x")
        ttk.Label(precision_bar, text="Manual saved-point precision (choose only evidence you have): ").pack(side="left")
        self.manual_precision = tk.StringVar(value="approximate")
        ttk.Combobox(precision_bar, textvariable=self.manual_precision, values=("approximate", "building", "street", "locality", "region", "country"), state="readonly").pack(side="left")
        def save():
            if tabs.select() == str(advanced):
                edited = json.loads(self.editor_json.get("1.0", "end-1c"))
                if edited.get("id") != entity["id"]:
                    raise ValueError("Retain this record's stable ID when editing.")
            else:
                edited = copy.deepcopy(entity)
                for key in ("display_name", "kind", "organization", "role"):
                    edited[key] = self.editor_vars[key].get().strip()
                if not edited["display_name"]:
                    raise ValueError("A display name is required.")
                edited["notes"] = self.editor_notes.get("1.0", "end-1c")
                for key, plural in (("phone", "phones"), ("email", "emails")):
                    value, old = self.editor_vars[key].get().strip(), edited.get(plural, [])
                    if value:
                        edited[plural] = [{**(old[0] if old else {}), "value": value, "normalized": value.casefold() if key == "email" else "", "label": (old[0].get("label", "unspecified") if old else "unspecified")}] + old[1:]
                    elif old:
                        edited[plural] = old[1:]
                fields = {key: self.editor_vars[key].get().strip() for key in ADDRESS_FIELDS}
                if any(fields.values()):
                    new_address = {**address, "id": address.get("id", str(uuid.uuid4())), "original": address.get("original") or ", ".join(v for v in fields.values() if v),
                                   "label": self.editor_vars["label"].get() or "unspecified", "po_box": address.get("po_box", ""), "extended": address.get("extended", ""), **fields}
                    edited["addresses"] = [new_address] + edited.get("addresses", [])[1:]
                    if any(fields[key] != address.get(key, "") for key in ADDRESS_FIELDS):
                        # A derived point for the old address cannot silently survive a correction.
                        edited.setdefault("corrections", []).append({"at": now_iso(), "previous_address": address})
                        for association in edited.get("associations", []):
                            if association.get("address_id") == new_address["id"]:
                                association.update(point=None, geocode_status="unresolved", provider=None, resolved_at=None, normalized_address=None)
                    if not any(a.get("address_id") == new_address["id"] for a in edited.get("associations", [])):
                        edited.setdefault("associations", []).append(self.association(address_id=new_address["id"]))
                elif address:
                    edited["addresses"] = edited.get("addresses", [])[1:]
                    edited["associations"] = [a for a in edited.get("associations", []) if a.get("address_id") != address["id"]]
                changed = {key: entity.get(key) for key in ("display_name", "kind", "organization", "role", "notes", "phones", "emails")
                           if entity.get(key) != edited.get(key)}
                edited.setdefault("corrections", []).append({"source": "user", "at": now_iso(), "action": "desktop edit", "previous_values": changed})
            self.store.save_entity(edited)
            close()
            self.refresh_network()
            self.status.set("Contact saved locally. No address was transmitted.")
        bar = ttk.Frame(dialog)
        # Reserve the action row before the expanding notebook so it remains
        # reachable on compact desktops and at larger text sizes.
        bar.pack(side="bottom", fill="x", padx=10, pady=8, before=tabs)
        self.save_contact_button = self.button(bar, "Save (Ctrl+Enter)", lambda: self.guard("Save contact", save))
        self.button(bar, "Cancel", close)
        self.button(bar, "Phone app", lambda: self.contact_action("tel", self.editor_vars["phone"].get()))
        self.button(bar, "Email app", lambda: self.contact_action("mailto", self.editor_vars["email"].get()))
        def add_point():
            lat, lon = self.place.get("latitude"), self.place.get("longitude")
            if lat is None or lon is None:
                raise ValueError("Select valid manual/report coordinates first.")
            if not messagebox.askyesno("Manual association", f"Save this point with {self.manual_precision.get()} precision?\nChoose precision only from your own evidence. This is not current location or verified residence.", parent=dialog):
                return
            value = json.loads(self.editor_json.get("1.0", "end-1c"))
            value.setdefault("associations", []).append(self.association(point={"latitude": lat, "longitude": lon, "precision": self.manual_precision.get()}))
            self.editor_json.delete("1.0", "end")
            self.editor_json.insert("1.0", json.dumps(value, ensure_ascii=False, indent=2))
            tabs.select(advanced)
        self.button(bar, "Associate saved point", lambda: self.guard("Saved point", add_point))
        dialog.bind("<Escape>", lambda _: close())
        def focus_name(_=None):
            tabs.select(basic_page)
            canvas.yview_moveto(0)
            self.display_name_entry.focus_set()
            self.display_name_entry.selection_range(0, "end")
            return "break"
        def keyboard_save(_=None):
            self.guard("Save contact", save)
            return "break"
        dialog.bind("<Alt-n>", focus_name)
        dialog.bind("<Alt-N>", focus_name)
        dialog.bind("<Control-Return>", keyboard_save)
        focus_job = dialog.after_idle(focus_name)

    @staticmethod
    def association(address_id=None, point=None):
        return {"id": str(uuid.uuid4()), "address_id": address_id, "basis": "manual_location" if point else "saved_address",
                "source": "user_observation", "temporal_status": "saved", "point": point,
                "geocode_status": "resolved" if point else "unresolved", "provider": None,
                "resolved_at": None, "verified_at": now_iso() if point else None, "normalized_address": None}

    def contact_action(self, scheme, value):
        if not value.strip() or any(c in value for c in "\r\n"):
            self.status.set("No usable phone/email selected.")
            return
        if messagebox.askyesno("Open system application", f"Open the {scheme} application for this detail value?\nFIC will not send or call automatically.", parent=self.editor_window or self.root):
            self.guard("External action", lambda: external(scheme + ":" + quote(value.strip(), safe="+@.-")))

    def delete_contacts(self):
        selected = self.network_tree.selection()
        if selected and messagebox.askyesno("Delete local contacts", f"Delete {len(selected)} selected records and derived associations?\nOriginal files and previous exports are not deleted.", parent=self.root):
            for identifier in selected:
                self.store.delete_entity(identifier)
            self.refresh_network()

    def export_contacts(self):
        selected = self.network_tree.selection()
        if not selected:
            self.status.set("Select the specific records to export.")
            return
        def export():
            fmt = self.export_format.get()
            preview = export_preview([self.store.get_entity(identifier) for identifier in selected], fmt)
            warnings = "\n".join(preview.warnings) or "Canonical JSON retains provenance and unknown fields."
            if not messagebox.askyesno("Deliberate export preview", f"Export {preview.count} selected records including details and notes?\n{warnings}\nExported files are not encrypted.", parent=self.root):
                return
            name = filedialog.asksaveasfilename(parent=self.root, defaultextension="." + fmt, filetypes=[(fmt.upper(), "*." + fmt)])
            if name:
                atomic_write(name, preview.data)
                self.status.set(f"Exported {preview.count} selected records as {fmt.upper()}; no network transfer.")
        self.guard("Export contacts", export)

    def build_place(self, prefs):
        ttk.Label(self.place_tab, text="Desktop has no assumed GPS. Enter a place manually or select one from a report.", wraplength=950).pack(anchor="w")
        self.place_label = tk.StringVar(value=self.place_origin)
        ttk.Label(self.place_tab, textvariable=self.place_label).pack(anchor="w", pady=10)
        form = ttk.Frame(self.place_tab)
        form.pack(fill="x")
        self.place_vars = {}
        for row, key in enumerate(("latitude", "longitude", "street", "neighborhood", "locality", "region", "country")):
            ttk.Label(form, text=key.title()).grid(row=row, column=0, sticky="w", pady=6)
            value = self.place.get(key)
            variable = tk.StringVar(value="" if value is None else str(value))
            self.place_vars[key] = variable
            ttk.Entry(form, textvariable=variable, width=65).grid(row=row, column=1, padx=10, sticky="ew")
        form.columnconfigure(1, weight=1)
        ttk.Label(form, text="Radius (metres)").grid(row=7, column=0, sticky="w")
        self.radius = tk.StringVar(value=str(prefs.get("radius_m", 1000)))
        ttk.Entry(form, textvariable=self.radius).grid(row=7, column=1, sticky="w", padx=10)
        bar = ttk.Frame(self.place_tab)
        bar.pack(fill="x", pady=12)
        self.button(bar, "Emergency numbers", lambda: self.guard("Emergency numbers", self.show_country_numbers))
        self.save_place_button = self.button(bar, "Use this manual place", lambda: self.guard("Place", self.save_place))
        self.button(bar, "Open map for saved point", self.open_map)
        self.earth_button = self.button(bar, "World Intelligence", lambda: self.guard("World Intelligence", self.open_earth_engine))
        self.analysis_button = self.button(bar, "AI Analysis", lambda: self.guard("AI Analysis", self.open_analysis))
        ttk.Label(self.place_tab, text="Street/neighborhood text matches require locality and country context. Missing evidence is unavailable, not zero people. Radius means distance to a qualified saved point, not walking distance or current whereabouts. External geocoding is OFF; no imported address is transmitted.", wraplength=950).pack(anchor="w")

    def save_place(self):
        lat, lon = coordinates(self.place_vars["latitude"].get(), self.place_vars["longitude"].get())
        self.place = {key: variable.get().strip() for key, variable in self.place_vars.items() if key not in ("latitude", "longitude")}
        self.place.update(latitude=lat, longitude=lon)
        self.place_origin = "Manual place"
        self.sync_place()
        self.save_preferences()
        self.status.set("Manual place saved locally; no GPS or geocoder claim.")

    def sync_place(self):
        self.place_label.set(self.place_origin)
        self.area_label.set(self.area_text())
        for key, variable in self.place_vars.items():
            variable.set("" if self.place.get(key) is None else str(self.place.get(key)))

    def area_text(self):
        parts = [str(self.place.get(key)) for key in ("street", "locality", "region", "country") if self.place.get(key)]
        if self.place.get("latitude") is not None and self.place.get("longitude") is not None:
            parts.append(f"{self.place['latitude']}, {self.place['longitude']}")
        return "Selected area: " + (", ".join(parts) or "unavailable") + f" — {self.place_origin}"

    def show_country_numbers(self):
        from tkinter import simpledialog
        from field_intelligence_console.regional import interior_country, emergency_services
        lat, lon = self.place.get("latitude"), self.place.get("longitude")
        suggested = interior_country(lat, lon) if lat is not None and lon is not None else ""
        country = simpledialog.askstring("Emergency numbers", "Confirm the country (two-letter ISO code).",
                                         initialvalue=suggested or "", parent=self.root)
        if country is None:
            return
        rows = emergency_services(country)
        text = "\n\n".join(f"{r['category']} · {r['number']}\n{r['verified']} · {r['source']}" for r in rows)
        messagebox.showinfo("Emergency numbers", text or "EMERGENCY NUMBER NOT VERIFIED", parent=self.root)

    def open_analysis(self):
        from human_analysis import AnalysisDialog
        from field_intelligence_console.regional import interior_country
        ctx = dict(self.place)
        lat, lon = ctx.get("latitude"), ctx.get("longitude")
        origin = {"Manual place": "manual", "Report-derived place": "historical"}.get(self.place_origin, "unknown")
        ctx.update(origin=origin, area=ctx.get("region"), override=ctx.get("country") if origin == "manual" else None,
                   detected=interior_country(lat, lon) if lat is not None and lon is not None else None)
        def save(language):
            self.analysis_language = language
            self.save_preferences()
        self.analysis = AnalysisDialog(self.root, ctx, self.analysis_language, save)

    def open_earth_engine(self):
        from field_intelligence_console.earth import client_url
        url = client_url(self.place.get("latitude"), self.place.get("longitude"), language=None)
        if not webbrowser.open(url, new=1):
            raise ValueError("Unable to open the Earth panel in the default browser.")
        self.status.set("World Intelligence opened for the selected place.")

    def open_map(self):
        lat, lon = self.place.get("latitude"), self.place.get("longitude")
        if lat is None or lon is None:
            self.status.set("LOCATION UNAVAILABLE — select valid coordinates.")
            return
        def open_selected():
            coordinates(lat, lon)
            if messagebox.askyesno("Open external map", "Send the selected saved coordinates to OpenStreetMap in your browser?\nNo contact details are sent.", parent=self.root):
                external(f"https://www.openstreetmap.org/?mlat={lat}&mlon={lon}#map=16/{lat}/{lon}")
        self.guard("Map", open_selected)

    def build_settings(self, prefs):
        ttk.Label(self.settings_tab, text="Local preferences. No required account or API key.").pack(anchor="w")
        self.theme = tk.StringVar(value=prefs.get("theme", "Light"))
        self.text_size = tk.StringVar(value=str(prefs.get("text_size", 11)))
        for label, variable, values in (("Theme", self.theme, ("Light", "Dark", "High contrast")), ("Text size", self.text_size, ("10", "11", "12", "14", "16", "18"))):
            ttk.Label(self.settings_tab, text=label).pack(anchor="w", pady=(12, 3))
            combo = ttk.Combobox(self.settings_tab, textvariable=variable, values=values, state="readonly")
            combo.pack(anchor="w")
            combo.bind("<<ComboboxSelected>>", lambda _: self.apply_theme())
        ttk.Label(self.settings_tab, text=f"FIC {VERSION} experimental\nCore: offline / local SQLite\nExternal geocoding: OFF by default; optional OpenCage requires key + address consent\nAI providers: unavailable on desktop; imported advisory stays labeled\nGPS: unavailable; manual/report locations only\nContacts: selected files only; no address-book access\nSigning: unsigned Windows experimental application\nStorage: local profile, not encrypted; protect your Windows account/backups\nKeyboard: Ctrl+F search, Ctrl+I import, Ctrl+N add, Enter open, Escape cancel\nDeletion: exports/backups and filesystem recovery may remain", wraplength=960, justify="left").pack(anchor="w", pady=20)
        ttk.Button(self.settings_tab, text="Third-party notices", command=self.show_notices).pack(anchor="w")

    def show_notices(self):
        dialog = tk.Toplevel(self.root)
        dialog.title("FIC and third-party licenses")
        dialog.geometry("900x650")
        text = tk.Text(dialog, wrap="word")
        text.pack(fill="both", expand=True)
        directory = Path(getattr(sys, "_MEIPASS", Path(__file__).parent)) / "notices"
        for path in sorted(directory.glob("*.txt")):
            text.insert("end", path.name + "\n\n" + path.read_text(encoding="utf-8", errors="replace") + "\n\n")
        text.configure(state="disabled")

    def apply_theme(self, save=True):
        style = ttk.Style(self.root)
        style.theme_use("clam")
        palettes = {"Light": ("#f4f6f8", "#132332", "#ffffff", "#005b96"),
                    "Dark": ("#18212b", "#f1f5f9", "#253241", "#3169a0"),
                    "High contrast": ("#000000", "#ffffff", "#000000", "#003d99")}
        bg, fg, field, selected = palettes.get(self.theme.get(), palettes["Light"])
        size = int(self.text_size.get())
        for name in ("TkDefaultFont", "TkTextFont", "TkMenuFont", "TkHeadingFont"):
            tkfont.nametofont(name).configure(size=size)
        style.configure(".", background=bg, foreground=fg, font=("Segoe UI", size))
        style.configure("Treeview", background=field, fieldbackground=field, foreground=fg, rowheight=size * 2 + 8)
        style.map("Treeview", background=[("selected", selected)], foreground=[("selected", "white")])
        style.configure("TEntry", fieldbackground=field, foreground=fg)
        style.configure("TCombobox", fieldbackground=field, foreground=fg)
        style.map("TCombobox", fieldbackground=[("readonly", field)], foreground=[("readonly", fg)])
        style.configure("TNotebook", background=bg)
        style.configure("TNotebook.Tab", background=field, foreground=fg)
        style.map("TNotebook.Tab", background=[("selected", selected)], foreground=[("selected", "white")])
        style.map("TButton", background=[("active", selected)], foreground=[("active", "white")])
        self.root.configure(background=bg)
        self.report_detail.configure(background=field, foreground=fg, insertbackground=fg, font=("Segoe UI", size))
        if save:
            self.save_preferences()

    def save_preferences(self):
        prefs = {"theme": self.theme.get(), "text_size": int(self.text_size.get()), "place": self.place,
                 "place_origin": self.place_origin, "radius_m": self.radius.get(), "analysis_language": self.analysis_language}
        atomic_write(self.prefs_file, json.dumps(prefs, allow_nan=False).encode("utf-8"))

    def close(self):
        self.save_preferences()
        self.store.close()
        self.root.destroy()


def main():
    parser = argparse.ArgumentParser(description="FIC offline Windows workstation")
    parser.add_argument("--profile", type=Path, help="Isolated profile; otherwise local application data")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--ui-test", action="store_true")
    parser.add_argument("--evidence", type=Path)
    parser.add_argument("--stay-open", action="store_true", help="Keep synthetic UI window open for native interaction checks")
    parser.add_argument("--interop-input", type=Path, help="Explicit synthetic canonical exchange input for UI round-trip gate")
    parser.add_argument("--interop-output", type=Path, help="Explicit canonical output for UI round-trip gate")
    parser.add_argument("--interop-report-input", type=Path, help="Explicit synthetic report input for UI round trip")
    parser.add_argument("--interop-report-output", type=Path, help="Explicit report output for UI round trip")
    args = parser.parse_args()
    if (args.self_test or args.ui_test) and not args.profile:
        parser.error("Test modes require an explicit isolated --profile.")
    if bool(args.interop_input) != bool(args.interop_output) or (args.interop_input and not args.ui_test):
        parser.error("Interop input/output must be supplied together with --ui-test.")
    if bool(args.interop_report_input) != bool(args.interop_report_output) or (args.interop_report_input and not args.ui_test):
        parser.error("Report interop input/output must be supplied together with --ui-test.")
    if args.self_test or args.ui_test:
        from workstation_checks import run_checks
        return run_checks(args)
    profile = args.profile or Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "FieldIntelligenceConsole"
    root = tk.Tk()
    Workstation(root, profile)
    root.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
