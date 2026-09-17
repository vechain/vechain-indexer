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


MODULE = load_module("regression_summary", SCRIPTS_DIR / "regression_summary.py")


def _result(status, path="/api/v1/nfts?address=0xabc", **extra):
    base = {
        "label": f"GET {path}",
        "method": "GET",
        "path": path,
        "status": status,
        "status_codes": {"baseline": 200, "candidate": 200},
        "errors": {},
        "diffs": {},
        "tolerated_diffs": {},
    }
    base.update(extra)
    return base


class RenderTest(unittest.TestCase):
    def test_unavailable_and_one_sided_operations_are_called_out(self) -> None:
        report = {
            "summary": {
                "total": 2, "passed": 1, "failed": 0, "unavailable": 1,
                "operations_on_one_side_only": ["GET /api/v1/vevote/proposals/comments"],
            },
            "results": [
                _result("pass"),
                _result("unavailable", "/api/v1/blocks", status_codes={"baseline": 429, "candidate": 429}),
            ],
        }
        text = MODULE.render(report, None, "c", "b")
        self.assertIn("| **Unavailable** | 1 |", text)
        self.assertIn("### Operations declared by one endpoint only", text)
        self.assertIn("### Unavailable", text)
        self.assertIn("baseline: 429", text)


    def test_counts_and_no_data_operations_are_rendered(self) -> None:
        report = {
            "summary": {
                "total": 3, "passed": 1, "vacuous": 1, "tolerated": 0, "deprecated": 0,
                "failed": 1, "operations_without_data": ["GET /api/v1/safes/owner/{address}"],
            },
            "results": [
                _result("pass"),
                _result("vacuous", "/api/v1/safes/owner/0xabc"),
                _result("fail", "/api/v1/blocks", diffs={"baseline vs candidate": [
                    ["root.data[0].gasUsed", "value mismatch: 1 != 2"]]}),
            ],
        }
        text = MODULE.render(report, None, "https://c.example", "https://b.example")
        self.assertIn("| **Vacuous** | 1 |", text)
        self.assertIn("### Operations that compared no data", text)
        self.assertIn("<code>GET /api/v1/safes/owner/{address}</code>", text)
        self.assertIn("### Failed endpoints", text)
        self.assertIn("root.data[0].gasUsed: value mismatch: 1 != 2", text)
        self.assertNotIn("Validator Sampling", text)

    def test_seed_metadata_and_clean_run(self) -> None:
        report = {"summary": {"total": 1, "passed": 1, "failed": 0}, "results": [_result("pass")]}
        seed = {
            "validatorSeed": 1337,
            "sampledValidatorIds": ["0xv1"],
            "seeders": {"seed_transfers": "ok", "seed_safes": "skipped: LookupError: no safe"},
        }
        text = MODULE.render(report, seed, "c", "b")
        self.assertIn("### Validator Sampling", text)
        self.assertIn("- `0xv1`", text)
        self.assertIn("1 of 2 families seeded", text)
        self.assertIn("`seed_safes` — skipped: LookupError: no safe", text)
        self.assertNotIn("seed_transfers", text)
        self.assertIn("All endpoints matched.", text)
