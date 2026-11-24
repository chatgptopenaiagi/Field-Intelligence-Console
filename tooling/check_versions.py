"""Reject inconsistent release identities before expensive packaging starts."""
from pathlib import Path
import argparse
import ast
import json
import re
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def constant(path, name):
    tree = ast.parse((ROOT / path).read_text("utf-8"))
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == name for t in node.targets):
            return ast.literal_eval(node.value)
    raise ValueError("Missing release constant: " + path + ":" + name)


def check(allow_development=False):
    app = constant("python/src/field_intelligence_console/models.py", "APP_VERSION")
    release, separator, iteration = app.partition(".dev")
    if separator and (not allow_development or not re.fullmatch(r"[1-9][0-9]*", iteration)):
        raise ValueError("Release checks require a version without a development suffix")
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)-beta\.(\d+)", release)
    if not match:
        raise ValueError("Expected explicit BETA version")
    major, minor, patch, beta = map(int, match.groups())
    py = f"{major}.{minor}.{patch}b{beta}"
    if separator:
        py += ".dev" + iteration
    numeric = f"{major}.{minor}.{patch}.{beta}"
    expected = [("python/src/field_intelligence_console/__init__.py", "__version__", py),
                ("windows/fic_windows.py", "VERSION", app)]
    if not separator:
        expected += [("tooling/finalize_release.py", "VERSION", app),
                     ("tooling/finalize_release.py", "PYTHON_VERSION", py)]
    for path, name, value in expected:
        if constant(path, name) != value:
            raise ValueError("Release version mismatch: " + path)
    project = tomllib.loads((ROOT / "python/pyproject.toml").read_text("utf-8"))
    if project["project"]["version"] != py:
        raise ValueError("Wheel metadata version mismatch")
    cli = (ROOT / "python/src/field_intelligence_console/cli.py").read_text()
    if re.search(r'action="version", version="([^"]+)"', cli)[1] != py:
        raise ValueError("CLI version mismatch")
    android = (ROOT / "app/build.gradle.kts").read_text()
    if re.search(r'versionName\s*=\s*"([^"]+)"', android)[1] != app:
        raise ValueError("Android version name mismatch")
    code = int(re.search(r"versionCode\s*=\s*(\d+)", android)[1])
    if code != 3 + beta:
        raise ValueError("Android version code must preserve the documented v4 beta mapping")
    info = ast.parse((ROOT / "windows/version-info.txt").read_text())
    fixed = [n for n in ast.walk(info) if isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id == "FixedFileInfo"][0]
    for key in ("filevers", "prodvers"):
        value = next(ast.literal_eval(k.value) for k in fixed.keywords if k.arg == key)
        if value != (major, minor, patch, beta):
            raise ValueError("Windows fixed binary version mismatch")
    strings = {ast.literal_eval(n.args[0]): ast.literal_eval(n.args[1]) for n in ast.walk(info)
               if isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id == "StringStruct"}
    if strings["FileVersion"] != numeric or strings["ProductVersion"] != app:
        raise ValueError("Windows descriptive version mismatch")
    print(json.dumps(dict(status="PASS", application=app, python=py, android_code=code, windows=numeric)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-development", action="store_true")
    check(parser.parse_args().allow_development)
