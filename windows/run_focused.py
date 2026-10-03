"""Run real workstation tests and emit only sanitized counts for CI artifacts."""
import argparse
import json
from pathlib import Path
import unittest

parser = argparse.ArgumentParser()
parser.add_argument("--receipt", type=Path, required=True)
args = parser.parse_args()
suite = unittest.defaultTestLoader.discover(str(Path(__file__).parent), pattern="test_workstation.py")
result = unittest.TextTestRunner(verbosity=2).run(suite)
receipt = {"tests": result.testsRun, "failures": len(result.failures), "errors": len(result.errors),
           "skipped": len(result.skipped), "status": "PASS" if result.wasSuccessful() and result.testsRun else "FAIL"}
args.receipt.parent.mkdir(parents=True, exist_ok=True)
args.receipt.write_text(json.dumps(receipt, indent=2), encoding="utf-8")
raise SystemExit(0 if receipt["status"] == "PASS" else 1)
