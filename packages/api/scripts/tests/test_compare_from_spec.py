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

BASELINE_URL = "https://baseline.example.com"
CANDIDATE_URL = "https://candidate.example.com"
ENDPOINTS = [("baseline", BASELINE_URL), ("candidate", CANDIDATE_URL)]


def fetch_stub(baseline, candidate, baseline_again=None):
    """Serve by URL, so a re-read of the baseline is answered like the first."""
    seen = {"baseline": 0}

    def stub(url, headers, timeout, context):
        if url.startswith(BASELINE_URL):
            seen["baseline"] += 1
            body = baseline if seen["baseline"] == 1 or baseline_again is None else baseline_again
        else:
            body = candidate
        if isinstance(body, Exception):
            raise body
        return body, {}

    return stub


def run_case(test_case, baseline, candidate, baseline_again=None, **kwargs):
    with patch.object(
        MODULE, "fetch_json_with_headers", side_effect=fetch_stub(baseline, candidate, baseline_again)
    ):
        return MODULE.execute_test_case(
            test_case,
            endpoints=ENDPOINTS,
            common_headers=kwargs.pop("common_headers", {}),
            timeout=kwargs.pop("timeout", 5),
            insecure=kwargs.pop("insecure", False),
            cafile=kwargs.pop("cafile", None),
            ignored_paths=kwargs.pop("ignored_paths", set()),
            unordered_lists=kwargs.pop("unordered_lists", False),
            **kwargs,
        )


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

        result = run_case(test_case, baseline, candidate)

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

        result = run_case(test_case, not_found, not_found)

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

        result = run_case(test_case, baseline_error, candidate_error)

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

        result = run_case(test_case, not_found, server_error)

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
        return run_case(test_case, baseline, candidate, **tol)

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


