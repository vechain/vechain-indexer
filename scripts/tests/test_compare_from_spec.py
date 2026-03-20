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


if __name__ == "__main__":
    unittest.main()
