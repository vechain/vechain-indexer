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


def status(**schemas):
    return {name: {"schema": name, "blockNumber": block} for name, block in schemas.items()}


class SyncGateTest(unittest.TestCase):
    def test_a_small_lag_is_allowed(self) -> None:
        self.assertEqual(
            MODULE.compare(status(blocks=1000, history=1000), status(blocks=990, history=1000), 100),
            [],
        )

    def test_a_single_lagging_indexer_is_named(self) -> None:
        problems = MODULE.compare(
            status(blocks=1000, history=1000), status(blocks=999, history=400), 100
        )
        self.assertEqual(problems, ["history: 600 blocks behind the baseline"])

    def test_an_indexer_that_has_written_nothing_fails(self) -> None:
        problems = MODULE.compare(status(blocks=1000), status(blocks=None), 100)
        self.assertEqual(problems, ["blocks: the candidate has indexed no block"])

    def test_an_indexer_only_one_colour_runs_is_reported(self) -> None:
        problems = MODULE.compare(status(blocks=10), status(blocks=10, safe=10), 100)
        self.assertEqual(problems, ["safe: the baseline does not run this indexer"])

    def test_a_candidate_ahead_of_the_baseline_is_fine(self) -> None:
        self.assertEqual(MODULE.compare(status(blocks=1000), status(blocks=1200), 100), [])
