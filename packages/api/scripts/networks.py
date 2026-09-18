#!/usr/bin/env python3
"""The networks the regression suite compares, and how each one is reached.

Both colours sit behind CloudFront; the `*.prod.veworld.vechain.org` ALB origins admit
distribution traffic only, so these are the distribution aliases (cloudfront_only_guard.sh).

    python3 networks.py testnet candidate
"""

from __future__ import annotations

import argparse
import sys
from typing import Dict

NETWORKS: Dict[str, Dict[str, str]] = {
    "mainnet": {
        "baseline": "https://indexer.mainnet.vechain.org",
        "candidate": "https://mainnet.dead.veworld.vechain.org",
        "thor": "https://mainnet.vechain.org",
        "test_values": "test_values.mainnet.json",
    },
    "testnet": {
        "baseline": "https://indexer.testnet.vechain.org",
        "candidate": "https://testnet.dead.veworld.vechain.org",
        "thor": "https://testnet.vechain.org",
        "test_values": "test_values.testnet.json",
    },
}

DEFAULT_NETWORK = "mainnet"


def profile(network: str) -> Dict[str, str]:
    if network not in NETWORKS:
        raise KeyError(f"unknown network {network!r}; expected one of {', '.join(NETWORKS)}")
    return NETWORKS[network]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("network", choices=sorted(NETWORKS))
    parser.add_argument("field", choices=sorted(next(iter(NETWORKS.values()))))
    args = parser.parse_args()
    print(profile(args.network)[args.field])
    return 0


if __name__ == "__main__":
    sys.exit(main())
