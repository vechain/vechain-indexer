#!/usr/bin/env python3
"""Check the candidate's indexers have caught up with the chain before comparing responses.

The reference is a Thor node's best block, not the other colour. The other colour is itself
an indexer that can lag, and during the Mongo to Postgres migration it cannot serve
/api/v1/status at all, because that endpoint reads a Postgres table.

Exits non-zero when any indexer trails the chain head by more than the allowed gap.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from typing import Any, Dict, List

STATUS_PATH = "/api/v1/status"
BEST_BLOCK_PATH = "/blocks/best"


def fetch(url: str, timeout: int) -> Any:
    request = urllib.request.Request(url, headers={"User-Agent": "sync-gate/1.0"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode())


def fetch_checkpoints(base_url: str, timeout: int) -> List[Dict[str, Any]]:
    payload = fetch(base_url.rstrip("/") + STATUS_PATH, timeout)
    return payload if isinstance(payload, list) else []


def fetch_best_block(thor_url: str, timeout: int) -> int:
    return int(fetch(thor_url.rstrip("/") + BEST_BLOCK_PATH, timeout)["number"])


def compare(checkpoints: List[Dict[str, Any]], best_block: int, max_gap: int) -> List[str]:
    if not checkpoints:
        return ["the candidate reported no indexers at all"]
    problems = []
    for entry in sorted(checkpoints, key=lambda e: e.get("schema", "")):
        schema = entry.get("schema", "?")
        indexed = entry.get("blockNumber")
        if indexed is None:
            problems.append(f"{schema}: has indexed no block")
        elif best_block - indexed > max_gap:
            problems.append(f"{schema}: {best_block - indexed} blocks behind the chain head")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-url", required=True)
    parser.add_argument("--thor-url", required=True, help="A Thor node on the same network")
    parser.add_argument("--max-gap", type=int, default=100, help="Blocks an indexer may trail by")
    parser.add_argument("--timeout", type=int, default=30)
    args = parser.parse_args()

    try:
        checkpoints = fetch_checkpoints(args.candidate_url, args.timeout)
        best_block = fetch_best_block(args.thor_url, args.timeout)
    except Exception as exc:
        print(f"Could not read the candidate's status or the chain head: {exc}", file=sys.stderr)
        return 2

    problems = compare(checkpoints, best_block, args.max_gap)
    for problem in problems:
        print(f"  {problem}", file=sys.stderr)
    print(
        f"{len(checkpoints)} indexers against chain head {best_block}, "
        f"{len(problems)} outside a {args.max_gap}-block gap",
        file=sys.stderr,
    )
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