class PerEndpointIgnorePathsTest(unittest.TestCase):
    """path_overrides[endpoint].ignore_paths flows from JSON into compare_json."""

    def test_generate_test_cases_carries_ignore_paths(self) -> None:
        op = MODULE.Operation(path="/api/v1/transactions/count", method="GET")
        test_values = {
            "path_overrides": {
                "/api/v1/transactions/count": {
                    "ignore_paths": ["root.totalTransactions", "root.totalClauses"],
                }
            }
        }
        cases = MODULE.generate_test_cases(op, test_values, {})
        self.assertEqual(len(cases), 1)
        self.assertEqual(
            cases[0].extra_ignore_paths,
            ["root.totalTransactions", "root.totalClauses"],
        )

    def test_ignore_paths_key_is_not_treated_as_a_parameter(self) -> None:
        # Reserved meta-key: even if an operation had a param named ignore_paths,
        # generate_test_cases must not send it as a query/path/header value.
        param = MODULE.Parameter(name="ignore_paths", location="query")
        op = MODULE.Operation(
            path="/api/v1/whatever", method="GET", parameters=[param]
        )
        test_values = {
            "path_overrides": {
                "/api/v1/whatever": {"ignore_paths": ["root.foo"]}
            }
        }
        cases = MODULE.generate_test_cases(op, test_values, {})
        self.assertEqual(cases[0].query_params, {})
        self.assertEqual(cases[0].extra_ignore_paths, ["root.foo"])

    def test_execute_test_case_applies_extra_ignore_paths(self) -> None:
        # End-to-end: known-drift fields on transactions/count must be silenced
        # by the per-endpoint override.
        op = MODULE.Operation(path="/api/v1/transactions/count", method="GET")
        tc = MODULE.TestCase(
            operation=op,
            label="GET /api/v1/transactions/count",
            extra_ignore_paths=[
                "root.totalTransactions",
                "root.totalClauses",
            ],
        )
        baseline = {
            "totalTransactions": "157134427",
            "totalClauses": "587555970",
            "totalRevertedTransactions": "20281900",
            "totalRevertedClauses": "21849300",
        }
        candidate = {
            "totalTransactions": "157134443",
            "totalClauses": "587555986",
            "totalRevertedTransactions": "20281900",
            "totalRevertedClauses": "21849300",
        }
        result = run_case(tc, baseline, candidate)
        self.assertTrue(result.all_match)
        self.assertFalse(result.has_mismatch)

    def test_wildcard_ignore_path_silences_richlist_balance_drift(self) -> None:
        op = MODULE.Operation(path="/api/v1/b3tr/richlist", method="GET")
        tc = MODULE.TestCase(
            operation=op,
            label="GET /api/v1/b3tr/richlist",
            extra_ignore_paths=["root.data[*].balance"],
        )
        baseline = {
            "data": [
                {"address": "0xaaa", "balance": "100", "rank": 1},
                {"address": "0xbbb", "balance": "50", "rank": 2},
            ]
        }
        candidate = {
            "data": [
                {"address": "0xaaa", "balance": "101", "rank": 1},
                {"address": "0xbbb", "balance": "51", "rank": 2},
            ]
        }
        result = run_case(tc, baseline, candidate)
        self.assertTrue(result.all_match)

    def test_ignore_paths_as_string_is_wrapped_not_split(self) -> None:
        # If a JSON author writes ignore_paths as a single string instead of a
        # list, we must wrap it — not fall into list("root.data") which would
        # yield a list of characters and silently produce nonsense patterns.
        op = MODULE.Operation(path="/api/v1/transactions/count", method="GET")
        test_values = {
            "path_overrides": {
                "/api/v1/transactions/count": {"ignore_paths": "root.totalTransactions"}
            }
        }
        cases = MODULE.generate_test_cases(op, test_values, {})
        self.assertEqual(cases[0].extra_ignore_paths, ["root.totalTransactions"])

    def test_ignore_paths_drops_non_string_and_empty_entries(self) -> None:
        op = MODULE.Operation(path="/api/v1/transactions/count", method="GET")
        test_values = {
            "path_overrides": {
                "/api/v1/transactions/count": {
                    "ignore_paths": ["root.a", None, "", 0, "root.b"]
                }
            }
        }
        cases = MODULE.generate_test_cases(op, test_values, {})
        self.assertEqual(cases[0].extra_ignore_paths, ["root.a", "root.b"])

    def test_ignore_paths_none_yields_empty_list(self) -> None:
        op = MODULE.Operation(path="/api/v1/transactions/count", method="GET")
        test_values = {
            "path_overrides": {
                "/api/v1/transactions/count": {"ignore_paths": None}
            }
        }
        cases = MODULE.generate_test_cases(op, test_values, {})
        self.assertEqual(cases[0].extra_ignore_paths, [])

    def test_non_ignored_field_still_fails_alongside_wildcard_ignore(self) -> None:
        # If wildcard silences balance but ranks disagree, we must still fail.
        op = MODULE.Operation(path="/api/v1/b3tr/richlist", method="GET")
        tc = MODULE.TestCase(
            operation=op,
            label="GET /api/v1/b3tr/richlist",
            extra_ignore_paths=["root.data[*].balance"],
        )
        baseline = {"data": [{"balance": "100", "rank": 1}]}
        candidate = {"data": [{"balance": "101", "rank": 2}]}
        result = run_case(tc, baseline, candidate)
        self.assertTrue(result.has_mismatch)
        rank_diffs = [
            (p, m)
            for p, m in result.diffs["baseline vs candidate"]
            if p == "root.data[0].rank"
        ]
        self.assertEqual(len(rank_diffs), 1)


if __name__ == "__main__":
    unittest.main()


