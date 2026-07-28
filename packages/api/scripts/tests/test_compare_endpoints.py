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


MODULE = load_module("compare_endpoints", SCRIPTS_DIR / "compare_endpoints.py")


class WithinNumericToleranceTest(unittest.TestCase):
    def test_zero_tolerance_disables(self):
        self.assertFalse(MODULE.within_numeric_tolerance(1, 2, 0.0, 0.0))

    def test_abs_tolerance_includes_boundary(self):
        self.assertTrue(MODULE.within_numeric_tolerance(100, 101, 1, 0))
        self.assertTrue(MODULE.within_numeric_tolerance(100, 99, 1, 0))
        self.assertFalse(MODULE.within_numeric_tolerance(100, 102, 1, 0))

    def test_relative_tolerance(self):
        self.assertTrue(MODULE.within_numeric_tolerance(100, 105, 0, 0.05))
        self.assertFalse(MODULE.within_numeric_tolerance(100, 106, 0, 0.05))

    def test_tiny_float_drift(self):
        # The real-world topPercentage drift observed between two indexers.
        self.assertTrue(
            MODULE.within_numeric_tolerance(
                0.059771728553454445, 0.059771751707446134, 0, 1e-6
            )
        )

    def test_bool_excluded(self):
        # bool is a subclass of int in Python; tolerance must not silently equate
        # True/False with their integer counterparts.
        self.assertFalse(MODULE.within_numeric_tolerance(True, False, 1, 0))
        self.assertFalse(MODULE.within_numeric_tolerance(True, 1, 1, 0))
        self.assertFalse(MODULE.within_numeric_tolerance(False, 0, 1, 0))

    def test_non_numeric_returns_false(self):
        self.assertFalse(MODULE.within_numeric_tolerance("1", "2", 1, 0))
        self.assertFalse(MODULE.within_numeric_tolerance(None, 0, 1, 0))

    def test_int_float_mixed(self):
        # int vs float drift respects the bound just like int vs int.
        self.assertTrue(MODULE.within_numeric_tolerance(100, 100.5, 1, 0))
        self.assertFalse(MODULE.within_numeric_tolerance(100, 102.5, 1, 0))
        # Decimal-based math: 1 and 1.0 compare equal, so any non-zero tolerance accepts them.
        self.assertTrue(MODULE.within_numeric_tolerance(1, 1.0, 1, 0))


class LargeIntegerPrecisionTest(unittest.TestCase):
    """
    Floats lose precision at uint256 scale. A naive float-cast tolerance would
    treat 1e24+1 and 1e24+1000 as equal because both round to the same float.
    Decimal-based comparison must reject diffs that exceed the configured bound,
    even when they collapse under float arithmetic.
    """

    def test_neighbouring_uint256_ints_tolerated(self):
        a = 1_000_000_000_000_000_000_000_000_001
        b = 1_000_000_000_000_000_000_000_000_002
        self.assertTrue(MODULE.within_numeric_tolerance(a, b, 1, 0))

    def test_thousand_apart_at_uint256_scale_is_hard(self):
        a = 1_000_000_000_000_000_000_000_000_001
        b = 1_000_000_000_000_000_000_000_001_001  # +1000
        # Sanity: the float round-trip would have masked this diff entirely.
        self.assertEqual(float(a), float(b))
        # But the tolerance check must still reject it.
        self.assertFalse(MODULE.within_numeric_tolerance(a, b, 1, 0))

    def test_above_2_to_53_drift_not_tolerated(self):
        # 2**53 is the boundary of exact integer representation in float64.
        a = 2**53
        b = 2**53 + 1000
        # float(a) == float(a + 1) at this scale, so a naive float cast would
        # under-report the diff; Decimal-based math must catch it.
        self.assertEqual(float(a), float(a + 1))
        self.assertFalse(MODULE.within_numeric_tolerance(a, b, 1, 0))

    def test_wei_balance_drift_above_tolerance_is_hard(self):
        wei = 10**18
        self.assertTrue(MODULE.within_numeric_tolerance(5 * wei + 1, 5 * wei, 1, 0))
        self.assertFalse(
            MODULE.within_numeric_tolerance(5 * wei + 100, 5 * wei, 1, 0)
        )


