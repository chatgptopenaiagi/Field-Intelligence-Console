"""Fail-closed release metadata for one clean, tested three-platform candidate."""
import argparse
from email.parser import BytesParser
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile

VERSION = "4.0.0-beta.7"
PYTHON_VERSION = "4.0.0b7"
PREFIX = f"FieldIntelligenceConsole-{VERSION}"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(path):
    return json.loads(Path(path).read_text("utf-8-sig"))


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def validate_signing(artifacts):
    expected = read(Path(__file__).parents[1] / "shared" / "release-signing.json")["android_testing_certificate_sha256"]
    require(len(artifacts) == 2 and {a.get("variant") for a in artifacts} == {"debug", "release"},
            "Both signed Android variants required")
    require(all(a.get("certificate_sha256") == expected and a.get("signature_verified") is True for a in artifacts),
            "Retained testing certificate mismatch")


def validate_receipts(android, windows, python, sha):
    for name, receipt, dirty in (("Android", android, "source_dirty"),
                                  ("Windows", windows, "worktree_dirty"),
                                  ("Python", python, "source_dirty")):
        require(receipt.get("status") == "PASS", name + " gate did not pass")
        require(receipt.get("source_sha") == sha, name + " source SHA mismatch")
        require(receipt.get(dirty) is False, name + " gate was not a clean candidate")
    units = {item["variant"]: item for item in android.get("unit", [])}
    require(set(units) == {"Debug", "Release"}, "Both Android unit variants required")
    for unit in units.values():
        require(unit.get("status") == "PASS" and unit.get("tests", 0) >= 97
                and all(unit.get(k) == 0 for k in ("failures", "errors", "skipped")), "Android unit evidence incomplete")
    require(len(android.get("lint", [])) == 2 and all(x.get("errors") == 0 and x.get("status") == "PASS"
            for x in android["lint"]), "Both Android lint variants required")
    runtime = android.get("runtime_validation", {})
    require(runtime.get("status") == "PASS" and runtime.get("tests", 0) >= 6,
            "Actual Android emulator runtime gate required")
    require(windows.get("focused_tests", 0) >= 14 and windows.get("packaged_widget_checks", 0) >= 28
            and windows.get("native_checks", 0) >= 7, "Packaged Windows GUI evidence incomplete")
    require(python.get("unit_tests", 0) >= 47 and python.get("unit_failures") == 0
            and python.get("unit_skipped") == 0
            and python.get("isolated_wheel"), "Installed-wheel evidence incomplete")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("candidate-dir", "tag", "source-sha", "android-receipt", "windows-receipt", "python-receipt", "sbom", "notices-dir"):
        parser.add_argument("--" + key, required=True)
    args = parser.parse_args()
    require(re.fullmatch(r"[0-9a-f]{40}", args.source_sha), "Full source commit required")
    require(args.tag == "v" + VERSION, "Unexpected release tag/version mapping")
    android, windows, python = (read(p) for p in (args.android_receipt, args.windows_receipt, args.python_receipt))
    validate_receipts(android, windows, python, args.source_sha)
    candidate = Path(args.candidate_dir).resolve(strict=True)
    names = ["FieldIntelligenceConsole-BETA-Android.apk", "FieldIntelligenceConsole-BETA-Windows-x64.exe",
             f"field_intelligence_console-{PYTHON_VERSION}-py3-none-any.whl",
             f"field_intelligence_console-{PYTHON_VERSION}.tar.gz", PREFIX + "-source.zip"]
    paths = {name: candidate / name for name in names}
    require(all(path.is_file() and path.stat().st_size > 0 for path in paths.values()), "Required platform/source asset missing")
    scan = read(Path(args.android_receipt).parent / "artifact-scan.json")
    require(scan.get("status") == "REQUIRES_REVIEW" and scan.get("source_sha") == args.source_sha
            and scan.get("findings") == [] and scan.get("incomplete") == [], "Artifact scan incomplete or blocked")
    scanned = {item["name"]: item for item in scan.get("artifacts", [])}
    require(all(scanned.get(name, {}).get("sha256") == digest(path) for name, path in paths.items()),
            "Artifact scan does not cover these exact assets")
    debug = next((x for x in android["artifacts"] if x["variant"] == "debug"), {})
    validate_signing(android["artifacts"])
    require(debug.get("package") == "com.fieldconsole.debug" and debug.get("version") == VERSION
            and debug.get("version_code") == 3 + int(VERSION.split("-beta.")[1]) and debug.get("signature_verified") is True,
            "Android identity/version/signature mismatch")
    require(digest(paths[names[0]]) == debug.get("sha256"), "APK differs from tested artifact")
    pe = read(Path(args.windows_receipt).parent / "pe-metadata.json")
    require(pe.get("machine") == "AMD64" and pe.get("subsystem") == "Windows GUI"
            and pe.get("file_version") == "4.0.0.7" and pe.get("product_version") == VERSION
            and digest(paths[names[1]]) == pe.get("sha256"), "EXE differs from tested AMD64 GUI artifact")
    py_assets = {x["name"]: x for x in python["artifacts"]}
    for name in names[2:4]:
        require(digest(paths[name]) == py_assets.get(name, {}).get("sha256"), "Python package differs from tested artifact")
    with zipfile.ZipFile(paths[names[2]]) as wheel:
        metadata = wheel.read(f"field_intelligence_console-{PYTHON_VERSION}.dist-info/WHEEL").decode()
        require("Root-Is-Purelib: true" in metadata and "Tag: py3-none-any" in metadata, "Wheel tags mismatch")
        package = BytesParser().parsebytes(wheel.read(f"field_intelligence_console-{PYTHON_VERSION}.dist-info/METADATA"))
        require(package["Name"] == "field-intelligence-console" and package["Version"] == PYTHON_VERSION
                and package["Requires-Python"] == ">=3.11" and package["License-Expression"] == "MIT"
                and package.get_all("Requires-Dist") == ["jsonschema<5,>=4.26"], "Wheel package metadata mismatch")
        for schema in ("network-v1.schema.json", "field-report-v1.schema.json"):
            require(any(n.endswith("schemas/" + schema) for n in wheel.namelist()), "Wheel schema missing")
        require(any(n.endswith(".dist-info/licenses/LICENSE") for n in wheel.namelist()), "Wheel license missing")
    with zipfile.ZipFile(paths[names[4]]) as source:
        require(source.comment.decode("ascii") == args.source_sha, "Source ZIP is not git archive of the tested commit")
        require(not any(n.startswith("/") or ".." in Path(n).parts for n in source.namelist()), "Unsafe source archive member")
    bom = read(args.sbom)
    require(bom.get("bomFormat") == "CycloneDX" and bom.get("components"), "Actual SBOM required")
    properties = {p["name"]: p["value"] for p in bom["metadata"]["component"].get("properties", [])}
    require(properties.get("fic:source-sha") == args.source_sha, "SBOM source mismatch")
    notices = Path(args.notices_dir) / "THIRD_PARTY_NOTICES.txt"
    require(notices.is_file() and notices.stat().st_size > 0, "Third-party notices required")
    inventory_path = Path(args.notices_dir) / "artifact-inventory.json"
    built_inventory = read(inventory_path)
    require(built_inventory.get("source_sha") == args.source_sha, "Artifact inventory source mismatch")
    inventoried = {item["name"]: item for item in built_inventory.get("artifacts", [])}
    require(all(inventoried.get(name, {}).get("sha256") == digest(path) for name, path in paths.items()),
            "Generated inventory does not match all candidate binaries and source")
    for source, name in ((Path(args.sbom), "SBOM.cdx.json"), (notices, "THIRD_PARTY_NOTICES.txt"),
                         (inventory_path, "artifact-inventory.json")):
        target = candidate / name
        if source.resolve() != target.resolve():
            shutil.copyfile(source, target)
        paths[name] = target
    notes = f"""# FIELD INTELLIGENCE CONSOLE | NEW BETA RELEASE

BETA {VERSION} for the private repository.

ONE TAP. INSTANT AWARENESS.

Source: `{args.source_sha}`; tag: `{args.tag}`. Android package identities and
recovered baseline history are preserved. Includes local Known Network import,
preview/cancel/confirm, manual management, qualified geographic association queries,
deliberate VCF/CSV/FIC JSON export and shared field report exchange on Android,
Windows and the independently installed Python core. Saved addresses never prove
current physical presence. NETWORK details/counts are excluded from ordinary reports.

Assets: Android testing APK (debug signing), portable Windows x64 GUI (unsigned),
Python wheel/sdist, exact-source archive, SBOM, notices, manifest and checksums.
No developer toolchain is needed to run the EXE. Verify SHA256SUMS before use.
Install Android with `FieldIntelligenceConsole-BETA-Android.apk` (Android 10+).
Run `FieldIntelligenceConsole-BETA-Windows-x64.exe` on Windows x64; no installation
or developer tools are required. Install the wheel in a Python 3.11+ environment
with `python -m pip install field_intelligence_console-{PYTHON_VERSION}-py3-none-any.whl`,
then run `fic --help` and `fic diagnostics`. See the included source README for
workflows, data location, export and uninstall instructions.

Changes from the recovered v3 application: bounded external requests and geocoding,
non-destructive Room migrations, honest provider/cache status, deliberate emergency
dialer actions, local Known Network management and shared report exchange, plus
the standalone Windows workstation and Python core. Beta7 validates the Android version code for this candidate and updates
the hosted emulator runtime gate after beta6 timed out.

Verified candidate: 97 or more Android tests per variant, both assemblies/lint,
six or more isolated-emulator instrumentation tests, 47 or more Python tests and
clean-wheel workflows, fourteen Windows focused tests, 28 packaged GUI checks and seven
native mouse/keyboard/restart checks. Exact counts and toolchains are in the manifest.

Limitations: no physical Android-device/ARM64 runtime validation; static APK ABIs
are separately reported. Reconstructed historical v1 migration evidence is not an
authentic old binary/schema export; exported v2 migration and fail-closed behavior
are tested. Lint advisory version/target warnings remain, unsuppressed. Databases
are not encrypted; exports contain deliberately selected private data. VCF supports
the documented bounded UTF-8 v3/v4 subset, not every historical variant. No live
provider key was used in testing. Optional providers require explicit configuration
and consent; disconnected AI/weather/CAD hooks are not claimed operational. No
production signing/certification, live tracking, enterprise connector, graph database,
or PyPI publication is included. Repeatable scripts do not establish
byte-for-byte reproducibility.
"""
    (candidate / "RELEASE_NOTES.md").write_text(notes, "utf-8")
    paths["RELEASE_NOTES.md"] = candidate / "RELEASE_NOTES.md"
    inventory = [{"name": name, "bytes": path.stat().st_size, "sha256": digest(path)} for name, path in sorted(paths.items())]
    manifest = {"schema_version": 1, "product": "Field Intelligence Console", "version": VERSION,
                "tag": args.tag, "source_sha": args.source_sha, "visibility": "private", "release_kind": "BETA prerelease",
                "artifacts": inventory, "android": android, "windows": windows, "python": python,
                "signing": {"android": "debug/testing", "windows": windows.get("signing", "NotSigned")},
                "artifact_scan": {"findings": 0, "incomplete": 0,
                                  "contextual_review": "Coordinator review required before publication; scanner alone is not authorization"},
                "physical_device": "NOT_EXECUTED", "reproducibility": "Not compared; traceable repeatable scripts only"}
    (candidate / "release-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", "utf-8")
    paths["release-manifest.json"] = candidate / "release-manifest.json"
    (candidate / "SHA256SUMS.txt").write_text("".join(f"{digest(path)}  {name}\n" for name, path in sorted(paths.items())), "ascii")
    print(json.dumps({"status": "PASS", "source_sha": args.source_sha, "assets": len(paths) + 1}))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        print("Release finalization BLOCKED: " + str(error))
        raise SystemExit(1)