class CaseGenerationTest(unittest.TestCase):
    """Base case, one variant per optional filter, extra cases per configured list."""

    def _op(self):
        return MODULE.Operation(
            path="/api/v1/transfers/from",
            method="GET",
            parameters=[
                MODULE.Parameter(name="address", location="query", required=True),
                MODULE.Parameter(name="tokenAddress", location="query"),
                MODULE.Parameter(name="eventType", location="query", enum=["VET", "NFT"]),
                MODULE.Parameter(name="size", location="query", schema_type="integer", default=20),
                MODULE.Parameter(name="cursor", location="query"),
            ],
        )

    def test_base_case_sends_required_configured_and_defaulted_params_only(self) -> None:
        cases = MODULE.generate_test_cases(self._op(), {"parameters": {"address": ["0xabc"]}}, {})
        self.assertEqual(cases[0].query_params, {"address": "0xabc", "size": 20})

    def test_each_unconfigured_optional_filter_becomes_its_own_variant(self) -> None:
        cases = MODULE.generate_test_cases(self._op(), {"parameters": {"address": ["0xabc"]}}, {})
        variants = {c.label.split("[+")[1].rstrip("]") for c in cases[1:]}
        # cursor has no generatable value and is skipped rather than sent as junk.
        self.assertEqual(variants, {"tokenAddress=" + MODULE._DEFAULT_CONTRACT, "eventType=VET"})
        for case in cases[1:]:
            self.assertEqual(len(case.query_params), 3, case.label)

    def test_overridden_filter_joins_the_base_case_and_lists_fan_out(self) -> None:
        test_values = {
            "parameters": {"address": ["0xabc", "0xdef"], "tokenAddress": ["0xtoken"]},
            "path_overrides": {"/api/v1/transfers/from": {"eventType": ["NFT"]}},
        }
        cases = MODULE.generate_test_cases(self._op(), test_values, {})
        self.assertEqual(cases[0].query_params, {"address": "0xabc", "eventType": "NFT", "size": 20})
        by_label = {c.label: c.query_params for c in cases}
        self.assertIn("GET /api/v1/transfers/from [address=0xdef]", by_label)
        self.assertEqual(by_label["GET /api/v1/transfers/from [address=0xdef]"]["eventType"], "NFT")
        # A global value feeds the variant without pulling the filter into the base case.
        self.assertEqual(
            by_label["GET /api/v1/transfers/from [+tokenAddress=0xtoken]"]["tokenAddress"], "0xtoken"
        )

    def test_null_override_drops_a_parameter_entirely(self) -> None:
        test_values = {
            "parameters": {"address": ["0xabc"]},
            "path_overrides": {"/api/v1/transfers/from": {"eventType": None}},
        }
        cases = MODULE.generate_test_cases(self._op(), test_values, {})
        self.assertFalse(any("eventType" in c.query_params for c in cases))

    def test_placeholder_spec_examples_lose_to_the_window_heuristics(self) -> None:
        param = MODULE.Parameter(
            name="startTimestamp", location="query", required=True, example=1704143600
        )
        self.assertEqual(MODULE.generate_value(param, {}), 1704067200)
        other = MODULE.Parameter(name="level", location="query", example="Strength")
        self.assertEqual(MODULE.generate_value(other, {}), "Strength")


class VariantValuesTest(unittest.TestCase):
    def test_a_variant_covers_more_than_one_configured_value(self) -> None:
        op = MODULE.Operation(
            path="/api/v1/nfts",
            method="GET",
            parameters=[
                MODULE.Parameter(name="address", location="query", required=True),
                MODULE.Parameter(name="page", location="query", schema_type="integer"),
                MODULE.Parameter(name="size", location="query", schema_type="integer"),
            ],
        )
        values = {"parameters": {"address": ["0xabc"], "page": [0, 3], "size": [20, 150]}}
        cases = MODULE.generate_test_cases(op, values, {})
        self.assertEqual(cases[0].query_params, {"address": "0xabc"})
        self.assertEqual(
            sorted(c.label.split("[+")[1].rstrip("]") for c in cases[1:]),
            ["page=0", "page=3", "size=150", "size=20"],
        )

    def test_the_variant_value_limit_caps_a_long_list(self) -> None:
        op = MODULE.Operation(
            path="/api/v1/transfers",
            method="GET",
            parameters=[MODULE.Parameter(name="tokenAddress", location="query")],
        )
        values = {"parameters": {"tokenAddress": ["0xa", "0xb", "0xc", "0xd"]}}
        cases = MODULE.generate_test_cases(op, values, {})
        self.assertEqual(len(cases) - 1, MODULE.VARIANT_VALUE_LIMIT)


