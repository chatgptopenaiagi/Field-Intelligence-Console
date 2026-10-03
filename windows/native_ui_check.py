"""Native mouse/keyboard inspection of only a uniquely copied synthetic FIC EXE."""
import argparse
import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
from pywinauto import Desktop
from field_intelligence_console import Store


def process_image(pid):
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.OpenProcess(0x1000, False, pid)
    if not handle:
        return ""
    try:
        size = wintypes.DWORD(32768)
        buffer = ctypes.create_unicode_buffer(size.value)
        if kernel.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(size)):
            return buffer.value
        return ""
    finally:
        kernel.CloseHandle(handle)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("exe", type=Path)
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    args.evidence.mkdir(parents=True, exist_ok=True)
    profile = Path(tempfile.mkdtemp(prefix="FIC-synthetic-native-"))
    portable = profile / "portable-fic.exe"
    shutil.copy2(args.exe, portable)
    data = profile / "profile"
    receipt = args.evidence / "native-widget-receipt.json"
    env = {key: value for key, value in os.environ.items() if key not in {"PYTHONPATH", "PYTHONHOME", "VIRTUAL_ENV"}}
    process = subprocess.Popen([str(portable), "--ui-test", "--stay-open", "--profile", str(data), "--evidence", str(receipt.resolve())], cwd=profile, env=env)
    desktop = Desktop(backend="win32")
    def owned(title):
        return [w for w in desktop.windows(title_re=title, visible_only=True)
                if os.path.normcase(process_image(w.process_id())) == os.path.normcase(str(portable))]
    checks = []
    result = {"status": "FAIL", "checks": checks, "scope": "Unique copied portable EXE; synthetic isolated profile"}
    try:
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline and not receipt.exists():
            time.sleep(0.2)
        if not receipt.exists() or json.loads(receipt.read_text())["status"] != "PASS":
            raise AssertionError("Packaged widget gate failed before native interaction")
        windows = owned(r"^Field Intelligence Console .*")
        if len(windows) != 1:
            raise AssertionError("Owned FIC window not uniquely identified")
        window = windows[0]
        window.restore()
        window.set_focus()
        time.sleep(0.3)
        window.capture_as_image().save(args.evidence / "native-network.png")
        checks.append("Packaged EXE rendered native Known Network window")
        # Keyboard shortcut is dispatched by Windows to the owned Tk window.
        window.type_keys("^n", pause=0.1)
        time.sleep(0.4)
        dialogs = owned(r"^Contact details .*")
        if len(dialogs) != 1:
            raise AssertionError("Ctrl+N did not open contact editor")
        editor = dialogs[0]
        editor.set_focus()
        editor.capture_as_image().save(args.evidence / "native-contact-editor.png")
        checks.append("Native Ctrl+N opens real contact editor")
        # Real mouse click focuses the first display-name field, then keyboard edits.
        editor.click_input(coords=(360, 108))
        editor.type_keys("^aSynthetic Native UI Contact", with_spaces=True, pause=0.02)
        time.sleep(0.5)
        editor.capture_as_image().save(args.evidence / "native-contact-input.png")
        editor.click_input(coords=(95, 740))
        time.sleep(0.4)
        with Store(data / "fic-workstation.sqlite3") as store:
            created = store.list_entities("Synthetic Native UI Contact")
            if len(created) != 1 or created[0]["display_name"] != "Synthetic Native UI Contact":
                raise AssertionError("Native contact entry/save did not persist the typed value")
            # Remove only this synthetic test-created row so repeatable fixture checks stay unchanged.
            store.delete_entity(created[0]["id"])
        checks.append("Native mouse and keyboard create/save a persisted contact")
        window.set_focus()
        window.type_keys("^n")
        time.sleep(0.3)
        editor = owned(r"^Contact details .*")[0]
        editor.type_keys("{ESC}")
        time.sleep(0.3)
        if owned(r"^Contact details .*"):
            raise AssertionError("Escape did not dismiss editor")
        checks.append("Native Escape cancels a second contact editor")
        window.set_focus()
        window.type_keys("^f")
        window.type_keys("Synthetic", pause=0.05)
        window.capture_as_image().save(args.evidence / "native-search.png")
        checks.append("Native Ctrl+F and text search interaction")
        window.type_keys("^a{BACKSPACE}")
        window.close()
        process.wait(timeout=15)
        checks.append("Native window close exits portable application")
        # New process on the same isolated profile proves restart, not just Store reopen.
        process = subprocess.Popen([str(portable), "--ui-test", "--profile", str(data), "--evidence", str((args.evidence / "native-restart.json").resolve())], cwd=profile, env=env)
        process.wait(timeout=40)
        restarted = json.loads((args.evidence / "native-restart.json").read_text())
        if process.returncode != 0 or restarted["status"] != "PASS":
            raise AssertionError("Packaged process restart failed")
        checks.append("Second packaged process retains data and preferences")
        result["status"] = "PASS"
    except Exception as exc:
        result["failure_type"] = type(exc).__name__
        result["failure"] = str(exc)[:220] if isinstance(exc, AssertionError) else "Native UI operation failed; inspect local development evidence."
    finally:
        for window in owned(r"^(Field Intelligence Console|Contact details).*"):
            try:
                window.close()
            except Exception:
                pass
        result["check_count"] = len(checks)
        (args.evidence / "native-ui.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        # Local-only receipt identifies the owned synthetic profile for cleanup/review.
        (args.evidence / "native-profile-path.txt").write_text(str(profile), encoding="utf-8")
    print(json.dumps(result))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
