"""Optional, consent-gated OpenCage forward geocoding. Never called by import/store."""
from collections import OrderedDict
from concurrent.futures import CancelledError
from copy import deepcopy
from email.utils import parsedate_to_datetime
from datetime import datetime, timezone
import hashlib
import json
import queue
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, HTTPRedirectHandler, build_opener
from .models import ValidationError, now_iso
from .geography import distance_m

ATTRIBUTION = "Geocoding by OpenCage; data © OpenStreetMap contributors and other open sources (https://opencagedata.com/credits)"
_RATE_LOCK = threading.Lock()
_NEXT_REQUEST = 0.0
_TRANSPORT_SLOT = threading.BoundedSemaphore(1)


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # Never redirect address or key to another origin.


def _http(address, key, timeout):
    query = urlencode({"q": address, "key": key, "limit": 5, "no_annotations": 1, "no_record": 1})
    request = Request("https://api.opencagedata.com/geocode/v1/json?" + query,
                      headers={"User-Agent": "FieldIntelligenceConsole/4.0.0-beta.5", "Accept": "application/json"})
    opener = build_opener(_NoRedirect())
    try:
        with opener.open(request, timeout=timeout) as response:
            body = response.read(1024 * 1024 + 1)
            if len(body) > 1024 * 1024:
                raise ValidationError("Geocoder response exceeds size limit")
            return response.status, dict(response.headers), body
    except HTTPError as exc:
        # No raw provider body or URL in errors, even when it contains the key.
        result = exc.code, dict(exc.headers), b""
        exc.close()
        return result
    except (URLError, OSError, TimeoutError):
        raise ValidationError("Geocoder network request failed") from None


def _cancelled(cancel):
    if cancel is not None and cancel.is_set():
        raise CancelledError("Geocoding cancelled")


def _wait(seconds, cancel):
    if cancel is not None:
        if cancel.wait(seconds):
            raise CancelledError("Geocoding cancelled")
    else:
        time.sleep(seconds)


def _rate_limit(cancel):
    global _NEXT_REQUEST
    with _RATE_LOCK:
        delay = max(0, _NEXT_REQUEST - time.monotonic())
        _wait(delay, cancel)
        _cancelled(cancel)
        _NEXT_REQUEST = time.monotonic() + 1.0


class OpenCageGeocoder:
    """Session cache only. Candidates are suggestions; caller must deliberately select one.

    A bounded daemon transport worker enforces the caller deadline even if platform DNS
    or socket cancellation is uncooperative. At most one worker exists process-wide;
    while a timed-out worker still runs, new calls fail busy rather than leak threads.
    A timeout/cancel cannot guarantee that an already-sent address stops being processed.
    """
    def __init__(self, *, transport=None, timeout=10.0):
        if not 0 < timeout <= 10:
            raise ValueError("Geocoder timeout must be within 10 seconds")
        self._transport = transport or _http
        self._timeout = timeout
        self._cache = OrderedDict()
        self._cache_lock = threading.Lock()

    def _request(self, address, key, cancel):
        if not _TRANSPORT_SLOT.acquire(blocking=False):
            raise ValidationError("Geocoder is busy finishing a prior request; try later")
        result = queue.Queue(maxsize=1)
        def work():
            try:
                result.put((True, self._transport(address, key, self._timeout)))
            except Exception:
                result.put((False, None))
            finally:
                _TRANSPORT_SLOT.release()
        threading.Thread(target=work, name="fic-geocoder", daemon=True).start()
        deadline = time.monotonic() + self._timeout
        while True:
            _cancelled(cancel)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise ValidationError("Geocoder timed out; coordinates remain unresolved")
            try:
                ok, value = result.get(timeout=min(0.05, remaining))
                _cancelled(cancel)
                if not ok:
                    raise ValidationError("Geocoder request failed; no address was resolved")
                return value
            except queue.Empty:
                continue

    def lookup(self, address, api_key, *, consent=False, cancel=None):
        if not consent:
            raise ValidationError("Explicit consent is required before sending an address to OpenCage")
        if not isinstance(api_key, str) or not api_key.strip():
            raise ValidationError("OpenCage is unavailable without a user-supplied key")
        if not isinstance(address, str) or not 2 <= len(address.strip()) <= 1024:
            raise ValidationError("Select a bounded address before geocoding")
        _cancelled(cancel)
        key = hashlib.sha256(address.encode("utf-8")).hexdigest()
        with self._cache_lock:
            cached = self._cache.get(key)
            if cached and time.monotonic() - cached[0] < 3600:
                self._cache.move_to_end(key)
                return deepcopy(cached[1])
        for attempt in range(2):
            _rate_limit(cancel)
            status, headers, body = self._request(address, api_key, cancel)
            if status == 200:
                candidates = self._decode(body)
                with self._cache_lock:
                    self._cache[key] = (time.monotonic(), candidates)
                    self._cache.move_to_end(key)
                    while len(self._cache) > 256:
                        self._cache.popitem(last=False)
                return deepcopy(candidates)
            if attempt == 0 and (status == 429 or 500 <= status <= 599):
                retry = next((str(v) for k, v in headers.items() if k.lower() == "retry-after"), "2")
                try:
                    delay = float(retry)
                except ValueError:
                    try:
                        delay = (parsedate_to_datetime(retry) - datetime.now(timezone.utc)).total_seconds()
                    except (ValueError, TypeError, OverflowError):
                        delay = 2.0
                if not 0 <= delay <= 60:
                    raise ValidationError("Provider requests a longer retry delay; try later")
                _wait(max(1.0, delay), cancel)
                continue
            raise ValidationError(f"OpenCage query failed (HTTP {status}); no address was resolved")
        raise ValidationError("Geocoding failed")

    @staticmethod
    def _decode(body):
        if len(body) > 1024 * 1024:
            raise ValidationError("Geocoder response exceeds size limit")
        try:
            data = json.loads(body)
            if data.get("status", {}).get("code") != 200 or not isinstance(data.get("results"), list):
                raise ValueError()
            candidates = []
            for item in data["results"][:5]:
                lat, lon = item["geometry"]["lat"], item["geometry"]["lng"]
                distance_m(lat, lon, lat, lon)  # shared finite/range validation
                components = item.get("components", {})
                kind = components.get("_type", "")
                precision = {"building": "building", "road": "street", "city": "locality",
                             "town": "locality", "village": "locality", "state": "region", "country": "country"}.get(kind, "approximate")
                formatted = item["formatted"]
                if not isinstance(formatted, str) or len(formatted) > 16384:
                    raise ValueError()
                candidates.append({"formatted": formatted, "latitude": lat, "longitude": lon,
                    "precision": precision, "provider": "OpenCage", "resolved_at": now_iso(), "attribution": ATTRIBUTION})
            return candidates
        except (ValueError, TypeError, KeyError, AttributeError, UnicodeError, RecursionError):
            raise ValidationError("Malformed geocoder response; no address was resolved") from None