class VacuousClassificationTest(unittest.TestCase):
    def _run(self, baseline, candidate, deprecated=False):
        op = MODULE.Operation(path="/api/v1/nfts", method="GET", deprecated=deprecated)
        tc = MODULE.TestCase(operation=op, label="GET /api/v1/nfts")
        return run_case(tc, baseline, candidate)

    def test_response_shapes(self) -> None:
        self.assertEqual(MODULE.response_shape(None, 200), "empty")
        self.assertEqual(MODULE.response_shape([], 200), "empty")
        self.assertEqual(MODULE.response_shape({"data": [], "pagination": {}}, 200), "empty")
        self.assertEqual(MODULE.response_shape({"id": 1}, 404), "error")
        self.assertEqual(MODULE.response_shape(15135786, 200), "scalar")
        self.assertEqual(MODULE.response_shape({"data": [{"a": 1}]}, 200), "data")

    def test_matching_empty_pages_are_vacuous_not_passes(self) -> None:
        result = self._run({"data": [], "pagination": {"hasNext": False}},
                           {"data": [], "pagination": {"hasNext": False}})
        self.assertEqual(MODULE.status_of(result), "vacuous")
        self.assertTrue(result.effective_pass)

    def test_matching_errors_are_reported_as_such(self) -> None:
        err = MODULE.HttpResponseError(404, "Not Found", {"id": "x", "status": 404}, "u")
        op = MODULE.Operation(path="/api/v1/accounts/overview/{address}", method="GET")
        tc = MODULE.TestCase(operation=op, label="x")
        result = run_case(tc, err, err)
        self.assertEqual(result.baseline_shape, "error")
        self.assertEqual(MODULE.status_of(result), "error-match")
        self.assertTrue(result.effective_pass)

    def test_an_empty_baseline_against_data_is_still_a_failure(self) -> None:
        result = self._run({"data": []}, {"data": [{"tokenId": "1"}]})
        self.assertEqual(MODULE.status_of(result), "fail")

    def test_operations_without_data_are_listed(self) -> None:
        empty = self._run({"data": []}, {"data": []})
        full = self._run({"data": [{"a": 1}]}, {"data": [{"a": 1}]})
        full.test_case.operation.path = "/api/v1/nfts/contracts"
        summary = MODULE.summarize([empty, full])
        self.assertEqual(summary["vacuous"], 1)
        self.assertEqual(summary["passed"], 1)
        self.assertEqual(summary["operations_without_data"], ["GET /api/v1/nfts"])


