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
from pywinauto.keyboard import send_keys
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
    def wait_for_owned(title, timeout=5):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            matches = owned(title)
            if len(matches) == 1:
                return matches[0]
            time.sleep(0.1)
        raise AssertionError("Expected owned native window did not appear within five seconds")
    checks = []
    result = {"status": "FAIL", "checks": checks, "scope": "Unique copied portable EXE; synthetic isolated profile"}
    user = ctypes.WinDLL("user32", use_last_error=True)
    user.GetForegroundWindow.restype = wintypes.HWND
    user.IsChild.argtypes = [wintypes.HWND, wintypes.HWND]

    def owns_keyboard_focus(window):
        focus = window.get_focus()
        return (user.GetForegroundWindow() == window.handle and focus is not None
                and (focus.handle == window.handle or user.IsChild(window.handle, focus.handle)))

    def native_keys(window, keys, **kwargs):
        if not owns_keyboard_focus(window):
            raise AssertionError("Keyboard target is not a focused control of the owned native window")
        # type_keys() attaches input queues and may reset focus/key state after
        # activation. Send real Windows input without a second focus transition.
        send_keys(keys, pause=kwargs.pop("pause", 0.1), **kwargs)
    def wait_for_closed(title):
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            if not owned(title):
                return
            time.sleep(0.1)
        raise AssertionError("Owned contact editor did not finish closing")

    def activate(window):
        # A database commit is observable before Tk has destroyed its dialog and
        # Windows has completed activation. Verify the target before sending keys.
        window.restore()
        window.set_focus()
        window.click_input(coords=(300, 14))
        deadline = time.monotonic() + 5
        stable_since = None
        while time.monotonic() < deadline:
            if owns_keyboard_focus(window) and window.is_enabled():
                stable_since = stable_since or time.monotonic()
                if time.monotonic() - stable_since >= 0.2:
                    return
            else:
                stable_since = None
            time.sleep(0.05)
        raise AssertionError("Owned target window did not obtain stable native foreground focus")
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
        activate(window)
        window.capture_as_image().save(args.evidence / "native-network.png")
        checks.append("Packaged EXE rendered native Known Network window")
        # Keyboard shortcut is dispatched by Windows to the owned Tk window.
        native_keys(window, "^n")
        editor = wait_for_owned(r"^Contact details .*")
        # Deliberately exercise a compact owned window: fixed bottom-row coordinates
        # were invalid on the hosted runner. Never click outside this window.
        editor.move_window(x=0, y=0, width=900, height=600)
        activate(editor)
        editor.capture_as_image().save(args.evidence / "native-contact-editor.png")
        checks.append("Native Ctrl+N opens real contact editor")
        # Native caption click establishes owned-window mouse interaction. The
        # documented accessibility shortcuts focus and save independently of scale.
        editor.click_input(coords=(300, 14))
        native_keys(editor, "%n")
        native_keys(editor, "^aSynthetic Native UI Contact", with_spaces=True, pause=0.02)
        time.sleep(0.5)
        editor.capture_as_image().save(args.evidence / "native-contact-input.png")
        native_keys(editor, "^{ENTER}")
        persisted = False
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            with Store(data / "fic-workstation.sqlite3") as store:
                created = store.list_entities("Synthetic Native UI Contact")
                persisted = len(created) == 1 and created[0]["display_name"] == "Synthetic Native UI Contact"
            if persisted:
                break
            time.sleep(0.1)
        if not persisted:
            raise AssertionError("Native contact entry/save did not persist the typed value")
        wait_for_closed(r"^Contact details .*")
        with Store(data / "fic-workstation.sqlite3") as store:
            # Remove only this synthetic test-created row so repeatable fixture checks stay unchanged.
            store.delete_entity(created[0]["id"])
        checks.append("Native mouse and keyboard create/save a persisted contact")
        activate(window)
        focus = window.get_focus()
        result["second_editor_focus"] = {
            "native_class": focus.class_name() if focus else None,
            "ctrl_down": bool(ctypes.windll.user32.GetAsyncKeyState(0x11) & 0x8000),
            "alt_down": bool(ctypes.windll.user32.GetAsyncKeyState(0x12) & 0x8000),
            "shift_down": bool(ctypes.windll.user32.GetAsyncKeyState(0x10) & 0x8000),
        }
        native_keys(window, "^n")
        editor = wait_for_owned(r"^Contact details .*")
        activate(editor)
        native_keys(editor, "{ESC}")
        wait_for_closed(r"^Contact details .*")
        checks.append("Native Escape cancels a second contact editor")
        activate(window)
        native_keys(window, "^f")
        native_keys(window, "Synthetic", pause=0.05)
        window.capture_as_image().save(args.evidence / "native-search.png")
        checks.append("Native Ctrl+F and text search interaction")
        native_keys(window, "^a{BACKSPACE}")
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
        for failed_window in owned(r"^Field Intelligence Console .*"):
            try:
                failed_window.capture_as_image().save(args.evidence / "native-failure.png")
            except Exception:
                pass  # Optional synthetic-window evidence must not hide the failure receipt.
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
