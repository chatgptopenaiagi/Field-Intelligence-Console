"""Diagnostics must work offline without a personal profile or database."""
from contextlib import redirect_stdout, redirect_stderr
from io import StringIO
import json
import unittest
from unittest.mock import patch
from field_intelligence_console import __version__
from field_intelligence_console import cli


class DiagnosticsTests(unittest.TestCase):
    def test_diagnostics_never_opens_user_data_or_network(self):
        output = StringIO()
        with patch.object(cli, "Store", side_effect=AssertionError("Database access")), \
             patch("socket.create_connection", side_effect=AssertionError("Network access")), \
             patch.object(cli, "_read", side_effect=AssertionError("User file access")), redirect_stdout(output):
            self.assertEqual(cli.main(["diagnostics"]), 0)
        result = json.loads(output.getvalue())
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["version"], __version__)
        self.assertEqual(result["bundled_schemas"], "PASS")
        self.assertEqual(result["user_data"], "not accessed")

    def test_missing_packaged_schema_fails_without_disclosing_path(self):
        error = StringIO()
        with patch.object(cli.resources, "files", side_effect=FileNotFoundError("synthetic-private-path")), redirect_stderr(error):
            self.assertEqual(cli.main(["diagnostics"]), 2)
        self.assertNotIn("synthetic-private-path", error.getvalue())