class ConvergenceTest(unittest.TestCase):
    """A baseline that moves mid-comparison is retried, not reported as a difference."""

    def _case(self):
        op = MODULE.Operation(path="/api/v1/transfers/latest", method="GET")
        return MODULE.TestCase(operation=op, label="GET /api/v1/transfers/latest")

    def test_a_candidate_that_catches_up_passes_on_a_later_attempt(self) -> None:
        head = {"data": [{"blockNumber": 100}]}
        behind = {"data": [{"blockNumber": 99}]}
        candidate_bodies = iter([behind, head, head])

        def stub(url, headers, timeout, context):
            return (head if url.startswith(BASELINE_URL) else next(candidate_bodies)), {}

        with patch.object(MODULE, "fetch_json_with_headers", side_effect=stub):
            result = MODULE.execute_test_case(
                self._case(), endpoints=ENDPOINTS, common_headers={}, timeout=5, insecure=False,
                cafile=None, ignored_paths=set(), unordered_lists=False, attempts=3,
            )
        self.assertFalse(result.has_mismatch)
        self.assertEqual(result.attempts, 2)

    def test_a_real_difference_still_fails_after_every_attempt(self) -> None:
        result = run_case(self._case(), {"data": [{"value": "1"}]}, {"data": [{"value": "2"}]}, attempts=3)
        self.assertTrue(result.has_mismatch)
        self.assertEqual(result.attempts, 3)

    def test_a_baseline_that_moves_between_its_two_reads_is_inconclusive(self) -> None:
        # Baseline reads 100 then 101 around the candidate's 101: the window, not a regression.
        first = {"data": [{"blockNumber": 100}]}
        second = {"data": [{"blockNumber": 101}]}
        baseline_bodies = iter([first, second, second, second, second, second])

        def stub(url, headers, timeout, context):
            return (next(baseline_bodies) if url.startswith(BASELINE_URL) else second), {}

        with patch.object(MODULE, "fetch_json_with_headers", side_effect=stub):
            result = MODULE.execute_test_case(
                self._case(), endpoints=ENDPOINTS, common_headers={}, timeout=5, insecure=False,
                cafile=None, ignored_paths=set(), unordered_lists=False, attempts=2,
            )
        self.assertFalse(result.has_mismatch)
        self.assertEqual(result.attempts, 2)


class CacheBustingTest(unittest.TestCase):
    def test_the_nonce_is_appended_to_the_query_string(self) -> None:
        op = MODULE.Operation(path="/api/v1/transfers", method="GET")
        tc = MODULE.TestCase(operation=op, query_params={"address": "0xabc"}, label="x")
        self.assertEqual(tc.path_with(None), "/api/v1/transfers?address=0xabc")
        self.assertEqual(tc.path_with("n1"), f"/api/v1/transfers?address=0xabc&{MODULE.CACHE_BUST_PARAM}=n1")

    def test_the_default_nonce_differs_per_request(self) -> None:
        self.assertNotEqual(MODULE.fresh_nonce(1), MODULE.fresh_nonce(1))

    def test_every_attempt_uses_a_fresh_nonce_and_both_sides_share_it(self) -> None:
        op = MODULE.Operation(path="/api/v1/transfers/latest", method="GET")
        tc = MODULE.TestCase(operation=op, label="x")
        urls = []

        def stub(url, headers, timeout, context):
            urls.append(url)
            return {"data": [{"v": len(urls)}]}, {}

        with patch.object(MODULE, "fetch_json_with_headers", side_effect=stub):
            MODULE.execute_test_case(
                tc, endpoints=ENDPOINTS, common_headers={}, timeout=5, insecure=False, cafile=None,
                ignored_paths=set(), unordered_lists=False, attempts=2, cache_bust=True,
                nonce_for=lambda attempt: f"n{attempt}",
            )
        self.assertTrue(all(f"{MODULE.CACHE_BUST_PARAM}=n1" in u for u in urls[:3]), urls)
        self.assertTrue(any(f"{MODULE.CACHE_BUST_PARAM}=n2" in u for u in urls[3:]), urls)

    def test_a_cached_response_is_reported(self) -> None:
        op = MODULE.Operation(path="/api/v1/b3tr/richlist", method="GET")
        tc = MODULE.TestCase(operation=op, label="x")
        body = {"data": [{"rank": 1}]}

        def stub(url, headers, timeout, context):
            served = "Hit from cloudfront" if url.startswith(CANDIDATE_URL) else "Miss from cloudfront"
            return body, {"x-cache": served, "cache-control": "public, max-age=600"}

        with patch.object(MODULE, "fetch_json_with_headers", side_effect=stub):
            result = MODULE.execute_test_case(
                tc, endpoints=ENDPOINTS, common_headers={}, timeout=5, insecure=False, cafile=None,
                ignored_paths=set(), unordered_lists=False,
            )
        self.assertEqual(MODULE.cache_hits(result), {"candidate": "Hit from cloudfront"})
        self.assertEqual(result.cache_control["baseline"], "public, max-age=600")


