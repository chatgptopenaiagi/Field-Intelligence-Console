"""Release receipts must never promote preliminary or incomplete evidence."""
from copy import deepcopy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("finalize_release", Path(__file__).parents[1] / "finalize_release.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseGuards(unittest.TestCase):
    def setUp(self):
        self.sha = "a" * 40
        self.android = dict(status="PASS", source_sha=self.sha, source_dirty=False,
                            unit=[dict(variant=v, status="PASS", tests=97, failures=0, errors=0, skipped=0) for v in ("Debug", "Release")],
                            lint=[dict(status="PASS", errors=0) for _ in range(2)],
                            runtime_validation=dict(status="PASS", tests=6))
        self.windows = dict(status="PASS", source_sha=self.sha, worktree_dirty=False,
                            focused_tests=14, packaged_widget_checks=28, native_checks=7)
        self.python = dict(status="PASS", source_sha=self.sha, source_dirty=False,
                           unit_tests=45, unit_failures=0, unit_skipped=0, isolated_wheel="Executed")

    def check(self):
        release.validate_receipts(self.android, self.windows, self.python, self.sha)

    def test_complete_receipt_contract(self):
        self.check()

    def test_retained_testing_signing_identity(self):
        expected = release.read(Path(__file__).parents[2] / "shared" / "release-signing.json")["android_testing_certificate_sha256"]
        artifacts = [dict(variant=v, certificate_sha256=expected, signature_verified=True) for v in ("debug", "release")]
        release.validate_signing(artifacts)
        artifacts[0]["certificate_sha256"] = "f" * 64
        with self.assertRaises(ValueError): release.validate_signing(artifacts)

    def test_missing_or_unverified_signing_identity_fails_closed(self):
        with self.assertRaises(ValueError): release.validate_signing([])
        with self.assertRaises(ValueError):
            release.validate_signing([dict(variant=v, signature_verified=True) for v in ("debug", "release")])

    def test_preliminary_dirty_build_cannot_be_released(self):
        for receipt, key in ((self.android, "source_dirty"), (self.windows, "worktree_dirty"), (self.python, "source_dirty")):
            receipt[key] = True
            with self.assertRaises(ValueError): self.check()
            receipt[key] = False

    def test_mixed_commits_fail(self):
        for receipt in (self.android, self.windows, self.python):
            receipt["source_sha"] = "b" * 40
            with self.assertRaises(ValueError): self.check()
            receipt["source_sha"] = self.sha

    def test_unexecuted_runtime_or_skipped_tests_fail(self):
        self.android["runtime_validation"]["status"] = "NOT_EXECUTED"
        with self.assertRaises(ValueError): self.check()
        self.android["runtime_validation"]["status"] = "PASS"
        self.android["unit"][0]["skipped"] = 1
        with self.assertRaises(ValueError): self.check()
        self.android["unit"][0]["skipped"] = 0
        self.python["unit_skipped"] = 1
        with self.assertRaises(ValueError): self.check()

    def test_no_native_gui_or_no_installed_wheel_fails(self):
        self.windows["focused_tests"] = 7
        with self.assertRaises(ValueError): self.check()
        self.windows["focused_tests"] = 14
        self.windows["native_checks"] = 0
        with self.assertRaises(ValueError): self.check()
        self.windows["native_checks"] = 7
        self.python["isolated_wheel"] = None
        with self.assertRaises(ValueError): self.check()

    def test_stale_or_missing_provenance_receipt_fails(self):
        self.python["status"] = "NOT_COMPLETED"
        with self.assertRaises(ValueError): self.check()
        self.python["status"] = "PASS"
        del self.python["source_dirty"]
        with self.assertRaises(ValueError): self.check()


if __name__ == "__main__":
    unittest.main()
