"""Generate a traceable CycloneDX inventory from actual build/installed metadata.

No network access, guessed versions, workspace-wide uploads or private absolute paths.
The Android input must be the current variant's generated AGP lint report model:
AGP 8.5.2 uses lint_report_lint_model/debug/generateDebugLintReportModel/
debug-artifact-dependencies.xml. Never fall back to obsolete incremental outputs.
Its <package> graph is used, never the larger compile/test graph. Windows native files
come from the matching PyInstaller Analysis TOC; pure Python packages come from the
actual packaging environment's installed FIC dependency graph.
"""
import argparse
import ast
from datetime import datetime, timezone
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote
import uuid
import xml.etree.ElementTree as ET
import zipfile


STAGE = "arguments"


def failure_diagnostic(error):
    """Only code locations/types, never exception values or private input paths."""
    frames = []
    trace = error.__traceback__
    while trace is not None:
        if Path(trace.tb_frame.f_code.co_filename).resolve() == Path(__file__).resolve():
            frames.append({"function": trace.tb_frame.f_code.co_name, "line": trace.tb_lineno})
        trace = trace.tb_next
    return {"status": "FAIL", "stage": STAGE, "error_type": type(error).__name__, "code_locations": frames}


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def licenses(entries):
    result = []
    identifiers = {"Apache 2.0": "Apache-2.0", "The Apache Software License, Version 2.0": "Apache-2.0",
                   "Apache License, Version 2.0": "Apache-2.0", "Apache License 2.0": "Apache-2.0",
                   "Apache-2.0": "Apache-2.0", "MIT": "MIT", "MIT License": "MIT"}
    for name, url in entries:
        item = {"id": identifiers[name]} if name in identifiers else {"name": name or "Unspecified license"}
        if url and url.startswith(("https://", "http://")):
            item["url"] = url
        if {"license": item} not in result:
            result.append({"license": item})
    return result


def pom_licenses(pom, cache, seen=None):
    seen = seen or set()
    if pom is None or pom in seen:
        return []
    seen.add(pom)
    root = ET.parse(pom).getroot()
    entries = [(node.findtext("{*}name", ""), node.findtext("{*}url", ""))
               for node in root.findall("{*}licenses/{*}license")]
    if not entries:
        parent = root.find("{*}parent")
        if parent is not None:
            g, a, v = (parent.findtext("{*}" + key, "") for key in ("groupId", "artifactId", "version"))
            candidates = list((cache / g / a / v).glob("*/*.pom"))
            if len(candidates) == 1:
                return pom_licenses(candidates[0], cache, seen)
    return entries


def archive_notices(path):
    found = []
    def read_archive(archive, prefix=""):
        for info in archive.infolist():
            name = info.filename
            basename = name.rsplit("/", 1)[-1].lower()
            if info.file_size > 1024 * 1024:
                continue
            if basename.startswith(("license", "notice", "copying")) or basename in {"al2.0", "lgpl2.1"}:
                text = archive.read(info).decode("utf-8", errors="replace")
                found.append((prefix + name, text))
            elif name == "classes.jar":
                with zipfile.ZipFile(io.BytesIO(archive.read(info))) as nested:
                    read_archive(nested, "classes.jar!")
    with zipfile.ZipFile(path) as archive:
        read_archive(archive)
    return found


def android_inventory(metadata, cache):
    root = ET.parse(metadata).getroot()
    package = root.find("package")
    if package is None:
        raise ValueError("AGP runtime/package graph is missing")
    coordinates = set(filter(None, package.get("roots", "").split(",")))
    coordinates.update(d.get("name", "") for d in package.iter("dependency"))
    components, notices, unresolved = [], [], []
    for coordinate in sorted(coordinates):
        match = re.fullmatch(r"([\w.\-]+):([\w.\-]+):([\w.\-+]+)@(aar|jar)", coordinate)
        if not match:
            raise ValueError("Unsupported resolved artifact coordinate")
        group, name, version, extension = match.groups()
        folder = cache / group / name / version
        paths = list(folder.glob(f"*/{name}-{version}.{extension}"))
        if not paths:
            # Gradle module metadata can publish e.g. animation-release.aar.
            # A unique artifact in the resolved GAV cache is evidence; multiple
            # variant artifacts remain an explicit failure, never a guessed choice.
            paths = [p for p in folder.glob(f"*/*.{extension}")
                     if not any(marker in p.name for marker in ("-sources", "-javadoc", "-samples"))]
        if len(paths) != 1:
            raise ValueError(f"Expected one cached runtime artifact: {group}:{name}:{version}")
        path = paths[0]
        poms = list(folder.glob(f"*/{name}-{version}.pom"))
        entries = pom_licenses(poms[0] if len(poms) == 1 else None, cache)
        purl = f"pkg:maven/{quote(group)}/{quote(name)}@{quote(version)}?type={extension}"
        component = {"type": "library", "bom-ref": purl, "group": group, "name": name, "version": version,
                     "purl": purl, "hashes": [{"alg": "SHA-256", "content": sha(path)}],
                     "properties": [{"name": "fic:target", "value": "android"},
                                    {"name": "fic:evidence", "value": "AGP resolved package graph and cached artifact"}]}
        if entries:
            component["licenses"] = licenses(entries)
        else:
            unresolved.append(group + ":" + name + ":" + version)
        components.append(component)
        notices.append("\n=== " + coordinate + " ===\n" + "\n".join(name + (" — " + url if url else "") for name, url in entries))
        for inner, content in archive_notices(path):
            notices.append("\n--- " + inner + " ---\n" + content)
    return components, notices, unresolved


