"""SQLite persistence with explicit schema checks and atomic, selected imports."""
from copy import deepcopy
import json
from pathlib import Path
import sqlite3
from .models import ValidationError, validate_report, network_dataset, now_iso
from .exchange import normalized


class Store:
    def __init__(self, path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(self.path, timeout=5)
        self.connection.execute("PRAGMA foreign_keys=ON")
        try:
            version = self.connection.execute("PRAGMA user_version").fetchone()[0]
            tables = {row[0] for row in self.connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            if version == 0 and not tables:
                with self.connection:
                    self.connection.execute("CREATE TABLE entities (id TEXT PRIMARY KEY, document TEXT NOT NULL, search_text TEXT NOT NULL)")
                    self.connection.execute("CREATE TABLE ancestry (source_id TEXT NOT NULL, record_id TEXT NOT NULL, entity_id TEXT NOT NULL REFERENCES entities(id) ON DELETE CASCADE, PRIMARY KEY(source_id,record_id))")
                    self.connection.execute("CREATE TABLE reports (id TEXT PRIMARY KEY, document TEXT NOT NULL, search_text TEXT NOT NULL)")
                    self.connection.execute("PRAGMA user_version=1")
            elif version != 1:
                raise ValidationError("Unsupported local store version; database left intact")
            expected = {"entities": ["id", "document", "search_text"], "ancestry": ["source_id", "record_id", "entity_id"],
                        "reports": ["id", "document", "search_text"]}
            for table, columns in expected.items():
                info = list(self.connection.execute(f"PRAGMA table_info({table})"))
                expected_keys = [1, 2, 0] if table == "ancestry" else [1, 0, 0]
                expected_nulls = [1, 1, 1] if table == "ancestry" else [0, 1, 1]
                if ([row[1] for row in info] != columns or [row[2].upper() for row in info] != ["TEXT"] * 3
                        or [row[3] for row in info] != expected_nulls or [row[5] for row in info] != expected_keys):
                    raise ValidationError("Malformed local store schema; database left intact")
            foreign_keys = list(self.connection.execute("PRAGMA foreign_key_list(ancestry)"))
            if len(foreign_keys) != 1 or foreign_keys[0][2:5] != ("entities", "entity_id", "id") or foreign_keys[0][6] != "CASCADE":
                raise ValidationError("Malformed local store relationships; database left intact")
        except Exception:
            self.connection.close()
            raise

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def close(self):
        self.connection.close()

    @staticmethod
    def _json(document):
        return json.dumps(document, ensure_ascii=False, allow_nan=False, separators=(",", ":"))

    @staticmethod
    def _search(entity):
        return normalized(" ".join(str(entity[key]) for key in
            ("display_name", "organization", "role", "tags", "notes", "phones", "emails", "addresses")))

    def _duplicate(self, entity):
        if self.connection.execute("SELECT 1 FROM entities WHERE id=?", (entity["id"],)).fetchone():
            return True
        return any(self.connection.execute("SELECT 1 FROM ancestry WHERE source_id=? AND record_id=?",
                   (a["source_id"], a["record_id"])).fetchone() for a in entity["ancestry"])

    def preview_counts(self, preview):
        identities, ancestry, duplicates = set(), set(), 0
        for entity in preview.entities:
            keys = {(a["source_id"], a["record_id"]) for a in entity["ancestry"]}
            if entity["id"] in identities or ancestry.intersection(keys) or self._duplicate(entity):
                duplicates += 1
            identities.add(entity["id"])
            ancestry.update(keys)
        return {"valid": preview.valid_count, "invalid": preview.invalid_count,
                "duplicates": duplicates, "new": preview.valid_count - duplicates}

    def _write_entity(self, entity, replace=False):
        network_dataset([entity])
        if replace:
            self.connection.execute("UPDATE entities SET document=?,search_text=? WHERE id=?",
                                    (self._json(entity), self._search(entity), entity["id"]))
            self.connection.execute("DELETE FROM ancestry WHERE entity_id=?", (entity["id"],))
        else:
            self.connection.execute("INSERT INTO entities VALUES (?,?,?)",
                                    (entity["id"], self._json(entity), self._search(entity)))
        for a in entity["ancestry"]:
            self.connection.execute("INSERT OR IGNORE INTO ancestry VALUES (?,?,?)", (a["source_id"], a["record_id"], entity["id"]))
            owner = self.connection.execute("SELECT entity_id FROM ancestry WHERE source_id=? AND record_id=?",
                                            (a["source_id"], a["record_id"])).fetchone()[0]
            if owner != entity["id"]:
                raise ValidationError("Source ancestry belongs to another record; explicit merge review required")

    def import_preview(self, preview, selected_ids):
        if selected_ids is None:
            raise ValidationError("Explicit record selection required")
        selected = set(selected_ids)
        if selected - {entity["id"] for entity in preview.entities}:
            raise ValidationError("Selection contains an unknown record")
        inserted, duplicates = 0, 0
        # Validate the entire selection before mutation, then one SQL transaction.
        for entity in preview.entities:
            if entity["id"] in selected:
                network_dataset([entity])
        try:
            with self.connection:
                for entity in preview.entities:
                    if entity["id"] not in selected:
                        continue
                    if self._duplicate(entity):
                        duplicates += 1
                    else:
                        self._write_entity(entity)
                        inserted += 1
        except sqlite3.DatabaseError as exc:
            raise ValidationError("Atomic import failed; no selected records were imported") from exc
        return {"inserted": inserted, "duplicates": duplicates}

    def list_entities(self, search=""):
        term = normalized(search)
        return [json.loads(row[0]) for row in self.connection.execute(
            "SELECT document FROM entities WHERE instr(search_text,?)>0 ORDER BY id", (term,))]

    def get_entity(self, id):
        row = self.connection.execute("SELECT document FROM entities WHERE id=?", (id,)).fetchone()
        return json.loads(row[0]) if row else None

    def save_entity(self, entity):
        entity = deepcopy(entity)
        previous = self.get_entity(entity["id"])
        if previous:
            # Preserve original ancestry and record correction time; do not copy private old text into audit logs.
            for a in previous["ancestry"]:
                if a not in entity["ancestry"]:
                    entity["ancestry"].append(a)
            entity["corrections"].append({"at": now_iso(), "source": "user_observation"})
        with self.connection:
            self._write_entity(entity, replace=previous is not None)

    def delete_entity(self, id):
        with self.connection:
            self.connection.execute("DELETE FROM entities WHERE id=?", (id,))

    def save_report(self, report):
        validate_report(report)
        search = normalized(" ".join((report["observation"]["note"], report["observation"]["tag"],
                                      report["location"]["address"] or "", report["observed_at"])))
        with self.connection:
            self.connection.execute("INSERT INTO reports VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET document=excluded.document,search_text=excluded.search_text",
                                    (report["id"], self._json(report), search))

    def list_reports(self, search=""):
        return [json.loads(row[0]) for row in self.connection.execute(
            "SELECT document FROM reports WHERE instr(search_text,?)>0 ORDER BY id", (normalized(search),))]

    def get_report(self, id):
        row = self.connection.execute("SELECT document FROM reports WHERE id=?", (id,)).fetchone()
        return json.loads(row[0]) if row else None

    def delete_report(self, id):
        with self.connection:
            self.connection.execute("DELETE FROM reports WHERE id=?", (id,))
