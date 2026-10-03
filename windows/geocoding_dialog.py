"""Opt-in outbound address geocoding, separated from offline import and browsing."""
import concurrent.futures
import queue
import threading
import tkinter as tk
from tkinter import ttk, messagebox
import uuid


class GeocodingDialog:
    def __init__(self, owner, entity, provider=None):
        from field_intelligence_console.providers import OpenCageGeocoder
        self.owner, self.entity = owner, entity
        self.provider = provider or OpenCageGeocoder()
        self.cancel_event = threading.Event()
        self.results = queue.Queue()
        self.closed = False
        self.candidates = []
        self.address = None
        self.dialog = tk.Toplevel(owner.root)
        self.dialog.title("Optional external geocoding — explicit address disclosure")
        self.dialog.geometry("950x600")
        self.dialog.transient(owner.root)
        self.dialog.protocol("WM_DELETE_WINDOW", self.close)
        ttk.Label(self.dialog, text="OFF until you click Send selected address. Provider: OpenCage.\nOnly the selected address is transmitted, never contact name, phone, email or notes.\nAn address can itself be private. Candidates remain unresolved until you deliberately select one.", wraplength=920).pack(anchor="w", padx=12, pady=10)
        self.addresses = owner.table(self.dialog, ("Saved label", "Exact address sent"), (140, 750))
        for address in entity.get("addresses", []):
            text = address.get("original") or ", ".join(address.get(k, "") for k in ("street", "locality", "region", "postal_code", "country") if address.get(k))
            self.addresses.insert("", "end", iid=address["id"], values=(address.get("label", "unspecified"), text))
        self.addresses.configure(selectmode="browse", height=4)
        keybar = ttk.Frame(self.dialog)
        keybar.pack(fill="x", padx=12)
        ttk.Label(keybar, text="OpenCage key (memory only): ").pack(side="left")
        self.key = tk.StringVar()
        ttk.Entry(keybar, textvariable=self.key, show="•", width=40).pack(side="left")
        self.send_button = owner.button(keybar, "Send selected address", self.send)
        owner.button(keybar, "Cancel request", self.cancel)
        self.state = tk.StringVar(value="No request sent.")
        ttk.Label(self.dialog, textvariable=self.state, wraplength=920).pack(anchor="w", padx=12, pady=8)
        self.matches = owner.table(self.dialog, ("Candidate address", "Precision", "Attribution"), (600, 100, 230))
        self.matches.configure(selectmode="browse")
        bar = ttk.Frame(self.dialog)
        bar.pack(fill="x", padx=12, pady=8)
        self.save_button = owner.button(bar, "Confirm selected candidate", self.save)
        self.save_button.configure(state="disabled")
        owner.button(bar, "Close without selection", self.close)
        self.dialog.bind("<Escape>", lambda _: self.close())

    def send(self):
        selected = self.addresses.selection()
        if not selected or not self.key.get().strip():
            self.state.set("Select an address and enter a provider key. Offline features need no key.")
            return
        address = next(a for a in self.entity["addresses"] if a["id"] == selected[0])
        text = self.addresses.item(selected[0], "values")[1]
        if not messagebox.askyesno("Confirm address disclosure to OpenCage", f"Send this exact address to OpenCage?\n\n{text}\n\nNo other contact fields will be sent. Provider terms and account limits apply.", parent=self.dialog):
            return
        self.address = address
        self.cancel_event = threading.Event()
        self.candidates = []
        self.matches.delete(*self.matches.get_children())
        self.send_button.configure(state="disabled")
        self.save_button.configure(state="disabled")
        self.state.set("Querying OpenCage; you can cancel. No location will be saved automatically.")
        api_key = self.key.get().strip()
        self.key.set("")
        def worker():
            try:
                candidates = self.provider.lookup(text, api_key, consent=True, cancel=self.cancel_event)
                self.results.put(("ok", candidates))
            except concurrent.futures.CancelledError:
                self.results.put(("cancel", None))
            except Exception:
                self.results.put(("error", None))
        threading.Thread(target=worker, daemon=True, name="FIC-explicit-geocode").start()
        self.dialog.after(50, self.poll)

    def poll(self):
        if self.closed:
            return
        try:
            status, values = self.results.get_nowait()
        except queue.Empty:
            self.dialog.after(50, self.poll)
            return
        self.send_button.configure(state="normal")
        if self.cancel_event.is_set() or status == "cancel":
            self.state.set("Cancelled. No derived location was saved.")
        elif status != "ok":
            self.state.set("QUERY FAILED. No address resolved or saved; offline records are unchanged.")
        else:
            self.candidates = values
            for index, candidate in enumerate(values):
                self.matches.insert("", "end", iid=str(index), values=(candidate["formatted"], candidate["precision"], str(candidate.get("attribution", "OpenCage"))))
            self.state.set("Select and confirm a candidate deliberately. Saved association is not present whereabouts." if values else "NO RESULTS — no address resolved; existing data unchanged.")
            self.save_button.configure(state="normal" if values else "disabled")

    def save(self):
        selected = self.matches.selection()
        if not selected or not self.address:
            self.state.set("Select a candidate first; ambiguous matches are not automatically trusted.")
            return
        candidate = self.candidates[int(selected[0])]
        def persist():
            current = self.owner.store.get_entity(self.entity["id"])
            if current is None or not any(a["id"] == self.address["id"] and a == self.address for a in current["addresses"]):
                raise ValueError("The saved address changed. Reopen geocoding to review it again.")
            current["associations"].append({"id": str(uuid.uuid4()), "address_id": self.address["id"], "basis": "saved_address",
                "source": "external_provider", "temporal_status": "saved",
                "point": {"latitude": candidate["latitude"], "longitude": candidate["longitude"], "precision": candidate["precision"]},
                "geocode_status": "resolved", "provider": candidate["provider"], "resolved_at": candidate["resolved_at"],
                "verified_at": None, "normalized_address": candidate["formatted"]})
            current.setdefault("extensions", {})["geocoder_attribution"] = candidate.get("attribution", "OpenCage")
            self.owner.store.save_entity(current)
            self.owner.refresh_network()
            self.close()
        self.owner.guard("Confirm derived address", persist)

    def cancel(self):
        self.cancel_event.set()
        self.save_button.configure(state="disabled")
        self.state.set("Cancellation requested. The platform request may take time to finish; no result will be saved.")

    def close(self):
        self.cancel_event.set()
        self.key.set("")
        self.closed = True
        self.dialog.destroy()
