from concurrent.futures import CancelledError
import json
import threading
import time
import unittest
from unittest.mock import patch
from field_intelligence_console import ValidationError
from field_intelligence_console.providers import OpenCageGeocoder


def response(kind="building", count=1):
    return json.dumps({"status": {"code": 200}, "results": [{"formatted": "Synthetic address",
        "geometry": {"lat": 0, "lng": 0}, "components": {"_type": kind}} for _ in range(count)]}).encode()


class ProviderTests(unittest.TestCase):
    def setUp(self):
        # Keep focused retry tests fast; separate test proves rate behavior itself.
        self.rate = patch("field_intelligence_console.providers._rate_limit")
        self.rate.start()

    def tearDown(self):
        self.rate.stop()

    def test_no_consent_or_key_never_calls_transport(self):
        calls = []
        provider = OpenCageGeocoder(transport=lambda *args: calls.append(args))
        for kwargs in ({}, {"consent": True}):
            with self.assertRaises(ValidationError):
                provider.lookup("Synthetic address", "", **kwargs)
        self.assertEqual([], calls)

    def test_candidates_not_auto_selected_cache_and_unknown_precision(self):
        calls = []
        def transport(address, key, timeout):
            calls.append((address, timeout))
            return 200, {}, response("unknown", 2)
        provider = OpenCageGeocoder(transport=transport)
        result = provider.lookup("Synthetic address", "synthetic-not-a-real-key", consent=True)
        self.assertEqual(2, len(result))
        self.assertEqual("approximate", result[0]["precision"])
        self.assertIn("OpenStreetMap", result[0]["attribution"])
        provider.lookup("Synthetic address", "synthetic-not-a-real-key", consent=True)
        self.assertEqual(1, len(calls))

    def test_cancel_propagates_and_late_worker_is_bounded(self):
        cancel = threading.Event()
        cancel.set()
        provider = OpenCageGeocoder(transport=lambda *args: (200, {}, response()))
        with self.assertRaises(CancelledError):
            provider.lookup("Synthetic address", "synthetic", consent=True, cancel=cancel)
        release = threading.Event()
        def blocked(*args):
            release.wait(1)
            return 200, {}, response()
        provider = OpenCageGeocoder(transport=blocked, timeout=0.05)
        try:
            with self.assertRaisesRegex(ValidationError, "timed out"):
                provider.lookup("Synthetic address", "synthetic", consent=True)
            with self.assertRaisesRegex(ValidationError, "busy"):
                provider.lookup("Synthetic other", "synthetic", consent=True)
        finally:
            release.set()
            time.sleep(0.03)

    def test_retry_is_bounded_and_honors_long_retry_after(self):
        attempts = []
        def transient(*args):
            attempts.append(1)
            return (429, {"Retry-After": "1"}, b"") if len(attempts) == 1 else (200, {}, response())
        with patch("field_intelligence_console.providers._wait") as wait:
            result = OpenCageGeocoder(transport=transient).lookup("Synthetic", "synthetic", consent=True)
            self.assertEqual(1, len(result))
            self.assertEqual(2, len(attempts))
            wait.assert_called_once_with(1.0, None)
        with self.assertRaisesRegex(ValidationError, "longer retry"):
            OpenCageGeocoder(transport=lambda *a: (503, {"Retry-After": "3600"}, b"")).lookup("Synthetic", "synthetic", consent=True)

    def test_empty_success_missing_results_and_errors(self):
        self.assertEqual([], OpenCageGeocoder(transport=lambda *a: (200, {}, response(count=0))).lookup("Synthetic", "synthetic", consent=True))
        for result in ((200, {}, b"{}"), (200, {}, b"[]"), (200, {}, b"null"), (406, {}, b"private ignored")):
            with self.assertRaises(ValidationError) as caught:
                OpenCageGeocoder(transport=lambda *a: result).lookup("Synthetic", "synthetic-key", consent=True)
            self.assertNotIn("synthetic-key", str(caught.exception))
            self.assertNotIn("private", str(caught.exception))

    def test_process_rate_spacing_and_cancellation(self):
        self.rate.stop()
        try:
            import field_intelligence_console.providers as module
            with patch.object(module, "_NEXT_REQUEST", 0), patch.object(module.time, "monotonic", return_value=100), patch.object(module, "_wait") as wait:
                module._rate_limit(None)
                module._rate_limit(None)
                self.assertEqual([0, 1.0], [call.args[0] for call in wait.call_args_list])
                cancel = threading.Event()
                cancel.set()
                with self.assertRaises(CancelledError):
                    module._rate_limit(cancel)
        finally:
            self.rate.start()


if __name__ == "__main__":
    unittest.main()