class CompareJsonToleranceTest(unittest.TestCase):
    def _compare(self, a, b, **kwargs):
        diffs, tolerated = [], []
        MODULE.compare_json(a, b, diffs=diffs, tolerated_diffs=tolerated, **kwargs)
        return diffs, tolerated

    def test_real_world_drift_classified_as_tolerated(self):
        baseline = {
            "endBlock": 24769503,
            "startBlock": 24760863,
            "topPercentage": 0.059771728553454445,
            "totalHolders": 2581488,
            "rank": 2581489,
            "name": "ok",
        }
        candidate = {
            "endBlock": 24769504,
            "startBlock": 24760864,
            "topPercentage": 0.059771751707446134,
            "totalHolders": 2581487,
            "rank": 2581488,
            "name": "ok",
        }
        diffs, tolerated = self._compare(
            baseline, candidate, num_abs_tolerance=1, num_rel_tolerance=0
        )
        self.assertEqual(diffs, [])
        # All five drifted numeric fields surfaced as tolerated, with paths preserved.
        tolerated_paths = {p for p, _ in tolerated}
        self.assertEqual(
            tolerated_paths,
            {
                "root.endBlock",
                "root.startBlock",
                "root.topPercentage",
                "root.totalHolders",
                "root.rank",
            },
        )

    def test_int_float_same_value_not_a_diff(self):
        diffs, tolerated = self._compare({"x": 1}, {"x": 1.0})
        self.assertEqual(diffs, [])
        self.assertEqual(tolerated, [])

    def test_int_float_drift_within_tolerance(self):
        diffs, tolerated = self._compare(
            {"x": 100}, {"x": 100.5}, num_abs_tolerance=1
        )
        self.assertEqual(diffs, [])
        self.assertEqual(len(tolerated), 1)

    def test_int_float_drift_outside_tolerance_is_value_mismatch(self):
        diffs, tolerated = self._compare(
            {"x": 100}, {"x": 200.5}, num_abs_tolerance=1
        )
        self.assertEqual(tolerated, [])
        self.assertEqual(len(diffs), 1)
        self.assertIn("value mismatch", diffs[0][1])
        self.assertNotIn("type mismatch", diffs[0][1])

    def test_int_vs_string_remains_type_mismatch(self):
        diffs, tolerated = self._compare(
            {"x": 1}, {"x": "1"}, num_abs_tolerance=10
        )
        self.assertEqual(tolerated, [])
        self.assertEqual(len(diffs), 1)
        self.assertIn("type mismatch", diffs[0][1])

    def test_bool_vs_int_remains_type_mismatch(self):
        diffs, tolerated = self._compare(
            {"x": True}, {"x": 1}, num_abs_tolerance=1
        )
        self.assertEqual(tolerated, [])
        self.assertEqual(len(diffs), 1)
        self.assertIn("type mismatch", diffs[0][1])

    def test_string_drift_remains_hard(self):
        diffs, tolerated = self._compare(
            {"x": "a"}, {"x": "b"}, num_abs_tolerance=10
        )
        self.assertEqual(tolerated, [])
        self.assertEqual(len(diffs), 1)

    def test_list_length_mismatch_remains_hard(self):
        diffs, tolerated = self._compare(
            [1, 2, 3], [1, 2], num_abs_tolerance=10
        )
        self.assertEqual(tolerated, [])
        self.assertTrue(any("list length" in m for _, m in diffs))

    def test_no_tolerated_sink_means_numeric_diff_is_hard(self):
        # When callers omit tolerated_diffs (legacy behavior), tolerance must
        # not silently swallow numeric mismatches.
        diffs = []
        MODULE.compare_json(
            {"x": 1}, {"x": 2}, diffs=diffs, num_abs_tolerance=10
        )
        self.assertEqual(len(diffs), 1)


class WildcardIgnorePathTest(unittest.TestCase):
    def _compare(self, a, b, ignored):
        diffs = []
        MODULE.compare_json(a, b, diffs=diffs, ignored_paths=set(ignored))
        return diffs

    def test_exact_prefix_still_works_without_wildcard(self):
        # Regression: the legacy no-wildcard behaviour must be preserved.
        diffs = self._compare(
            {"data": {"x": 1, "y": 2}},
            {"data": {"x": 9, "y": 8}},
            ignored=["root.data.x"],
        )
        paths = {p for p, _ in diffs}
        self.assertNotIn("root.data.x", paths)
        self.assertIn("root.data.y", paths)

    def test_wildcard_matches_any_list_index(self):
        # root.data[*].balance must silence balance drift on every element.
        baseline = {"data": [{"balance": "1"}, {"balance": "2"}, {"balance": "3"}]}
        candidate = {"data": [{"balance": "10"}, {"balance": "20"}, {"balance": "30"}]}
        diffs = self._compare(baseline, candidate, ignored=["root.data[*].balance"])
        self.assertEqual(diffs, [])

    def test_wildcard_still_reports_other_fields(self):
        baseline = {"data": [{"balance": "1", "rank": 1}]}
        candidate = {"data": [{"balance": "10", "rank": 2}]}
        diffs = self._compare(baseline, candidate, ignored=["root.data[*].balance"])
        paths = {p for p, _ in diffs}
        self.assertNotIn("root.data[0].balance", paths)
        self.assertIn("root.data[0].rank", paths)

    def test_wildcard_matches_root_level_list(self):
        # root[*].timestamp — the historic-bucket endpoint pattern.
        baseline = [{"timestamp": 100, "value": 5}, {"timestamp": 200, "value": 6}]
        candidate = [{"timestamp": 110, "value": 5}, {"timestamp": 210, "value": 6}]
        diffs = self._compare(baseline, candidate, ignored=["root[*].timestamp"])
        self.assertEqual(diffs, [])

    def test_wildcard_ignores_present_only_in_one_side(self):
        # If a wildcarded leaf appears on only one side, it must still be silenced.
        baseline = {"data": [{"balance": "1", "extra": "x"}]}
        candidate = {"data": [{"balance": "10"}]}
        diffs = self._compare(baseline, candidate, ignored=["root.data[*].balance", "root.data[*].extra"])
        self.assertEqual(diffs, [])

    def test_matches_ignored_helper(self):
        # Direct helper coverage.
        self.assertTrue(MODULE.path_matches_ignored("root.data[0].balance", "root.data[*].balance"))
        self.assertTrue(MODULE.path_matches_ignored("root.data[42].balance", "root.data[*].balance"))
        self.assertFalse(MODULE.path_matches_ignored("root.data[0].rank", "root.data[*].balance"))
        # descendants of a wildcarded path also match
        self.assertTrue(MODULE.path_matches_ignored("root.data[0].nested.field", "root.data[*].nested"))
        # multiple wildcards
        self.assertTrue(
            MODULE.path_matches_ignored(
                "root.data[0].events[3].topic", "root.data[*].events[*].topic"
            )
        )


if __name__ == "__main__":
    unittest.main()
