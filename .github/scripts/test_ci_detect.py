#!/usr/bin/env python3
"""Unit tests for the ledger CI change mapping."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("ci_detect.py")
SPEC = importlib.util.spec_from_file_location("ci_detect", MODULE_PATH)
assert SPEC and SPEC.loader
ci_detect = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ci_detect)


class DetectMappingTest(unittest.TestCase):
    def test_docs_only(self) -> None:
        self.assertEqual(ci_detect.classify(["README.md"]), ([], []))

    def test_service_source(self) -> None:
        self.assertEqual(
            ci_detect.classify(["src/ledger/ledgerwriter/src/main/App.java"]),
            (["ledgerwriter"], []),
        )

    def test_docker_input(self) -> None:
        self.assertEqual(
            ci_detect.classify(["src/ledger/transactionhistory/pom.xml"]),
            (["transactionhistory"], ["transactionhistory"]),
        )

    def test_common_workflow(self) -> None:
        expected = (list(ci_detect.SERVICES), list(ci_detect.SERVICES))
        self.assertEqual(ci_detect.classify([".github/workflows/build-and-push.yml"]), expected)

    def test_common_maven_wrapper(self) -> None:
        self.assertEqual(ci_detect.classify(["mvnw"]), (list(ci_detect.SERVICES), []))

    def test_delete_uses_deleted_path(self) -> None:
        paths = ci_detect.paths_from_name_status("D\tsrc/ledger/balancereader/src/main/App.java\n")
        self.assertEqual(ci_detect.classify(paths), (["balancereader"], []))

    def test_rename_checks_old_and_new_paths(self) -> None:
        paths = ci_detect.paths_from_name_status(
            "R100\tsrc/ledger/balancereader/a.java\tsrc/ledger/ledgerwriter/a.java\n"
        )
        self.assertEqual(ci_detect.classify(paths), (["balancereader", "ledgerwriter"], []))

    def test_unknown_path_fails_closed(self) -> None:
        with self.assertRaisesRegex(ValueError, "unclassified paths"):
            ci_detect.classify(["new-unmapped-config.yaml"])


if __name__ == "__main__":
    unittest.main()
