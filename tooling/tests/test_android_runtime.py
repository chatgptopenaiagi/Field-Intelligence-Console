"""Regression coverage for package-service startup races and fail-closed runtime evidence."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("android_runtime", Path(__file__).parents[1] / "android_runtime.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class AndroidRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.logs = []
        self.device = runtime.Emulator("adb", "emulator-5580", self.logs)

    def test_physical_device_is_rejected(self):
        with self.assertRaises(runtime.RuntimeFailure):
            runtime.Emulator("adb", "physical-device", [])

    @patch.object(runtime.time, "sleep")
    def test_boot_flag_alone_does_not_establish_package_readiness(self, sleep):
        with patch.object(self.device, "command", side_effect=[(0, "1"), (1, "Broken pipe"), (0, "1"), (0, "package:/system/framework/framework-res.apk")]) as command:
            self.device.ready()
        self.assertEqual(command.call_count, 4)
        sleep.assert_called_once_with(2)

    @patch.object(runtime.time, "sleep")
    def test_transient_install_rechecks_service_and_preserves_data(self, sleep):
        with patch.object(self.device, "ready") as ready, patch.object(self.device, "command", side_effect=[(1, "Broken pipe (32)"), (0, "Success\n")]) as command:
            self.device.install(Path("synthetic.apk"))
        self.assertEqual(ready.call_count, 2)
        self.assertEqual(command.call_args.args, ("install", "--no-streaming", "-r", "synthetic.apk"))
        self.assertEqual([x["result"] for x in self.logs], ["TRANSIENT", "PASS"])

    def test_signature_failure_never_uninstalls_or_retries(self):
        with patch.object(self.device, "ready"), patch.object(self.device, "command", return_value=(1, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")) as command:
            with self.assertRaisesRegex(runtime.RuntimeFailure, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"):
                self.device.install(Path("synthetic.apk"))
        command.assert_called_once()

    @patch.object(runtime.time, "sleep")
    def test_transient_retry_budget_is_finite(self, sleep):
        with patch.object(self.device, "ready"), patch.object(self.device, "command", return_value=(1, "Broken pipe")) as command:
            with self.assertRaises(runtime.RuntimeFailure):
                self.device.install(Path("synthetic.apk"))
        self.assertEqual(command.call_count, 3)

    def test_incomplete_or_crashed_instrumentation_cannot_pass(self):
        for output in ("OK (5 tests)", "OK (6 tests)\nProcess crashed", "FAILURES", ""):
            with self.subTest(output=output), patch.object(self.device, "command", return_value=(0, output)):
                with self.assertRaises(runtime.RuntimeFailure):
                    self.device.instrument(False)

    def test_real_count_required_and_software_wait_is_explicit(self):
        with patch.object(self.device, "command", return_value=(0, "OK (6 tests)")) as command:
            self.assertEqual(self.device.instrument(True), 6)
        self.assertIn("60000", command.call_args.args)
        self.assertEqual(command.call_args.kwargs["timeout"], 600)

    def test_command_timeout_is_a_failure(self):
        with patch.object(runtime.subprocess, "run", side_effect=subprocess.TimeoutExpired("adb", 30)):
            self.assertEqual(self.device.command("get-state"), (124, "Command timed out"))

    def test_runtime_identity_is_measured(self):
        with patch.object(self.device, "command", side_effect=[(0, "34"), (0, "x86_64")]):
            self.device.identity()
        with patch.object(self.device, "command", side_effect=[(0, "37"), (0, "x86_64")]):
            with self.assertRaises(runtime.RuntimeFailure):
                self.device.identity()

    def test_tampered_artifact_or_mixed_source_fails_before_install(self):
        with tempfile.TemporaryDirectory(prefix="fic-runtime-synthetic-") as owned:
            root = Path(owned)
            (root / "assets").mkdir()
            (root / "android-test").mkdir()
            apk = root / "assets/FieldIntelligenceConsole-BETA-Android.apk"
            tests = root / "android-test/app-debug-androidTest.apk"
            apk.write_bytes(b"synthetic-not-an-apk")
            tests.write_bytes(b"synthetic-not-a-test-apk")
            build = dict(status="BUILD_PASS", source_dirty=False, source_sha="a" * 40,
                         tag="v4.0.0-beta.3", version="4.0.0-beta.3",
                         artifacts=[dict(name=apk.name, sha256=runtime.digest(apk))],
                         instrumentation_apk_sha256=runtime.digest(tests))
            (root / "build-receipt.json").write_text(json.dumps(build))
            self.assertEqual(runtime.candidate(root, "a" * 40, build["tag"]), (apk, tests))
            with self.assertRaises(runtime.RuntimeFailure):
                runtime.candidate(root, "b" * 40, build["tag"])
            apk.write_bytes(b"changed")
            with self.assertRaises(runtime.RuntimeFailure):
                runtime.candidate(root, "a" * 40, build["tag"])


if __name__ == "__main__":
    unittest.main()
