"""Conservative Git-history upload inventory; findings contain no matched values.

This is a review aid, never automatic permission to push. Run again for each new
candidate ref. Only Git objects reachable from explicit refs are read; the working
tree, ignored files and unrelated credentials are not examined.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from collections import defaultdict
from pathlib import Path

PATTERNS = {
    "private_key": r"-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----",
    "provider_token": r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-(?:proj-)?[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{30,})\b",
    "credential_literal": r'''(?i)(?:api[_-]?key|password|client[_-]?secret|access[_-]?token|authorization)\s*[=:]\s*["'][^"'\r\n]{8,}["']''',
    "authenticated_url": r"(?i)https?://[^\s/:]+:[^\s/@]+@",
    "email": r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b",
    "vcard_data": r"(?im)^BEGIN:VCARD\s*$",
    "coordinate_literal": r"(?i)(?:lat(?:itude)?|lon(?:gitude)?)\s*[\"']?\s*[:=]\s*-?\d+(?:\.\d+)?",
    "host_path": r"(?i)(?:[A-Z]:[\\/](?:Users|home)[\\/]|/home/|/Users/)",
    "data_row": r"(?i)(?:INSERT\s+INTO\s+(?:field_entries|contacts|network)|SQLite format 3)",
}
DATA_SUFFIXES = {".db", ".sqlite", ".sqlite3", ".vcf", ".csv", ".jks", ".keystore", ".pem", ".p12", ".pfx", ".log", ".apk", ".exe", ".whl", ".zip", ".7z"}


def git(*args: str) -> bytes:
    return subprocess.run(["git", *args], check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=120).stdout


def audit(refs: list[str]) -> dict:
    resolved = {ref: git("rev-parse", "--verify", ref + "^{commit}").decode().strip() for ref in refs}
    commits = git("rev-list", *resolved.values()).decode().splitlines()
    paths: dict[str, set[str]] = defaultdict(set)
    special = []
    for commit in commits:
        for entry in git("ls-tree", "-rz", "--full-tree", commit).split(b"\0"):
            if not entry:
                continue
            info, raw_path = entry.split(b"\t", 1)
            mode, kind, oid = info.decode().split()
            path = raw_path.decode("utf-8", errors="replace")
            if kind == "blob":
                paths[oid].add(path)
            if mode in {"120000", "160000"}:
                special.append({"commit": commit, "path": path, "mode": mode})
    findings = []
    binaries = []
    data_paths = []
    inventory = []
    total_bytes = 0
    compiled = {name: re.compile(pattern) for name, pattern in PATTERNS.items()}
    for oid, names in sorted(paths.items()):
        blob = git("cat-file", "blob", oid)
        total_bytes += len(blob)
        inventory.append({"blob": oid, "paths": sorted(names), "bytes": len(blob),
                          "sha256": hashlib.sha256(blob).hexdigest()})
        for name in sorted(names):
            if Path(name).suffix.lower() in DATA_SUFFIXES or Path(name).name in {".env", "local.properties"}:
                data_paths.append({"blob": oid, "path": name, "bytes": len(blob)})
        try:
            content = blob.decode("utf-8")
            if "\0" in content:
                raise UnicodeError()
        except UnicodeError:
            binaries.append({"blob": oid, "paths": sorted(names), "bytes": len(blob),
                             "sha256": hashlib.sha256(blob).hexdigest()})
            continue
        for category, pattern in compiled.items():
            lines = sorted({content.count("\n", 0, match.start()) + 1 for match in pattern.finditer(content)})
            if lines:
                findings.append({"category": category, "blob": oid, "paths": sorted(names), "lines": lines})
    # Commit/tag messages and identities can also disclose sensitive information.
    metadata = git("log", "--format=%H%x00%an%x00%ae%x00%cn%x00%ce%x00%B", *resolved.values()).decode("utf-8", errors="replace")
    metadata_categories = sorted(name for name, pattern in compiled.items() if pattern.search(metadata))
    tag_objects = []
    for ref in refs:
        oid = git("rev-parse", "--verify", ref).decode().strip()
        if git("cat-file", "-t", oid).strip() == b"tag":
            tag_text = git("cat-file", "tag", oid).decode("utf-8", errors="replace")
            tag_objects.append({"ref": ref, "object": oid,
                                "categories": sorted(name for name, p in compiled.items() if p.search(tag_text))})
    return {"status": "REQUIRES_HUMAN_REVIEW", "refs": resolved, "commits": len(commits),
            "unique_blobs": len(paths), "unique_paths": len(set().union(*paths.values())) if paths else 0,
            "total_blob_bytes": total_bytes, "findings": findings, "binaries": binaries,
            "data_paths": data_paths, "special_entries": special, "inventory": inventory,
            "commit_metadata_categories": metadata_categories, "annotated_tags": tag_objects,
            "limitations": "Pattern/inventory aid, not proof of absence. Review all flagged historical blobs, identities, binary contents and data provenance. No artifact or ignored-file audit."}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("refs", nargs="+", help="Explicit reviewed branch/tag names")
    parser.add_argument("--output", type=Path, required=True, help="Local sanitized JSON receipt")
    args = parser.parse_args()
    report = audit(args.refs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("status", "refs", "commits", "unique_blobs", "unique_paths", "total_blob_bytes")}))
    print(f"Finding groups: {len(report['findings'])}; binary blobs: {len(report['binaries'])}; data paths: {len(report['data_paths'])}")


if __name__ == "__main__":
    main()
