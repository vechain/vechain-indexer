import importlib.util
import json
import re
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


NETWORKS = load_module("networks", SCRIPTS_DIR / "networks.py")
SEED = load_module("regression_seed", SCRIPTS_DIR / "regression_seed.py")


class NetworkProfileTest(unittest.TestCase):
    def test_every_network_names_both_colours_a_thor_node_and_its_values(self) -> None:
        for name, network in NETWORKS.NETWORKS.items():
            with self.subTest(network=name):
                self.assertEqual(
                    sorted(network), ["baseline", "candidate", "test_values", "thor"]
                )
                self.assertTrue((SCRIPTS_DIR / network["test_values"]).is_file())

    def test_no_colour_points_at_an_alb_origin(self) -> None:
        # cloudfront_only_guard.sh rejects these; the profiles must not carry one.
        for name, network in NETWORKS.NETWORKS.items():
            for field in ("baseline", "candidate"):
                with self.subTest(network=name, field=field):
                    self.assertNotIn("prod.veworld.vechain.org", network[field])

    def test_unknown_network_is_rejected_by_name(self) -> None:
        with self.assertRaises(KeyError) as caught:
            NETWORKS.profile("devnet")
        self.assertIn("devnet", str(caught.exception))


class NetworkValuesTest(unittest.TestCase):
    """The shared file holds structure; each network file holds only its own chain's data."""

    def setUp(self) -> None:
        self.shared = json.loads((SCRIPTS_DIR / "test_values.json").read_text())
        self.overlays = {
            name: json.loads((SCRIPTS_DIR / network["test_values"]).read_text())
            for name, network in NETWORKS.NETWORKS.items()
        }

    def test_shared_file_carries_no_chain_data(self) -> None:
        for name, values in self.shared["parameters"].items():
            with self.subTest(parameter=name):
                self.assertFalse(
                    [v for v in values if isinstance(v, str) and v.startswith("0x")],
                    f"{name} names chain data and belongs in a network file",
                )

    def test_each_network_brings_its_own_chain_data(self) -> None:
        # A wallet can hold funds on both chains, and the built-in contracts are the same
        # address everywhere; what must not happen is one network's file standing in for
        # the other's, which would test mainnet ids against testnet rows.
        mainnet = self._addresses(self.overlays["mainnet"])
        testnet = self._addresses(self.overlays["testnet"])
        self.assertTrue(mainnet - testnet, "the mainnet file carries nothing of its own")
        self.assertTrue(testnet - mainnet, "the testnet file carries nothing of its own")
        self.assertLess(len(mainnet & testnet), min(len(mainnet), len(testnet)) / 2)

    def test_merge_lays_the_network_over_the_shared_values(self) -> None:
        merged = SEED.merge_values(
            {
                "parameters": {"size": [20], "address": ["0xshared"]},
                "path_overrides": {"/a": {"ignore_paths": ["root.x"], "address": ["0xshared"]}},
            },
            {
                "parameters": {"address": ["0xnetwork"]},
                "path_overrides": {"/a": {"address": ["0xnetwork"]}, "/b": {"tokenId": ["1"]}},
            },
        )
        self.assertEqual(merged["parameters"], {"size": [20], "address": ["0xnetwork"]})
        self.assertEqual(
            merged["path_overrides"]["/a"], {"ignore_paths": ["root.x"], "address": ["0xnetwork"]}
        )
        self.assertEqual(merged["path_overrides"]["/b"], {"tokenId": ["1"]})

    def test_merge_leaves_the_inputs_alone(self) -> None:
        base = {"parameters": {"address": ["0xshared"]}, "path_overrides": {}}
        SEED.merge_values(base, {"parameters": {"address": ["0xnetwork"]}})
        self.assertEqual(base["parameters"]["address"], ["0xshared"])

    @staticmethod
    def _addresses(overlay):
        found = set()
        for section in ("parameters", "path_overrides"):
            for entry in overlay.get(section, {}).values():
                groups = entry.values() if isinstance(entry, dict) else [entry]
                for group in groups:
                    for value in group if isinstance(group, list) else [group]:
                        if isinstance(value, str) and re.fullmatch(r"0x[0-9a-fA-F]{40}", value):
                            found.add(value.lower())
        return found


if __name__ == "__main__":
    unittest.main()