PYTHON_METADATA = r'''
from importlib import metadata
import json,platform,sys
from pathlib import Path
from packaging.requirements import Requirement
todo=['field-intelligence-console']; seen=set(); packages=[]
while todo:
    requested=todo.pop()
    distribution=metadata.distribution(requested)
    name=distribution.metadata['Name']
    canonical=name.lower().replace('_','-')
    if canonical in seen: continue
    seen.add(canonical)
    dependencies=[]
    for raw in distribution.requires or []:
        requirement=Requirement(raw)
        if requirement.marker is None or requirement.marker.evaluate({'extra':''}):
            todo.append(requirement.name);dependencies.append(requirement.name)
    texts=[]
    for file in distribution.files or []:
        if any(part.lower() in ('licenses','license','license.txt','license.md','copying','notice') for part in Path(file).parts):
            target=distribution.locate_file(file)
            if target.is_file() and target.stat().st_size<=1024*1024:
                texts.append({'name':Path(file).name,'text':target.read_text('utf-8',errors='replace')})
    packages.append({'name':name,'version':distribution.version,'license':distribution.metadata.get('License-Expression') or distribution.metadata.get('License') or '',
                     'license_classifiers':[v for v in distribution.metadata.get_all('Classifier',[]) if v.startswith('License ::')],
                     'dependencies':dependencies,'notices':texts})
import tkinter,ssl,sqlite3
tk=tkinter.Tk();tk.withdraw()
runtime={'python':platform.python_version(),'implementation':platform.python_implementation(),'machine':platform.machine(),
         'tcl':tk.tk.eval('info patchlevel'),'tk':tk.tk.call('package','provide','Tk'),
         'openssl':ssl.OPENSSL_VERSION,'sqlite':sqlite3.sqlite_version,'pyinstaller':metadata.version('pyinstaller')}
tk.destroy()
print(json.dumps({'packages':packages,'runtime':runtime},ensure_ascii=True))
'''


