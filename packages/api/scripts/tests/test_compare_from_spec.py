import importlib.util
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


MODULE = load_module("compare_from_spec", SCRIPTS_DIR / "compare_from_spec.py")


class CompareFromSpecTest(unittest.TestCase):
    def test_galaxy_member_level_overview_ignores_tie_ordering(self) -> None:
        operation = MODULE.Operation(
            path="/api/v1/b3tr/galaxy-members/level-overview",
            method="GET",
        )
        test_case = MODULE.TestCase(
            operation=operation,
            query_params={"level": "ALL"},
            label="GET /api/v1/b3tr/galaxy-members/level-overview",
        )
        baseline = [
            {"level": "EARTH", "totalNFTs": 89079},
            {"level": "MOON", "totalNFTs": 759},
            {"level": "VENUS", "totalNFTs": 189},
            {"level": "JUPITER", "totalNFTs": 51},
            {"level": "SATURN", "totalNFTs": 49},
            {"level": "MERCURY", "totalNFTs": 49},
            {"level": "MARS", "totalNFTs": 24},
        ]
        candidate = [
            {"level": "EARTH", "totalNFTs": 89079},
            {"level": "MOON", "totalNFTs": 759},
            {"level": "VENUS", "totalNFTs": 189},
            {"level": "JUPITER", "totalNFTs": 51},
            {"level": "MERCURY", "totalNFTs": 49},
            {"level": "SATURN", "totalNFTs": 49},
            {"level": "MARS", "totalNFTs": 24},
        ]

        with patch.object(MODULE, "fetch_json", side_effect=[baseline, candidate]):
            result = MODULE.execute_test_case(
                test_case,
                endpoints=[
                    ("baseline", "https://baseline.example.com"),
                    ("candidate", "https://candidate.example.com"),
                ],
                common_headers={},
                timeout=5,
                insecure=False,
                cafile=None,
                ignored_paths=set(),
                unordered_lists=False,
            )

        self.assertTrue(result.all_match)
        self.assertEqual(result.diffs["baseline vs candidate"], [])

    def test_matching_404s_are_not_reported_as_regressions(self) -> None:
        operation = MODULE.Operation(path="/api/v1/b3tr/richlist/{address}", method="GET")
        test_case = MODULE.TestCase(
            operation=operation,
            path_params={"address": "0xeb0c565f69557481c6c7fa347cae273128a0996e"},
            query_params={"scope": "ALL"},
            label="GET /api/v1/b3tr/richlist/{address}",
        )
        not_found = MODULE.HttpResponseError(
            status_code=404,
            reason="Not Found",
            body=None,
            url="https://example.com/api/v1/b3tr/richlist/0xeb0c565f69557481c6c7fa347cae273128a0996e?scope=ALL",
        )

        with patch.object(MODULE, "fetch_json", side_effect=[not_found, not_found]):
            result = MODULE.execute_test_case(
                test_case,
                endpoints=[
                    ("baseline", "https://baseline.example.com"),
                    ("candidate", "https://candidate.example.com"),
                ],
                common_headers={},
                timeout=5,
                insecure=False,
                cafile=None,
                ignored_paths=set(),
                unordered_lists=False,
            )

        self.assertTrue(result.all_match)
        self.assertFalse(result.has_mismatch)
        self.assertEqual(result.status_codes, {"baseline": 404, "candidate": 404})
        self.assertEqual(result.errors, {})

    def test_matching_404s_ignore_volatile_error_fields(self) -> None:
        operation = MODULE.Operation(path="/api/v1/b3tr/richlist/{address}", method="GET")
        test_case = MODULE.TestCase(
            operation=operation,
            path_params={"address": "0xeb0c565f69557481c6c7fa347cae273128a0996e"},
            query_params={"scope": "ALL"},
            label="GET /api/v1/b3tr/richlist/{address}",
        )
        baseline_error = MODULE.HttpResponseError(
            status_code=404,
            reason="Not Found",
            body={
                "id": "20b2ad69-9eb4-4a28-bfdd-290cee4e40a2",
                "timestamp": 1774866165,
                "message": "Not Found",
                "path": "/api/v1/b3tr/richlist/0xeb0c565f69557481c6c7fa347cae273128a0996e",
            },
            url="https://baseline.example.com",
        )
        candidate_error = MODULE.HttpResponseError(
            status_code=404,
            reason="Not Found",
            body={
                "id": "505e863f-b6b7-4c69-bc75-e56ae2b5d17c",
                "timestamp": 1774866166,
                "message": "Not Found",
                "path": "/api/v1/b3tr/richlist/0xeb0c565f69557481c6c7fa347cae273128a0996e",
            },
            url="https://candidate.example.com",
        )

        with patch.object(MODULE, "fetch_json", side_effect=[baseline_error, candidate_error]):
            result = MODULE.execute_test_case(
                test_case,
                endpoints=[
                    ("baseline", "https://baseline.example.com"),
                    ("candidate", "https://candidate.example.com"),
                ],
                common_headers={},
                timeout=5,
                insecure=False,
                cafile=None,
                ignored_paths=set(),
                unordered_lists=False,
            )

        self.assertTrue(result.all_match)
        self.assertFalse(result.has_mismatch)
        self.assertEqual(result.status_codes, {"baseline": 404, "candidate": 404})
        self.assertEqual(result.diffs["baseline vs candidate"], [])

    def test_different_http_statuses_are_reported_as_differences(self) -> None:
        operation = MODULE.Operation(path="/api/v1/b3tr/richlist/{address}", method="GET")
        test_case = MODULE.TestCase(
            operation=operation,
            path_params={"address": "0xeb0c565f69557481c6c7fa347cae273128a0996e"},
            query_params={"scope": "ALL"},
            label="GET /api/v1/b3tr/richlist/{address}",
        )
        not_found = MODULE.HttpResponseError(
            status_code=404,
            reason="Not Found",
            body=None,
            url="https://baseline.example.com",
        )
        server_error = MODULE.HttpResponseError(
            status_code=500,
            reason="Internal Server Error",
            body=None,
            url="https://candidate.example.com",
        )

        with patch.object(MODULE, "fetch_json", side_effect=[not_found, server_error]):
            result = MODULE.execute_test_case(
                test_case,
                endpoints=[
                    ("baseline", "https://baseline.example.com"),
                    ("candidate", "https://candidate.example.com"),
                ],
                common_headers={},
                timeout=5,
                insecure=False,
                cafile=None,
                ignored_paths=set(),
                unordered_lists=False,
            )

        self.assertFalse(result.all_match)
        self.assertIn(
            ("status", "status code mismatch: 404 != 500"),
            result.diffs["baseline vs candidate"],
        )


