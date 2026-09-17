import importlib.util
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


MODULE = load_module("check_sync_gate", SCRIPTS_DIR / "check_sync_gate.py")


def checkpoints(**schemas):
    return [{"schema": name, "blockNumber": block} for name, block in schemas.items()]


class SyncGateTest(unittest.TestCase):
    def test_a_small_lag_behind_the_chain_is_allowed(self) -> None:
        self.assertEqual(
            MODULE.compare(checkpoints(blocks=990, history=1000), best_block=1000, max_gap=100), []
        )

    def test_a_single_lagging_indexer_is_named(self) -> None:
        problems = MODULE.compare(
            checkpoints(blocks=999, history=400), best_block=1000, max_gap=100
        )
        self.assertEqual(problems, ["history: 600 blocks behind the chain head"])

    def test_an_indexer_that_has_written_nothing_fails(self) -> None:
        problems = MODULE.compare(checkpoints(blocks=None), best_block=1000, max_gap=100)
        self.assertEqual(problems, ["blocks: has indexed no block"])

    def test_an_empty_status_response_fails_rather_than_passing_vacuously(self) -> None:
        self.assertEqual(
            MODULE.compare([], best_block=1000, max_gap=100),
            ["the candidate reported no indexers at all"],
        )

    def test_an_indexer_at_or_past_the_head_is_fine(self) -> None:
        # Thor was read a moment after the status, so a checkpoint can lead the recorded head.
        self.assertEqual(
            MODULE.compare(checkpoints(blocks=1002), best_block=1000, max_gap=100), []
        )

    def test_every_lagging_indexer_is_reported_in_schema_order(self) -> None:
        problems = MODULE.compare(
            checkpoints(safe=10, blocks=20), best_block=1000, max_gap=100
        )
        self.assertEqual(
            problems,
            [
                "blocks: 980 blocks behind the chain head",
                "safe: 990 blocks behind the chain head",
            ],
        )