class FailureSemanticsTest(unittest.TestCase):
    """A run can only be green when the endpoints actually answered."""

    def _case(self, path="/api/v1/nfts", deprecated=False, expect_fail=None):
        op = MODULE.Operation(path=path, method="GET", deprecated=deprecated)
        return MODULE.TestCase(operation=op, label=f"GET {path}", expected_failure=expect_fail)

    def _error(self, status):
        return MODULE.HttpResponseError(status, "err", {"status": status}, "u")

    def test_a_deprecated_endpoint_no_longer_excuses_a_difference(self) -> None:
        result = run_case(self._case(deprecated=True), {"a": 1}, {"a": 2}, attempts=1)
        self.assertEqual(MODULE.status_of(result), "fail")
        self.assertFalse(result.effective_pass)

    def test_a_declared_expectation_downgrades_a_difference(self) -> None:
        case = self._case(expect_fail="v1 drops the field v2 added")
        result = run_case(case, {"a": 1}, {"a": 2}, attempts=1)
        self.assertEqual(MODULE.status_of(result), "expected-fail")
        self.assertTrue(result.effective_pass)

    def test_expect_fail_is_read_from_path_overrides_and_not_sent_as_a_parameter(self) -> None:
        op = MODULE.Operation(
            path="/api/v1/nfts",
            method="GET",
            parameters=[MODULE.Parameter(name="address", location="query", required=True)],
        )
        values = {
            "parameters": {"address": ["0xabc"]},
            "path_overrides": {"/api/v1/nfts": {"expect_fail": "known"}},
        }
        cases = MODULE.generate_test_cases(op, values, {})
        self.assertEqual(cases[0].expected_failure, "known")
        self.assertEqual(cases[0].query_params, {"address": "0xabc"})

    def test_matching_rate_limits_fail_the_run(self) -> None:
        result = run_case(self._case(), self._error(429), self._error(429), attempts=1)
        self.assertEqual(MODULE.status_of(result), "unavailable")
        self.assertFalse(result.effective_pass)

    def test_matching_server_errors_fail_the_run(self) -> None:
        result = run_case(self._case(), self._error(503), self._error(503), attempts=1)
        self.assertFalse(result.effective_pass)

    def test_a_matching_404_is_still_an_acceptable_answer(self) -> None:
        result = run_case(self._case(), self._error(404), self._error(404), attempts=1)
        self.assertEqual(MODULE.status_of(result), "error-match")
        self.assertTrue(result.effective_pass)

    def test_a_rate_limit_is_retried_before_it_counts(self) -> None:
        bodies = iter([self._error(429), self._error(429), self._error(429),
                       {"data": [{"a": 1}]}, {"data": [{"a": 1}]}, {"data": [{"a": 1}]}])

        def stub(url, headers, timeout, context):
            body = next(bodies)
            if isinstance(body, Exception):
                raise body
            return body, {}

        with patch.object(MODULE, "fetch_json_with_headers", side_effect=stub):
            result = MODULE.execute_test_case(
                self._case(), endpoints=ENDPOINTS, common_headers={}, timeout=5, insecure=False,
                cafile=None, ignored_paths=set(), unordered_lists=False, attempts=2,
            )
        self.assertEqual(MODULE.status_of(result), "pass")
        self.assertEqual(result.attempts, 2)

    def test_a_declared_expectation_also_covers_its_operation(self) -> None:
        case = self._case(expect_fail="only the candidate serves it")
        summary = MODULE.summarize([run_case(case, {"a": 1}, {"a": 2}, attempts=1)])
        self.assertEqual(summary["operations_without_data"], [])

    def test_an_operation_is_only_covered_by_a_case_that_compared_data(self) -> None:
        summary = MODULE.summarize([
            run_case(self._case(), self._error(404), self._error(404), attempts=1),
            run_case(self._case("/api/v1/blocks"), {"data": [{"a": 1}]}, {"data": [{"a": 1}]}),
        ])
        self.assertEqual(summary["error_match"], 1)
        self.assertEqual(summary["operations_without_data"], ["GET /api/v1/nfts"])


