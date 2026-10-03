"""Bounded, read-only release-asset review; patterns are not complete secrecy proof.

Inspects only explicit --artifact files. Never executes embedded code, opens user
profiles, extracts archive paths to disk, or prints candidate strings/filenames.
Clean automated output still requires human/coordinator contextual review.
"""
import argparse
import hashlib
import io
import importlib.util
import json
import marshal
from pathlib import Path, PurePosixPath
import re
import struct
import sys
import tarfile
import tempfile
import types
import zipfile
import zlib

MAX_ASSET = 1024 * 1024 * 1024
MAX_ENTRY = 64 * 1024 * 1024
MAX_TOTAL = 1024 * 1024 * 1024
MAX_ENTRIES = 20000
MAX_TOC = 8 * 1024 * 1024
MAX_RATIO = 300
MAX_DEPTH = 4

TOKEN_PATTERNS = (
    ("github_token", re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{30,255}\b")),
    ("github_fine_grained_token", re.compile(rb"\bgithub_pat_[A-Za-z0-9_]{50,255}\b")),
    ("provider_secret_token", re.compile(rb"\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,255}\b")),
    ("aws_access_id", re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("private_key_material", re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----[\sA-Za-z0-9+/=\-]{40,16384}-----END (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")),
    ("credential_assignment", re.compile(rb"(?i)(?:api[_-]?key|access[_-]?token|client[_-]?secret|password)\s*[:=]\s*[\"']([^\"'\r\n]{12,256})[\"']")),
)
PLACEHOLDERS = (b"example", b"placeholder", b"synthetic", b"your_", b"your-", b"not-a-real", b"test-key", b"${", b"getenv", b"secretkey", b"api_key")
PRIVATE_CODE_PATH = re.compile(r"(?i)(?:[a-z]:[\\/]Users[\\/][^\\/]+[\\/]|/(?:home|Users)/[^/]+/|/root/)")
SOURCE_PREFIXES = ("shared/fixtures/", "python/src/field_intelligence_console/fixtures/", "field_intelligence_console/fixtures/")


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_hash(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(chunk)
    return result.hexdigest()


class BoundsError(ValueError):
    pass


class Audit:
    def __init__(self):
        self.findings = []
        self.entries = 0
        self.bytes = 0
        self.code_objects = 0
        self.synthetic_fixture_entries = 0
        self.upstream_notice_entries = 0
        self.canonical_build_path_count = 0
        self._dedup = set()

    def finding(self, category, location):
        identity = (category, digest(location.encode("utf-8", errors="replace")))
        if identity in self._dedup:
            return
        self._dedup.add(identity)
        # No path or content excerpt: a fingerprint allows local contextual review.
        self.findings.append({"category": category, "entry_fingerprint_sha256": identity[1]})

    def count(self, length):
        self.entries += 1
        self.bytes += length
        if length < 0 or length > MAX_ENTRY or self.entries > MAX_ENTRIES or self.bytes > MAX_TOTAL:
            raise BoundsError("Archive entry/total limit exceeded")

    def entry_name(self, name, location):
        normalized = name.replace("\\", "/")
        parts = PurePosixPath(normalized).parts
        lower = normalized.casefold()
        basename = lower.rsplit("/", 1)[-1]
        if normalized.startswith("/") or re.match(r"^[A-Za-z]:", normalized) or ".." in parts:
            self.finding("unsafe_archive_path", location)
        if any(part.casefold() in {".git", ".local", ".venv", ".venv-core", ".venv-windows", "profiles", "userprofiles", "runtime-data"} for part in parts):
            self.finding("bundled_local_profile_or_environment", location)
        if basename == ".env" or basename.startswith(".env.") or basename in {"local.properties", "credentials.json", "credentials.xml", "id_rsa", "id_ed25519"}:
            self.finding("forbidden_configuration_or_key_file", location)
        if basename.endswith((".jks", ".keystore", ".p12", ".pfx", ".key", ".kdbx")):
            self.finding("forbidden_key_store", location)
        if basename.endswith((".db", ".db-wal", ".db-shm", ".sqlite", ".sqlite3", ".sqlite-wal", ".sqlite-shm")):
            self.finding("bundled_database", location)
        synthetic = any(lower.startswith(prefix) or ("/" + prefix) in lower for prefix in SOURCE_PREFIXES)
        if synthetic:
            self.synthetic_fixture_entries += 1
        if "notice" in basename or "license" in basename or basename in {"copying", "al2.0", "lgpl2.1"}:
            self.upstream_notice_entries += 1
        if basename.endswith((".vcf", ".csv")) and not synthetic:
            self.finding("contact_or_tabular_data_requires_review", location)
        if re.search(r"(?:contact[-_]?export|address[-_]?book|location[-_]?history|device[-_]?dump|operational[-_]?report)", basename):
            self.finding("personal_data_filename_requires_review", location)

    def content(self, data, location):
        for category, pattern in TOKEN_PATTERNS:
            for match in pattern.finditer(data):
                candidate = match.group(1) if category == "credential_assignment" else match.group(0)
                if category == "credential_assignment" and any(marker in candidate.lower() for marker in PLACEHOLDERS):
                    continue
                self.finding(category, location)
        # Database magic is checked even when the extension is disguised.
        if data.startswith(b"SQLite format 3\x00"):
            self.finding("sqlite_database_magic", location)

    def code(self, value, location, depth=0):
        if depth > 64:
            raise BoundsError("Code object nesting limit exceeded")
        if isinstance(value, types.CodeType):
            self.code_objects += 1
            if self.code_objects > 100000:
                raise BoundsError("Code object count limit exceeded")
            filename = value.co_filename
            if PRIVATE_CODE_PATH.search(filename):
                self.finding("private_absolute_code_path", location)
            elif re.search(r"(?i)(?:[a-z]:[\\/].*FieldIntelligenceConsole[\\/])", filename):
                self.canonical_build_path_count += 1
            for child in value.co_consts:
                self.code(child, location, depth + 1)
        elif isinstance(value, str):
            self.content(value.encode("utf-8", errors="replace"), location)
        elif isinstance(value, bytes):
            self.content(value, location)
        elif isinstance(value, (tuple, list, frozenset)):
            for child in value:
                self.code(child, location, depth + 1)

    def zip(self, source, location, depth=0):
        if depth > MAX_DEPTH:
            raise BoundsError("Nested archive depth exceeded")
        # Bound the central directory BEFORE zipfile allocates its member list.
        if isinstance(source, Path):
            length = source.stat().st_size
            with source.open("rb") as stream:
                stream.seek(max(0, length - 65557))
                end = stream.read(65557)
        else:
            length = len(source)
            end = source[-65557:]
        eocd = end.rfind(b"PK\x05\x06")
        if eocd < 0 or len(end) - eocd < 22:
            raise BoundsError("ZIP directory footer missing")
        _, disk, directory_disk, disk_entries, entries, directory_bytes, offset, comment_len = struct.unpack("<4s4H2IH", end[eocd:eocd + 22])
        if disk or directory_disk or entries != disk_entries or entries > MAX_ENTRIES or directory_bytes > 32 * 1024 * 1024:
            raise BoundsError("ZIP directory bounds or multi-disk format unsupported")
        if offset + directory_bytes > length or comment_len != len(end) - eocd - 22:
            raise BoundsError("ZIP directory/footer bounds inconsistent")
        with zipfile.ZipFile(source if isinstance(source, Path) else io.BytesIO(source)) as archive:
            if len(archive.infolist()) != entries:
                raise BoundsError("ZIP entry count inconsistent")
            names = set()
            for info in archive.infolist():
                entry = location + "!" + info.filename
                if info.filename in names:
                    self.finding("duplicate_archive_entry", entry)
                names.add(info.filename)
                self.entry_name(info.filename, entry)
                if info.is_dir():
                    continue
                self.count(info.file_size)
                if info.flag_bits & 1:
                    self.finding("encrypted_unreviewable_entry", entry)
                    continue
                if info.file_size > MAX_RATIO * max(1, info.compress_size):
                    raise BoundsError("Archive compression-ratio limit exceeded")
                if (info.external_attr >> 16) & 0o170000 == 0o120000:
                    self.finding("archive_symlink_requires_review", entry)
                with archive.open(info) as stream:
                    data = stream.read(MAX_ENTRY + 1)
                if len(data) != info.file_size:
                    raise BoundsError("Archive entry size inconsistent")
                self.content(data, entry)
                if data.startswith(b"PK\x03\x04") and info.filename.lower().endswith((".zip", ".jar", ".whl", ".apk")):
                    self.zip(data, entry, depth + 1)
                elif info.filename.lower().endswith((".tar.gz", ".tgz", ".tar")):
                    self.tar(data, entry, info.filename.lower().endswith((".tar.gz", ".tgz")), depth + 1)
                elif info.filename.endswith(".pyc") and len(data) > 16:
                    try:
                        self.code(marshal.loads(data[16:]), entry)
                    except (ValueError, EOFError, TypeError):
                        self.finding("unreviewable_bytecode", entry)

    def tar(self, source, location, compressed=True, depth=0):
        if depth > MAX_DEPTH:
            raise BoundsError("Nested archive depth exceeded")
        if isinstance(source, Path):
            if source.stat().st_size > MAX_ENTRY:
                raise BoundsError("Tar container exceeds 64 MiB input bound")
            data = source.read_bytes()
        else:
            data = source
        if len(data) > MAX_ENTRY:
            raise BoundsError("Tar container exceeds input bound")
        if compressed:
            decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
            expanded = decoder.decompress(data, MAX_ENTRY + 1)
            if len(expanded) > MAX_ENTRY or decoder.unconsumed_tail or not decoder.eof or decoder.unused_data:
                raise BoundsError("Gzip size bound, trailer or concatenated stream unsupported")
            if len(expanded) > MAX_RATIO * max(1, len(data)):
                raise BoundsError("Tar/gzip compression ratio exceeds bound")
            data = expanded
        # The entire tar/PAX parser input is bounded before metadata parsing.
        # Scanning it also covers strings in trailing tar padding, not only members.
        self.count(len(data))  # Conservative aggregate accounting includes headers/padding.
        self.content(data, location + "!tar-container")
        names, member_count = set(), 0
        with tarfile.open(fileobj=io.BytesIO(data), mode="r:") as archive:
            for member in archive:
                member_count += 1
                if member_count > MAX_ENTRIES:
                    raise BoundsError("Tar entry count exceeds bound")
                entry = location + "!" + member.name
                if member.name in names:
                    self.finding("duplicate_archive_entry", entry)
                names.add(member.name)
                self.entry_name(member.name, entry)
                if member.isdir():
                    continue
                if member.issym() or member.islnk():
                    self.finding("tar_link_requires_review", entry)
                    continue
                if not member.isfile():
                    self.finding("tar_device_or_special_entry", entry)
                    continue
                self.count(member.size)
                stream = archive.extractfile(member)
                if stream is None:
                    raise BoundsError("Tar member could not be inspected")
                with stream:
                    payload = stream.read(MAX_ENTRY + 1)
                if len(payload) != member.size:
                    raise BoundsError("Tar member size mismatch")
                self.content(payload, entry)
                lower = member.name.lower()
                if lower.endswith((".zip", ".jar", ".whl", ".apk")):
                    self.zip(payload, entry, depth + 1)
                elif lower.endswith((".tar.gz", ".tgz", ".tar")):
                    self.tar(payload, entry, lower.endswith((".tar.gz", ".tgz")), depth + 1)

    @staticmethod
    def decompress(data, expected=None):
        decoder = zlib.decompressobj()
        result = decoder.decompress(data, MAX_ENTRY + 1)
        if len(result) > MAX_ENTRY or decoder.unconsumed_tail or not decoder.eof:
            raise BoundsError("Compressed entry exceeds its bound")
        if expected is not None and len(result) != expected:
            raise BoundsError("Compressed entry length inconsistent")
        if len(result) > MAX_RATIO * max(1, len(data)):
            raise BoundsError("Compression ratio exceeds bound")
        return result

    def executable(self, path, location):
        from PyInstaller.archive.readers import CArchiveReader
        # Review the installed reader layout and preflight bounds before its TOC load.
        with path.open("rb") as stream:
            cookie_offset = CArchiveReader._find_magic_pattern(stream, CArchiveReader._COOKIE_MAGIC_PATTERN)
            if cookie_offset < 0:
                raise BoundsError("Not a recognizable PyInstaller executable")
            stream.seek(cookie_offset)
            cookie = stream.read(CArchiveReader._COOKIE_LENGTH)
            _, archive_length, toc_offset, toc_length, _, _ = struct.unpack(CArchiveReader._COOKIE_FORMAT, cookie)
            end_offset = cookie_offset + CArchiveReader._COOKIE_LENGTH
            start_offset = end_offset - archive_length
            if start_offset < 0 or toc_length > MAX_TOC or toc_offset + toc_length > archive_length:
                raise BoundsError("PyInstaller archive bounds exceeded")
            stream.seek(start_offset + toc_offset)
            toc_data = stream.read(toc_length)
            position, toc_names, toc_count = 0, set(), 0
            while position < len(toc_data):
                header_size = CArchiveReader._TOC_ENTRY_LENGTH
                header = toc_data[position:position + header_size]
                if len(header) != header_size:
                    raise BoundsError("Truncated PyInstaller TOC entry")
                entry_length, _, _, _, _, kind = struct.unpack(CArchiveReader._TOC_ENTRY_FORMAT, header)
                if entry_length <= header_size or position + entry_length > len(toc_data):
                    raise BoundsError("Invalid PyInstaller TOC entry length")
                name = toc_data[position + header_size:position + entry_length].rstrip(b"\x00").decode("utf-8")
                if name in toc_names and kind != b"o":
                    self.finding("duplicate_archive_entry", location + "!" + name)
                toc_names.add(name)
                position += entry_length
                toc_count += 1
                if toc_count > MAX_ENTRIES:
                    raise BoundsError("PyInstaller TOC entry count exceeded")
        archive = CArchiveReader(str(path))
        if len(archive.toc) > MAX_ENTRIES:
            raise BoundsError("PyInstaller entry count exceeded")
        for option in archive.options:
            self.content(option.encode("utf-8"), location + "!runtime-option")
            if PRIVATE_CODE_PATH.search(option):
                self.finding("private_absolute_runtime_option", location + "!runtime-option")
        for name, (offset, compressed, expanded, flag, kind) in archive.toc.items():
            entry = location + "!" + name
            self.entry_name(name, entry)
            if offset < 0 or compressed < 0 or offset + compressed > archive_length or compressed > MAX_ENTRY:
                raise BoundsError("PyInstaller member offset/size invalid")
            if kind == "z":
                if flag:
                    raise BoundsError("Compressed nested PYZ layout unsupported")
                self.pyz(path, start_offset + offset, compressed, entry)
                continue
            self.count(expanded)
            with path.open("rb") as stream:
                stream.seek(start_offset + offset)
                data = stream.read(compressed)
            data = self.decompress(data, expanded) if flag else data
            if len(data) != expanded:
                raise BoundsError("PyInstaller member length invalid")
            self.content(data, entry)
            if name.endswith(".zip"):
                self.zip(data, entry)
            elif kind in {"s", "m", "M"}:
                try:
                    self.code(marshal.loads(data), entry)
                except (ValueError, EOFError, TypeError):
                    self.finding("unreviewable_embedded_script", entry)

    def pyz(self, path, offset, length, location):
        with path.open("rb") as stream:
            stream.seek(offset)
            header = stream.read(12)
            if len(header) != 12 or header[:4] != b"PYZ\x00":
                raise BoundsError("PYZ header invalid")
            if header[4:8] != importlib.util.MAGIC_NUMBER:
                raise BoundsError("PYZ Python version does not match the inspection interpreter")
            toc_offset = struct.unpack("!i", header[8:12])[0]
            if toc_offset < 12 or toc_offset > length or length - toc_offset > MAX_TOC:
                raise BoundsError("PYZ TOC bounds invalid")
            stream.seek(offset + toc_offset)
            # Unlike the upstream reader's marshal.load(file), this deserializes
            # only a pre-bounded TOC buffer, never the remainder of the EXE.
            raw_toc = marshal.loads(stream.read(length - toc_offset))
        if not isinstance(raw_toc, (list, tuple, dict)) or len(raw_toc) > MAX_ENTRIES:
            raise BoundsError("PYZ entry count exceeded")
        toc = dict(raw_toc)
        if len(toc) != len(raw_toc):
            self.finding("duplicate_pyz_module", location)
        for name, (kind, relative, size) in toc.items():
            entry = location + "!" + name
            if kind == 3:  # Namespace package; no data.
                continue
            if relative < 12 or size < 0 or relative + size > toc_offset or size > MAX_ENTRY:
                raise BoundsError("PYZ member bounds invalid")
            with path.open("rb") as stream:
                stream.seek(offset + relative)
                data = stream.read(size)
            expanded = self.decompress(data)
            self.count(len(expanded))
            self.content(expanded, entry)
            try:
                self.code(marshal.loads(expanded), entry)
            except (ValueError, EOFError, TypeError):
                self.finding("unreviewable_pyz_bytecode", entry)

    def asset(self, path):
        size = path.stat().st_size
        if size > MAX_ASSET:
            raise BoundsError("Asset exceeds 1 GiB scan limit")
        identity = file_hash(path)
        suffix = path.suffix.lower()
        location = "asset:" + identity
        before = len(self.findings)
        if suffix in {".zip", ".whl", ".apk", ".jar"}:
            self.zip(path, location)
        elif path.name.lower().endswith((".tar.gz", ".tgz", ".tar")):
            self.tar(path, location, path.name.lower().endswith((".tar.gz", ".tgz")))
        elif suffix == ".exe":
            self.executable(path, location)
        elif suffix in {".json", ".txt", ".md"}:
            if size > MAX_ENTRY:
                raise BoundsError("Text artifact exceeds bound")
            self.count(size)
            self.content(path.read_bytes(), location)
        else:
            raise BoundsError("Unsupported artifact format; explicit review adapter required")
        return {"name": path.name, "sha256": identity, "bytes": size, "findings": len(self.findings) - before}


def self_test():
    with tempfile.TemporaryDirectory(prefix="fic-artifact-audit-test-") as folder:
        root = Path(folder)
        candidate = "ghp_" + "A" * 36
        bad = root / "synthetic.zip"
        with zipfile.ZipFile(bad, "w") as archive:
            archive.writestr("config.txt", candidate)
            archive.writestr("private/history.db", b"SQLite format 3\x00" + b"synthetic")
        audit = Audit()
        audit.asset(bad)
        rendered = json.dumps(audit.findings)
        assert candidate not in rendered and "history.db" not in rendered
        assert {f["category"] for f in audit.findings} >= {"github_token", "bundled_database", "sqlite_database_magic"}
        clean = root / "synthetic-clean.zip"
        with zipfile.ZipFile(clean, "w") as archive:
            archive.writestr("shared/fixtures/contacts.vcf", "BEGIN:VCARD\nVERSION:4.0\nFN:Synthetic Example\nEND:VCARD")
            archive.writestr("notices/LICENSE.txt", "Synthetic notice; upstream notices require contextual review")
        audit = Audit(); audit.asset(clean)
        assert not audit.findings and audit.synthetic_fixture_entries == 1 and audit.upstream_notice_entries == 1
        bomb = root / "synthetic-bomb.zip"
        with zipfile.ZipFile(bomb, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("zeros.txt", b"0" * 1000000)
        try:
            Audit().asset(bomb)
        except BoundsError:
            pass
        else:
            raise AssertionError("Compression ratio limit not enforced")
        audit = Audit()
        audit.code(compile("value=1", "C:/Users/SyntheticPrivateName/private.py", "exec"), "synthetic-code")
        assert audit.findings[0]["category"] == "private_absolute_code_path"
        tar_path = root / "synthetic-unsafe.tar.gz"
        with tarfile.open(tar_path, "w:gz") as archive:
            item = tarfile.TarInfo("../private.txt"); item.size = len(candidate)
            archive.addfile(item, io.BytesIO(candidate.encode()))
            link = tarfile.TarInfo("synthetic-link"); link.type = tarfile.SYMTYPE; link.linkname = "/private/not-read"
            archive.addfile(link)
            device = tarfile.TarInfo("synthetic-device"); device.type = tarfile.CHRTYPE
            archive.addfile(device)
        audit = Audit(); audit.asset(tar_path)
        assert {f["category"] for f in audit.findings} >= {"unsafe_archive_path", "github_token", "tar_link_requires_review", "tar_device_or_special_entry"}
        assert candidate not in json.dumps(audit.findings)
        tar_bomb = root / "synthetic-tar-bomb.tar.gz"
        with tarfile.open(tar_bomb, "w:gz") as archive:
            item = tarfile.TarInfo("zeros.txt"); item.size = 1000000
            archive.addfile(item, io.BytesIO(b"0" * item.size))
        try:
            Audit().asset(tar_bomb)
        except BoundsError:
            pass
        else:
            raise AssertionError("Tar/gzip expansion bound not enforced")
    print(json.dumps({"self_tests": 6, "status": "PASS", "secret_redaction": "PASS", "database_detection": "PASS", "bounds": "PASS", "private_code_path": "PASS", "tar_paths_links_devices": "PASS", "tar_expansion": "PASS"}))
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact", type=Path, action="append")
    parser.add_argument("--source-sha")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if not args.artifact or args.output is None or not re.fullmatch(r"[0-9a-f]{40}", args.source_sha or ""):
        parser.error("Explicit artifact paths, full source SHA and output receipt are required")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({"status": "NOT_COMPLETED", "source_sha": args.source_sha}), "utf-8")
    audit, assets, incomplete = Audit(), [], []
    for path in args.artifact:
        try:
            assets.append(audit.asset(path))
        except (OSError, ValueError, EOFError, TypeError, KeyError, zipfile.BadZipFile, tarfile.TarError, zlib.error, ImportError, struct.error):
            incomplete.append({"artifact_name_fingerprint_sha256": digest(path.name.encode()), "reason": "unsupported, malformed or bounded scan incomplete"})
    result = {"status": "BLOCKED_FINDINGS" if audit.findings or incomplete else "REQUIRES_REVIEW",
              "source_sha": args.source_sha, "artifacts": assets, "findings": audit.findings, "incomplete": incomplete,
              "entries_scanned": audit.entries, "uncompressed_bytes_scanned": audit.bytes, "code_objects_inspected": audit.code_objects,
              "synthetic_fixture_entries": audit.synthetic_fixture_entries, "upstream_notice_entries": audit.upstream_notice_entries,
              "canonical_build_path_count": audit.canonical_build_path_count,
              "manual_review_required": True,
              "limitations": ["Pattern matching is not complete proof of absence of secrets or personal data.",
                              "Source SHA is caller-supplied; matching clean-build provenance must be verified separately against these asset hashes.",
                              "Synthetic-fixture and upstream-notice path classifications require contextual review.",
                              "Binary strings and embedded Python code are inspected, not executed; arbitrary encrypted/unknown formats are not certified.",
                              "Candidate values and member paths are redacted; local review can correlate entry fingerprints."]}
    args.output.write_text(json.dumps(result, indent=2), "utf-8")
    print(json.dumps({"status": result["status"], "artifacts": len(assets), "findings": len(audit.findings), "incomplete": len(incomplete)}))
    return 2 if audit.findings or incomplete else 0


if __name__ == "__main__":
    raise SystemExit(main())
