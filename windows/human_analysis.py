"""Local Tk editor for the shared question catalog."""
import tkinter as tk
from tkinter import ttk
from tkinter.scrolledtext import ScrolledText
import webbrowser

from field_intelligence_console import human_context as hc


class AnalysisDialog(tk.Toplevel):
    def __init__(self, parent, context, language="en", save_language=lambda _: None):
        super().__init__(parent)
        self.title("AI ANALYSIS")
        self.geometry("720x670")
        self.transient(parent)
        self.context = context
        self.language = language
        self.save_language = save_language
        self.identifier = None
        self.disclosure = "none"
        self.notes = self.excerpt = self.target = ""
        self.request = self.t("default")
        self.frame = ttk.Frame(self, padding=12)
        self.frame.pack(fill="both", expand=True)
        self.bind("<Escape>", lambda _: self.destroy())
        self.form()

    def t(self, key):
        return hc.localized(key, self.language)

    def clear(self):
        for widget in self.frame.winfo_children():
            widget.destroy()

    def button(self, parent, key, action):
        widget = ttk.Button(parent, text=self.t(key), command=action)
        widget.pack(side="left", padx=3)
        return widget

    def bar(self):
        bar = ttk.Frame(self.frame)
        bar.pack(fill="x", pady=4)
        return bar

    def field(self, label, value, height):
        ttk.Label(self.frame, text=self.t(label)).pack(anchor="w")
        box = ScrolledText(self.frame, wrap="word", height=height, undo=True)
        box.pack(fill="x", pady=(0, 3))
        box.insert("1.0", value)
        return box

    def capture(self):
        self.request, self.notes, self.excerpt = (box.get("1.0", "end-1c") for box in
                                                  (self.question, self.note_box, self.earth_box))
        self.disclosure = ("none", "region", "exact")[self.location.current()]
        self.target = self.target_var.get()

    def form(self):
        self.clear()
        top = self.bar()
        self.button(top, "catalog", self.browse)
        self.button(top, "custom", lambda: self.choose(None))
        language = ttk.Combobox(top, values=hc.LANGUAGES, width=5, state="readonly")
        language.set(self.language)
        language.pack(side="right")
        def change(_):
            self.capture()
            old = hc.question(self.identifier)["question"][self.language] if self.identifier else self.t("default")
            self.language = language.get()
            if self.request == old:
                self.request = hc.question(self.identifier)["question"][self.language] if self.identifier else self.t("default")
            self.save_language(self.language)
            self.form()
        language.bind("<<ComboboxSelected>>", change)
        if self.identifier and int(self.identifier[1:]) <= 10:
            ttk.Label(self.frame, text=self.t("emergency"), wraplength=605).pack(anchor="w")
            detected, override = self.context.get("detected"), self.context.get("override")
            if detected and override and detected.upper() != override.upper():
                ttk.Label(self.frame, text=f"{self.t('mismatch')} {self.t('detected')} · {detected} / "
                                          f"{self.t('override')} · {override}", wraplength=605).pack(anchor="w")
        if self.identifier:
            ttk.Label(self.frame, text=self.identifier).pack(anchor="w")
        self.question = self.field("question", self.request, 2)
        self.location = ttk.Combobox(self.frame, state="readonly", values=[self.t(k) for k in ("none", "region", "exact")])
        self.location.current(("none", "region", "exact").index(self.disclosure))
        self.location.pack(anchor="w", pady=4)
        self.target_var = tk.StringVar(self, self.target)
        if self.identifier == "Q30":
            ttk.Label(self.frame, text=self.t("target")).pack(anchor="w")
            ttk.Entry(self.frame, textvariable=self.target_var).pack(fill="x")
        self.note_box = self.field("notes", self.notes, 2)
        self.earth_box = self.field("earth", self.excerpt, 2)
        ttk.Label(self.frame, text=self.t("evidenceHint")).pack(anchor="w")
        controls = self.bar()
        self.button(controls, "preview", self.preview)
        self.button(controls, "cancel", self.destroy)

    def browse(self):
        self.capture()
        self.clear()
        groups = hc.categories()
        group = ttk.Combobox(self.frame, state="readonly", values=[self.t("all")] + [c["title"][self.language] for c in groups])
        group.current(0)
        group.pack(fill="x")
        ttk.Label(self.frame, text=self.t("search")).pack(anchor="w")
        search = tk.StringVar(self)
        ttk.Entry(self.frame, textvariable=search).pack(fill="x")
        items = tk.Listbox(self.frame, height=12, exportselection=False)
        items.pack(fill="both", expand=True)
        def refresh(*_):
            items.delete(0, "end")
            i = group.current()
            for q in hc.questions(groups[i-1]["id"] if i > 0 else None):
                label = q["id"] + " · " + q["title"][self.language]
                if search.get().strip().casefold() in (label + " " + q["question"][self.language]).casefold():
                    items.insert("end", label)
        search.trace_add("write", refresh)
        group.bind("<<ComboboxSelected>>", refresh)
        refresh()
        def select(*_):
            if items.curselection():
                self.choose(items.get(items.curselection()[0]).split(" · ")[0])
        items.bind("<Return>", select)
        items.bind("<Double-Button-1>", select)
        bar = self.bar()
        self.button(bar, "question", select)
        self.button(bar, "back", self.form)

    def choose(self, identifier):
        if identifier is None:
            self.capture()
        self.identifier = identifier
        self.request = hc.question(identifier)["question"][self.language] if identifier else self.t("default")
        self.form()

    def preview(self):
        self.capture()
        if not self.request.strip():
            self.question.focus_set()
            return
        draft = hc.compose(self.identifier, self.language, self.context, disclosure=self.disclosure,
                           custom=self.request, observation=self.notes, earth_excerpt=self.excerpt, target_language=self.target)
        self.clear()
        ttk.Label(self.frame, text=self.t("edit")).pack(anchor="w")
        self.editor = ScrolledText(self.frame, wrap="word", undo=True)
        self.editor.pack(fill="both", expand=True)
        self.editor.insert("1.0", draft)
        self.status = tk.StringVar(self)
        ttk.Label(self.frame, textvariable=self.status).pack(anchor="w")
        bar = self.bar()
        self.button(bar, "copy", self.copy)
        self.button(bar, "open", self.open_chat)
        self.button(bar, "back", self.form)
        self.button(bar, "cancel", self.destroy)

    def copy(self):
        text = self.editor.get("1.0", "end-1c")
        if not hc.reviewable(text):
            self.status.set(self.t("limit"))
            return
        self.clipboard_clear()
        self.clipboard_append(text)
        self.status.set(self.t("copied"))

    def open_chat(self):
        self.status.set(self.t("verify" if webbrowser.open("https://chatgpt.com/", new=1) else "fallback"))