class SpecUnionTest(unittest.TestCase):
    def test_operations_on_one_side_only_are_reported_and_still_exercised(self) -> None:
        shared = MODULE.Operation(path="/api/v1/blocks", method="GET")
        baseline_only = MODULE.Operation(path="/api/v1/history/{account}", method="GET")
        candidate_only = MODULE.Operation(path="/api/v1/vevote/proposals/comments", method="GET")
        merged, only = MODULE.merge_operations([shared, baseline_only], [shared, candidate_only])
        self.assertEqual(
            [f"{op.method} {op.path}" for op in merged],
            ["GET /api/v1/blocks", "GET /api/v1/history/{account}",
             "GET /api/v1/vevote/proposals/comments"],
        )
        self.assertEqual(
            only, ["GET /api/v1/history/{account}", "GET /api/v1/vevote/proposals/comments"]
        )

    def test_identical_specs_report_nothing(self) -> None:
        ops = [MODULE.Operation(path="/api/v1/blocks", method="GET")]
        merged, only = MODULE.merge_operations(ops, list(ops))
        self.assertEqual(len(merged), 1)
        self.assertEqual(only, [])


class CursorWalkTest(unittest.TestCase):
    def _result(self, path, params, body, name="baseline"):
        op = MODULE.Operation(
            path=path,
            method="GET",
            parameters=[MODULE.Parameter(name=n, location="query") for n in params],
        )
        tc = MODULE.TestCase(operation=op, label=f"GET {path}")
        return MODULE.ComparisonResult(
            test_case=tc, responses={name: body}, status_codes={name: 200}, diffs={}, errors={}
        )

    def test_the_next_page_resumes_both_sides_from_the_baseline_cursor(self) -> None:
        body = {"data": [{"a": 1}], "pagination": {"hasNext": True, "cursor": "25910018|1"}}
        result = self._result("/api/v1/transfers/latest", ["size", "cursor"], body)
        follow_up = MODULE.cursor_follow_up(result, "baseline", 2)
        self.assertEqual(follow_up.query_params, {"cursor": "25910018|1"})
        self.assertEqual(follow_up.label, "GET /api/v1/transfers/latest [page 2 from the baseline cursor]")

    def test_blocks_resumes_through_its_from_parameter(self) -> None:
        body = {"data": [{"number": 10}], "pagination": {"hasNext": True, "cursor": 25910016}}
        result = self._result("/api/v1/blocks", ["from", "size"], body)
        self.assertEqual(MODULE.cursor_follow_up(result, "baseline", 2).query_params, {"from": "25910016"})

    def test_the_label_does_not_compound_across_pages(self) -> None:
        body = {"data": [{"a": 1}], "pagination": {"hasNext": True, "cursor": "c1"}}
        result = self._result("/api/v1/transfers/latest", ["cursor"], body)
        page_two = MODULE.cursor_follow_up(result, "baseline", 2)
        result.test_case = page_two
        page_three = MODULE.cursor_follow_up(result, "baseline", 3)
        self.assertEqual(
            page_three.label, "GET /api/v1/transfers/latest [page 3 from the baseline cursor]"
        )

    def test_the_last_page_and_a_non_paginated_response_end_the_walk(self) -> None:
        last = {"data": [{"a": 1}], "pagination": {"hasNext": False, "cursor": None}}
        self.assertIsNone(
            MODULE.cursor_follow_up(self._result("/api/v1/transfers/latest", ["cursor"], last), "baseline", 2)
        )
        offset = {"data": [{"a": 1}], "pagination": {"hasNext": True}}
        self.assertIsNone(
            MODULE.cursor_follow_up(self._result("/api/v1/nfts", ["page", "size"], offset), "baseline", 2)
        )
