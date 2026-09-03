import importlib.util
import sys
import unittest
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None:
        raise ImportError(f"Cannot load module {name!r} from path {path!r}")
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


MODULE = load_module("regression_seed", SCRIPTS_DIR / "regression_seed.py")


class RegressionSeedTest(unittest.TestCase):
    def test_collect_validator_candidates_dedupes_and_marks_preferred(self) -> None:
        responses = {
            "https://baseline.example.com/api/v1/validators?status=ACTIVE&page=0&size=2&sortBy=validatorTvl": {
                "data": [
                    {"id": "0x01", "nftYieldsNextCycle": {"Strength": 1.23}},
                    {"id": "0x02", "nftYieldsNextCycle": {}},
                ]
            },
            "https://baseline.example.com/api/v1/validators?status=ACTIVE&page=0&size=2&sortBy=totalTvl": {
                "data": [
                    {"id": "0x02", "nftYieldsNextCycle": {}},
                    {"id": "0x03", "nftYieldsNextCycle": {"Thunder": 4.56}},
                ]
            },
        }

        def fake_fetch(url: str, timeout: int):
            self.assertEqual(timeout, 15)
            return responses[url]

        candidate_ids, preferred_ids = MODULE.collect_validator_candidates(
            "https://baseline.example.com",
            timeout=15,
            page_count=1,
            page_size=2,
            sort_fields=["validatorTvl", "totalTvl"],
            statuses=["ACTIVE"],
            fetcher=fake_fetch,
        )

        self.assertEqual(candidate_ids, ["0x01", "0x02", "0x03"])
        self.assertEqual(preferred_ids, ["0x01", "0x03"])

    def test_choose_validator_sample_is_deterministic_and_prefers_preferred_pool(self) -> None:
        candidate_ids = ["0x01", "0x02", "0x03", "0x04", "0x05"]
        preferred_ids = ["0x02", "0x04"]

        sample_one = MODULE.choose_validator_sample(
            candidate_ids,
            preferred_ids,
            sample_size=4,
            seed=1337,
        )
        sample_two = MODULE.choose_validator_sample(
            candidate_ids,
            preferred_ids,
            sample_size=4,
            seed=1337,
        )

        self.assertEqual(sample_one, sample_two)
        self.assertEqual(set(sample_one[:2]), {"0x02", "0x04"})
        self.assertEqual(len(sample_one), 4)

    def test_apply_validator_sample_targets_validator_endpoints_without_widening_generic_params(self) -> None:
        test_values = {
            "parameters": {
                "validator": ["0xold"],
                "validatorId": ["0xold-detail"],
            },
            "path_overrides": {
                "/api/v1/validators": {"status": ["ACTIVE", "EXITING"]},
            },
        }

        updated = MODULE.apply_validator_sample(
            test_values,
            ["0x01", "0x02", "0x03", "0x04", "0x05", "0x06"],
            validator_pages=[0, 1, 2],
            validator_sort_fields=["validatorTvl", "totalTvl"],
        )

        self.assertEqual(updated["parameters"]["validator"], ["0x01"])
        self.assertEqual(updated["parameters"]["validatorId"], ["0x01"])
        self.assertEqual(
            updated["path_overrides"]["/api/v2/validators/{validatorId}"]["validatorId"],
            ["0x01", "0x02", "0x03", "0x04", "0x05", "0x06"],
        )
        self.assertEqual(
            updated["path_overrides"]["/api/v1/validators"]["page"],
            [0, 1, 2],
        )
        self.assertEqual(
            updated["path_overrides"]["/api/v1/validators"]["sortBy"],
            ["validatorTvl", "totalTvl"],
        )
        self.assertEqual(
            updated["path_overrides"]["/api/v1/validators"]["status"],
            ["ACTIVE", "EXITING"],
        )
        self.assertEqual(
            updated["path_overrides"]["/api/v1/validators/blocks/historic/{validator}"]["validator"],
            ["0x01", "0x02", "0x03", "0x04", "0x05"],
        )


if __name__ == "__main__":
    unittest.main()
