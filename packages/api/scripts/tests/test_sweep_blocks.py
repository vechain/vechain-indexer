import importlib.util
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


MODULE = load_module("sweep_blocks", SCRIPTS_DIR / "sweep_blocks.py")

BASELINE = "https://baseline.example.com"
CANDIDATE = "https://candidate.example.com"
ENDPOINTS = [("baseline", BASELINE), ("candidate", CANDIDATE)]


def block(number, transactions=(), gas_used=100):
    return {"number": number, "id": f"0x{number:08x}", "gasUsed": gas_used,
            "transactions": list(transactions)}


class WindowSelectionTest(unittest.TestCase):
    def test_genesis_the_forks_and_the_head_are_always_swept(self) -> None:
        chosen = MODULE.windows(head=1000, count=6, size=50, seed=1, forks=[300, 600])
        self.assertIn((0, 50), chosen)
        self.assertIn((300, 50), chosen)
        self.assertIn((600, 50), chosen)
        self.assertIn((950, 50), chosen)

    def test_the_same_seed_picks_the_same_windows(self) -> None:
        self.assertEqual(
            MODULE.windows(10_000, 8, 50, 7, []), MODULE.windows(10_000, 8, 50, 7, [])
        )
        self.assertNotEqual(
            MODULE.windows(10_000, 8, 50, 7, []), MODULE.windows(10_000, 8, 50, 8, [])
        )

    def test_a_fork_too_close_to_the_head_is_skipped(self) -> None:
        self.assertNotIn((980, 50), MODULE.windows(head=1000, count=6, size=50, seed=1, forks=[980]))


class SweepWindowTest(unittest.TestCase):
    def _sweep(self, responses, **kwargs):
        def stub(url, headers, timeout, context):
            if url not in responses:
                raise AssertionError(f"unexpected request {url}")
            return responses[url], {}

        with patch.object(MODULE, "fetch_json_with_headers", side_effect=stub):
            return MODULE.sweep_window(
                ENDPOINTS, kwargs.pop("window", (10, 2)), page_size=kwargs.pop("page_size", 2),
                timeout=5, ctx=None, ignored=kwargs.pop("ignored", []),
                max_transactions=kwargs.pop("max_transactions", 10),
            )

    def _page(self, blocks):
        return {"data": blocks, "pagination": {"hasNext": False}}

    def test_a_matching_window_reports_nothing(self) -> None:
        page = self._page([block(11, ["0xtx"]), block(10)])
        tx = {"id": "0xtx", "paid": "0x1"}
        report = self._sweep({
            f"{BASELINE}/api/v1/blocks?from=11&size=2": page,
            f"{CANDIDATE}/api/v1/blocks?from=11&size=2": page,
            f"{BASELINE}/api/v1/transactions/0xtx?expanded=false": tx,
            f"{CANDIDATE}/api/v1/transactions/0xtx?expanded=false": tx,
            f"{BASELINE}/api/v1/transactions/0xtx?expanded=true": tx,
            f"{CANDIDATE}/api/v1/transactions/0xtx?expanded=true": tx,
        })
        self.assertEqual(report["findings"], [])
        self.assertEqual(report["blocks_compared"], 2)
        self.assertEqual(report["transactions_compared"], 2)

    def test_a_block_the_candidate_never_indexed_is_a_finding(self) -> None:
        report = self._sweep({
            f"{BASELINE}/api/v1/blocks?from=11&size=2": self._page([block(11), block(10)]),
            f"{CANDIDATE}/api/v1/blocks?from=11&size=2": self._page([block(10)]),
        })
        self.assertEqual(report["findings"], [{"block": 11, "diffs": [["root", "missing on the candidate"]]}])

    def test_a_hex_spelling_difference_inside_a_transaction_is_caught(self) -> None:
        page = self._page([block(10, ["0xtx"])])
        report = self._sweep({
            f"{BASELINE}/api/v1/blocks?from=10&size=1": page,
            f"{CANDIDATE}/api/v1/blocks?from=10&size=1": page,
            f"{BASELINE}/api/v1/transactions/0xtx?expanded=false": {"paid": "0x0"},
            f"{CANDIDATE}/api/v1/transactions/0xtx?expanded=false": {"paid": "0x00"},
            f"{BASELINE}/api/v1/transactions/0xtx?expanded=true": {"paid": "0x0"},
            f"{CANDIDATE}/api/v1/transactions/0xtx?expanded=true": {"paid": "0x0"},
        }, window=(10, 1), page_size=1)
        self.assertEqual(len(report["findings"]), 1)
        self.assertEqual(report["findings"][0]["transaction"], "0xtx")
        self.assertIn("0x0", json.dumps(report["findings"][0]["diffs"]))

    def test_an_ignored_path_silences_a_known_difference(self) -> None:
        report = self._sweep({
            f"{BASELINE}/api/v1/blocks?from=10&size=1": self._page([block(10, gas_used=100)]),
            f"{CANDIDATE}/api/v1/blocks?from=10&size=1": self._page([block(10, gas_used=101)]),
        }, window=(10, 1), page_size=1, ignored=["root.gasUsed"])
        self.assertEqual(report["findings"], [])