class ToleratedDriftTest(unittest.TestCase):
    """End-to-end behavior of the abs/rel numeric tolerance through execute_test_case."""

    def _run(self, baseline, candidate, **tol):
        operation = MODULE.Operation(
            path="/api/v1/b3tr/richlist/{address}", method="GET"
        )
        test_case = MODULE.TestCase(
            operation=operation,
            path_params={"address": "0xeb0c565f69557481c6c7fa347cae273128a0996e"},
            query_params={"scope": "ALL"},
            label="GET /api/v1/b3tr/richlist/{address}",
        )
        with patch.object(MODULE, "fetch_json", side_effect=[baseline, candidate]):
            return MODULE.execute_test_case(
                test_case,
                endpoints=[
                    ("baseline", "https://baseline.example.com"),
                    ("candidate", "https://candidate.example.com"),
                ],
                common_headers={},
                timeout=5,
                insecure=False,
                cafile=None,
                ignored_paths=set(),
                unordered_lists=False,
                **tol,
            )

    def test_tolerated_only_does_not_fail_the_run(self):
        # Mirrors the real richlist drift: totalHolders and rank ±1, topPercentage
        # tiny float drift. With abs=1 these should classify as tolerated drift.
        baseline = {
            "rank": 2581489,
            "totalHolders": 2581488,
            "topPercentage": 0.059771728553454445,
        }
        candidate = {
            "rank": 2581488,
            "totalHolders": 2581487,
            "topPercentage": 0.059771751707446134,
        }
        result = self._run(baseline, candidate, num_abs_tolerance=1.0)

        self.assertFalse(result.all_match, "tolerated drift is not a strict match")
        self.assertFalse(
            result.has_mismatch, "tolerated diffs must not count as a mismatch"
        )
        self.assertTrue(result.has_tolerated)
        self.assertTrue(result.tolerated_only)
        # The whole point: the run still passes.
        self.assertTrue(result.effective_pass)

        tolerated_paths = {
            p for p, _ in result.tolerated_diffs["baseline vs candidate"]
        }
        self.assertEqual(
            tolerated_paths,
            {"root.rank", "root.totalHolders", "root.topPercentage"},
        )

    def test_drift_outside_tolerance_still_fails(self):
        baseline = {"totalHolders": 100}
        candidate = {"totalHolders": 1000}
        result = self._run(baseline, candidate, num_abs_tolerance=1.0)

        self.assertTrue(result.has_mismatch)
        self.assertFalse(result.tolerated_only)
        self.assertFalse(result.effective_pass)

    def test_large_integer_drift_above_2_to_53_not_silently_tolerated(self):
        # Past 2**53 a naive float-cast tolerance would lose precision and
        # potentially mark large diffs as tolerated. With Decimal-based math,
        # a 1000-unit drift must remain a hard diff under abs=1.
        baseline = {"balance": 2**53}
        candidate = {"balance": 2**53 + 1000}
        result = self._run(baseline, candidate, num_abs_tolerance=1.0)

        self.assertTrue(
            result.has_mismatch,
            "1000-unit drift at uint53+ scale must not be silently tolerated",
        )
        self.assertFalse(result.effective_pass)

    def test_uint256_neighbour_drift_is_tolerated(self):
        # Two uint256-scale ints that differ by exactly 1 -- legitimate chain-tip
        # drift -- must be classified as tolerated under abs=1.
        baseline = {"supply": 1_000_000_000_000_000_000_000_000_001}
        candidate = {"supply": 1_000_000_000_000_000_000_000_000_002}
        result = self._run(baseline, candidate, num_abs_tolerance=1.0)

        self.assertFalse(result.has_mismatch)
        self.assertTrue(result.tolerated_only)
        self.assertTrue(result.effective_pass)

    def test_strict_match_when_tolerance_disabled(self):
        # With tolerance disabled (defaults), the same drift case fails.
        baseline = {"rank": 2581489, "totalHolders": 2581488}
        candidate = {"rank": 2581488, "totalHolders": 2581487}
        result = self._run(baseline, candidate)

        self.assertTrue(result.has_mismatch)
        self.assertFalse(result.effective_pass)


if __name__ == "__main__":
    unittest.main()
