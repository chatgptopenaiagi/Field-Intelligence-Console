"""Run exact candidate APKs on an explicitly owned emulator; never erase app data."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time


HARDWARE_TIMEOUT = 600
SOFTWARE_TIMEOUT = 1800


class RuntimeFailure(RuntimeError):
    pass


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


class Emulator:
    def __init__(self, adb, serial, diagnostics):
        if not re.fullmatch(r"emulator-[0-9]+", serial):
            raise RuntimeFailure("An explicit isolated emulator serial is required")
        self.adb, self.serial, self.diagnostics = str(adb), serial, diagnostics

    def command(self, *args, timeout=30):
        try:
            result = subprocess.run([self.adb, "-s", self.serial, *args],
                                    capture_output=True, text=True, errors="replace", timeout=timeout)
            return result.returncode, result.stdout + result.stderr
        except subprocess.TimeoutExpired:
            return 124, "Command timed out"

    def ready(self, seconds=180):
        # sys.boot_completed can precede a usable package service on software emulators.
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            code, boot = self.command("shell", "getprop", "sys.boot_completed", timeout=10)
            if code == 0 and boot.strip() == "1":
                code, package = self.command("shell", "pm", "path", "android", timeout=15)
                if code == 0 and re.search(r"(?m)^package:/", package):
                    return
            time.sleep(2)
        raise RuntimeFailure("Emulator package service did not become ready")

    def install(self, apk):
        for attempt in range(1, 4):
            self.ready()
            # Avoid the streaming transport involved in the beta2 broken-pipe failure.
            code, output = self.command("install", "--no-streaming", "-r", str(apk), timeout=180)
            success = code == 0 and re.search(r"(?m)^Success\s*$", output) is not None
            transient = bool(re.search(r"Broken pipe|DeadObjectException|device offline|Can't find service|Command timed out", output, re.I))
            self.diagnostics.append({"stage": "install", "apk": Path(apk).name,
                                     "attempt": attempt, "exit_code": code,
                                     "result": "PASS" if success else "TRANSIENT" if transient else "FAIL"})
            if success:
                return
            # Signature, downgrade, storage and other deterministic errors must not be retried away.
            if not transient or attempt == 3:
                failure = re.search(r"INSTALL_FAILED_[A-Z_]+", output)
                raise RuntimeFailure("APK installation failed: " + (failure[0] if failure else "see stage diagnostics"))
            time.sleep(2 * attempt)

    def instrument(self, software):
        args = ["shell", "am", "instrument", "-w"]
        if software:
            args += ["-e", "ficEmulatorMode", "software", "-e", "ficUiWaitMillis", "60000"]
        args += ["-e", "class", "com.fieldconsole.DatabaseMigrationDeviceTest,com.fieldconsole.NetworkUiDeviceTest",
                 "com.fieldconsole.debug.test/androidx.test.runner.AndroidJUnitRunner"]
        code, output = self.command(*args, timeout=SOFTWARE_TIMEOUT if software else HARDWARE_TIMEOUT)
        match = re.search(r"OK \((\d+) tests?\)", output)
        count = int(match[1]) if match else 0
        self.diagnostics.append({"stage": "instrumentation", "exit_code": code, "tests": count})
        if code or count < 6 or re.search(r"FAILURES|INSTRUMENTATION_FAILED|Process crashed", output):
            # These tests use only synthetic data. Keep bounded failure context, stripping host paths.
            safe = re.sub(r"(?i)(?:[a-z]:[\\/]|/(?:home|Users|tmp|opt)/)[^\s]+", "[local-path]", output[-16000:])
            self.diagnostics[-1]["synthetic_test_output"] = safe
            raise RuntimeFailure("Actual APK instrumentation did not pass all required tests")
        return count

    def identity(self):
        sdk_code, sdk = self.command("shell", "getprop", "ro.build.version.sdk")
        abi_code, abi = self.command("shell", "getprop", "ro.product.cpu.abi")
        if sdk_code or abi_code or sdk.strip() != "34" or abi.strip() != "x86_64":
            raise RuntimeFailure("Runtime gate requires its isolated API34 x86_64 emulator")


def candidate(root, source, tag):
    build = json.loads((root / "build-receipt.json").read_text("utf-8-sig"))
    if build.get("status") != "BUILD_PASS" or build.get("source_dirty") is not False or build.get("source_sha") != source or build.get("tag") != tag:
        raise RuntimeFailure("Candidate provenance mismatch")
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+-beta\.[0-9]+", tag) or tag != "v" + build["version"]:
        raise RuntimeFailure("Candidate tag/version mismatch")
    apk = root / "assets" / "FieldIntelligenceConsole-BETA-Android.apk"
    tests = root / "android-test" / "app-debug-androidTest.apk"
    entries = [x for x in build["artifacts"] if x["name"] == apk.name]
    if len(entries) != 1 or digest(apk) != entries[0]["sha256"] or digest(tests) != build["instrumentation_apk_sha256"]:
        raise RuntimeFailure("Downloaded APK hash mismatch")
    return apk, tests


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--software", action="store_true")
    args = parser.parse_args()
    receipt = args.candidate / "android-runtime.json"
    receipt.write_text(json.dumps({"status": "NOT_COMPLETED"}), encoding="utf-8")
    diagnostics = []
    try:
        source = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        if not re.fullmatch(r"[0-9a-f]{40}", source):
            raise RuntimeFailure("Invalid source commit")
        apk, tests = candidate(args.candidate, source, args.tag)
        emulator = Emulator(args.adb, args.serial, diagnostics)
        emulator.identity()
        emulator.install(apk)
        emulator.install(tests)
        count = emulator.instrument(args.software)
        result = dict(status="PASS", source_sha=source, tag=args.tag, tests=count,
                      apk_sha256=digest(apk), instrumentation_apk_sha256=digest(tests),
                      runtime="Isolated API34 AOSP x86_64 emulator", acceleration="off" if args.software else "on",
                      ui_wait_millis=60000 if args.software else 10000,
                      instrumentation_timeout_seconds=SOFTWARE_TIMEOUT if args.software else HARDWARE_TIMEOUT, physical_device="NOT_EXECUTED")
        receipt.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print("Exact APK runtime PASS: " + str(count) + " tests")
        return 0
    except (RuntimeFailure, OSError, KeyError, ValueError, subprocess.SubprocessError) as error:
        # Only our constant diagnostics are safe to upload; arbitrary exception paths are withheld.
        reason = str(error) if isinstance(error, RuntimeFailure) else type(error).__name__
        print("Android runtime FAILED: " + reason)
        diagnostics.append({"stage": "failure", "reason": reason})
        return 1
    finally:
        (args.candidate / "android-runtime-diagnostics.json").write_text(json.dumps(diagnostics, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
