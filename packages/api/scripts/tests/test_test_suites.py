import importlib.util
import json
import re
import sys
import unittest
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


MODULE = load_module("compare_from_spec", SCRIPTS_DIR / "compare_from_spec.py")

# One path per suite, as the OpenAPI spec spells them.
SAMPLE_PATHS = [
    "/api/v1/accounts/overview/{address}",
    "/api/v2/accounts/totals",
    "/api/v1/b3tr/richlist",
    "/api/v1/b3tr/actions/global/overview",
    "/api/v2/b3tr/proposals/results",
    "/api/v1/blocks",
    "/api/v1/contracts/{address}",
    "/api/v1/explorer/block-usage",
    "/api/v1/history/{account}",
    "/api/v2/history/{account}",
    "/api/v1/nfts",
    "/api/v1/nfts/history",
    "/api/v1/safes/owner/{address}",
    "/api/v1/stargate/tokens",
    "/api/v1/transactions",
    "/api/v1/transactions/latest",
    "/api/v1/transfers",
    "/api/v1/transfers/forBlock",
    "/api/v1/validators",
    "/api/v2/validators/slots",
    "/api/v1/vevote/proposal/results",
]


class SuiteTest(unittest.TestCase):
    def setUp(self) -> None:
        self.suites = MODULE.load_suites()

    def test_a_suite_name_resolves_to_its_pattern(self) -> None:
        self.assertEqual(
            MODULE.resolve_path_filter("stargate", self.suites), self.suites["stargate"]
        )

    def test_anything_else_is_taken_as_a_regex(self) -> None:
        self.assertEqual(
            MODULE.resolve_path_filter("/api/v1/b3tr/nav.*", self.suites), "/api/v1/b3tr/nav.*"
        )

    def test_every_sample_path_belongs_to_a_suite(self) -> None:
        for path in SAMPLE_PATHS:
            with self.subTest(path=path):
                self.assertTrue(
                    [name for name, rx in self.suites.items() if re.search(rx, path)],
                    f"{path} is in no suite, so it can only be reached by regex",
                )

    def test_suites_select_their_own_domain_only(self) -> None:
        for name, pattern in self.suites.items():
            matched = [p for p in SAMPLE_PATHS if re.search(pattern, p)]
            with self.subTest(suite=name):
                self.assertTrue(matched, f"{name} matches nothing")
                stem = name.split("-")[0]
                for path in matched:
                    self.assertIn(stem, path)

    def test_transfers_does_not_swallow_treasury_transfers(self) -> None:
        pattern = self.suites["transfers"]
        self.assertIsNone(re.search(pattern, "/api/v1/b3tr/treasury/transfers"))

    def test_blocks_does_not_swallow_the_validator_block_endpoints(self) -> None:
        pattern = self.suites["blocks"]
        self.assertIsNone(re.search(pattern, "/api/v1/validators/blocks/missed"))

    def test_suite_patterns_compile(self) -> None:
        for name, pattern in self.suites.items():
            with self.subTest(suite=name):
                re.compile(pattern)


if __name__ == "__main__":
    unittest.main()
