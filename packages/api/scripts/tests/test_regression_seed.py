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


class FamilySeedersTest(unittest.TestCase):
    BASE = "https://baseline.example.com"

    def _api(self, responses):
        def fetcher(url, timeout):
            if url not in responses:
                raise KeyError(url)
            return responses[url]

        return MODULE.Baseline(self.BASE, fetcher, 5)

    def test_pick_skips_candidates_the_baseline_cannot_answer(self) -> None:
        api = self._api({f"{self.BASE}/api/v1/nfts?address=0xb": {"data": [{"tokenId": "7"}]}})
        chosen = api.pick(["0xa", "0xb"], lambda a: api.get("/api/v1/nfts", address=a)["data"])
        self.assertEqual(chosen, "0xb")
        with self.assertRaises(LookupError):
            api.pick(["0xa"], lambda a: api.get("/api/v1/nfts", address=a)["data"])

    def test_transfers_seed_hot_addresses_and_vet_rows(self) -> None:
        rows = [
            {"from": "0xa", "to": "0xb", "blockNumber": 10, "blockTimestamp": 5000},
            {"from": "0xa", "to": "0xc", "blockNumber": 11, "blockTimestamp": 5010,
             "tokenAddress": "0xtoken"},
            {"from": "0xd", "to": "0xa", "blockNumber": 12, "blockTimestamp": 5020,
             "tokenAddress": "0xtoken"},
        ]
        api = self._api({f"{self.BASE}/api/v1/transfers/latest?size=50": {"data": rows}})
        values = {}
        MODULE.seed_transfers(values, api)
        self.assertEqual(values["parameters"]["address"], ["0xa", "0xb", "0xc"])
        self.assertEqual(values["parameters"]["tokenAddress"], ["0xtoken"])
        # eventType stays a variant so it never contradicts the seeded token address.
        self.assertNotIn("eventType", values["path_overrides"]["/api/v1/transfers"])
        self.assertEqual(
            values["path_overrides"]["/api/v1/transfers/forBlock"],
            {"addresses": ["0xb"], "blockNumber": [10]},
        )
        self.assertEqual(
            values["path_overrides"]["/api/v1/accounts/balance/vet/{address}"],
            {"address": ["0xa"], "startTimestamp": [1400], "endTimestamp": [5060]},
        )

    def test_a_failing_seeder_restores_the_static_values(self) -> None:
        values = {"parameters": {"address": ["0xstatic"]}}
        api = self._api({})
        outcomes = MODULE.seed_families(values, api)
        self.assertEqual(values, {"parameters": {"address": ["0xstatic"]}})
        self.assertTrue(all(o.startswith("skipped:") for o in outcomes.values()))
        self.assertEqual(set(outcomes), {s.__name__ for s in MODULE.SEEDERS})

    def test_proposals_seed_stops_at_the_first_proposal_with_comments(self) -> None:
        responses = {
            f"{self.BASE}/api/v2/b3tr/proposals/results?size=20": {
                "data": [
                    {"proposalId": "1", "state": "Executed"},
                    {"proposalId": "2", "state": "Active"},
                ]
            },
            f"{self.BASE}/api/v1/b3tr/proposals/1/comments?size=1": {"data": []},
            f"{self.BASE}/api/v1/b3tr/proposals/2/comments?size=1": {"data": [{"voter": "0xv"}]},
        }
        values = {}
        MODULE.seed_proposals(values, self._api(responses))
        self.assertEqual(values["parameters"]["proposalId"], ["1", "2"])
        self.assertEqual(
            values["path_overrides"]["/api/v1/b3tr/proposals/{proposalId}/comments"],
            {"proposalId": ["2"]},
        )
        # The wallet is the one that commented; proposalId stays a variant rather than
        # narrowing the base case to a pair that may hold no comment of its own.
        self.assertEqual(
            values["path_overrides"]["/api/v1/b3tr/users/{wallet}/proposals/comments"],
            {"wallet": ["0xv"], "proposalId": None},
        )

    def test_head_windows_are_closed_and_end_a_day_before_the_head(self) -> None:
        head = {"data": [{"number": 1_000_000, "timestamp": 1_700_000_000}]}
        values = {}
        MODULE.seed_head_windows(values, self._api({f"{self.BASE}/api/v1/blocks?size=1": head}))
        self.assertEqual(values["parameters"]["startTimestamp"], [1_700_000_000 - 8 * 86400])
        self.assertEqual(values["parameters"]["endTimestamp"], [1_700_000_000 - 86400])
        self.assertEqual(values["path_overrides"]["/api/v1/blocks"]["from"], [999_000, 22343000])
        self.assertEqual(
            values["path_overrides"]["/api/v1/b3tr/actions/users/{wallet}/daily-summaries"],
            {"startDate": ["2023-10-15"], "endDate": ["2023-11-14"]},
        )