def python_inventory(executable):
    result = subprocess.run([str(executable), "-c", PYTHON_METADATA], capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise ValueError("Could not inspect the actual Windows packaging environment")
    data = json.loads(result.stdout)
    components, notices, edges = [], [], []
    names = {p["name"].lower().replace("_", "-"): p for p in data["packages"]}
    for item in data["packages"]:
        canonical = item["name"].lower().replace("_", "-")
        purl = f"pkg:pypi/{quote(canonical)}@{quote(item['version'])}"
        component = {"type": "library", "bom-ref": purl, "name": item["name"], "version": item["version"], "purl": purl,
                     "properties": [{"name": "fic:target", "value": "python/windows"},
                                    {"name": "fic:evidence", "value": "Installed distribution metadata in packaging environment"}]}
        license_name = item["license"]
        if not license_name and any("MIT License" in value for value in item["license_classifiers"]):
            license_name = "MIT"
        if license_name:
            if len(license_name) < 120:
                component["licenses"] = licenses([(license_name, "")])
            else:
                component["licenses"] = [{"license": {"name": "See supplied distribution license text"}}]
                notices.append("\n=== " + item["name"] + " " + item["version"] + " metadata license ===\n" + license_name)
        components.append(component)
        dependencies = []
        for dependency in item["dependencies"]:
            dep = names[dependency.lower().replace("_", "-")]
            dependencies.append(f"pkg:pypi/{quote(dep['name'].lower().replace('_', '-'))}@{quote(dep['version'])}")
        edges.append({"ref": purl, "dependsOn": sorted(dependencies)})
        for notice in item["notices"]:
            notices.append("\n=== " + item["name"] + " " + item["version"] + ": " + notice["name"] + " ===\n" + notice["text"])
    runtime = data["runtime"]
    for name, version, license_name in (("CPython", runtime["python"], "PSF-2.0 and included component licenses"),
                                         ("Tcl", runtime["tcl"], "Tcl/Tk license; full notice supplied"),
                                         ("Tk", runtime["tk"], "Tcl/Tk license; full notice supplied"),
                                         ("OpenSSL", runtime["openssl"], "Apache-2.0"),
                                         ("SQLite", runtime["sqlite"], "Public domain"),
                                         ("PyInstaller bootloader", runtime["pyinstaller"], "GPL-2.0-or-later with PyInstaller bootloader exception")):
        components.append({"type": "library", "bom-ref": "fic-runtime:" + name.replace(" ", "-") + "@" + version,
            "name": name, "version": version, "licenses": [{"license": {"name": license_name}}],
            "properties": [{"name": "fic:target", "value": "windows"}, {"name": "fic:evidence", "value": "Packaging interpreter/runtime metadata"}]})
    return components, notices, edges, runtime


def native_inventory(toc):
    if toc.stat().st_size > 20 * 1024 * 1024:
        raise ValueError("PyInstaller TOC exceeds bound")
    value = ast.literal_eval(toc.read_text("utf-8"))
    entries = {}
    def walk(item):
        if isinstance(item, (tuple, list)):
            if len(item) == 3 and all(isinstance(v, str) for v in item) and item[2] in {"BINARY", "EXTENSION"}:
                name, source, kind = item
                path = Path(source)
                if not path.is_file():
                    raise ValueError("A bundled native input is unavailable")
                entries[(name, sha(path))] = kind
            else:
                for child in item:
                    walk(child)
    walk(value)
    return [{"type": "file", "bom-ref": "fic-native:" + quote(name, safe="") + ":" + digest,
             "name": name.replace("\\", "/"), "hashes": [{"alg": "SHA-256", "content": digest}],
             "properties": [{"name": "fic:target", "value": "windows"},
                            {"name": "fic:evidence", "value": "PyInstaller Analysis TOC " + kind}]}
            for (name, digest), kind in sorted(entries.items())]


def main():
    global STAGE
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-dependencies", type=Path, required=True)
    parser.add_argument("--gradle-cache", type=Path, required=True)
    parser.add_argument("--python-executable", type=Path, required=True)
    parser.add_argument("--windows-toc", type=Path, required=True)
    parser.add_argument("--windows-notices", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, action="append", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_sha):
        parser.error("source SHA must be a full commit ID")
    print(json.dumps({"inventory_inputs_present": {
        "android_metadata": args.android_dependencies.is_file(),
        "gradle_cache": args.gradle_cache.is_dir(),
        "python_executable": args.python_executable.is_file(),
        "windows_toc": args.windows_toc.is_file(),
        "windows_notices": args.windows_notices.is_dir()}}), flush=True)
    STAGE = "android"
    android, android_notices, unresolved = android_inventory(args.android_dependencies, args.gradle_cache)
    STAGE = "python"
    python, python_notices, edges, runtime = python_inventory(args.python_executable)
    STAGE = "windows_native"
    native = native_inventory(args.windows_toc)
    STAGE = "artifacts"
    artifact_inventory = []
    for path in args.artifact:
        if not path.is_file():
            raise ValueError("An explicit release artifact is missing")
        artifact_inventory.append({"name": path.name, "bytes": path.stat().st_size, "sha256": sha(path)})
    if len({a["name"] for a in artifact_inventory}) != len(artifact_inventory):
        raise ValueError("Release artifact names must be unique")
    components = android + python + native
    root_ref = "fic-source:" + args.source_sha
    edges.append({"ref": root_ref, "dependsOn": sorted(c["bom-ref"] for c in components)})
    bom = {"bomFormat": "CycloneDX", "specVersion": "1.5", "serialNumber": "urn:uuid:" + str(uuid.uuid4()), "version": 1,
           "metadata": {"timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                        "component": {"type": "application", "bom-ref": root_ref, "name": "Field Intelligence Console",
                                      "version": args.version, "properties": [{"name": "fic:source-sha", "value": args.source_sha}]}},
           "components": components, "dependencies": edges,
           "properties": [{"name": "fic:scope", "value": "Resolved Android package graph; installed FIC Python dependencies; packaged Windows native inputs. Build-tool/transitive-source audit is not exhaustive."},
                          {"name": "fic:unresolved-android-license-metadata", "value": json.dumps(unresolved)}]}
    STAGE = "write_inventory"
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "SBOM.cdx.json").write_text(json.dumps(bom, ensure_ascii=True, indent=2) + "\n", "utf-8")
    (args.output / "artifact-inventory.json").write_text(json.dumps({"source_sha": args.source_sha, "artifacts": artifact_inventory, "runtime": runtime}, indent=2) + "\n", "utf-8")
    notices = ["FIC THIRD-PARTY NOTICES\nGenerated from the actual resolved/installed artifacts. Software licenses do not grant rights to imported contact/report data.\n"] + android_notices + python_notices
    for path in sorted(args.windows_notices.glob("*.txt")):
        notices.append("\n=== Windows bundled notice: " + path.name + " ===\n" + path.read_text("utf-8", errors="replace"))
    (args.output / "THIRD_PARTY_NOTICES.txt").write_text("\n".join(notices), "utf-8")
    print(json.dumps({"android_components": len(android), "python_runtime_components": len(python), "windows_native_inputs": len(native),
                      "artifacts": len(artifact_inventory), "unresolved_license_metadata": unresolved}))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile, subprocess.TimeoutExpired) as error:
        print(json.dumps(failure_diagnostic(error)), file=sys.stderr)
        raise SystemExit(1)
