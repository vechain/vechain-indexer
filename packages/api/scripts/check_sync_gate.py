#!/usr/bin/env python3
"""Compare two colours' indexer checkpoints before trusting a regression run.

Exits non-zero when any indexer on the candidate trails the baseline by more than the
allowed gap, or when one colour runs an indexer the other does not.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from typing import Any, Dict, List

STATUS_PATH = "/api/v1/status"


def fetch_status(base_url: str, timeout: int) -> Dict[str, Dict[str, Any]]:
    url = base_url.rstrip("/") + STATUS_PATH
    request = urllib.request.Request(url, headers={"User-Agent": "sync-gate/1.0"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.loads(response.read().decode())
    return {entry["schema"]: entry for entry in payload}


def compare(
    baseline: Dict[str, Dict[str, Any]], candidate: Dict[str, Dict[str, Any]], max_gap: int
) -> List[str]:
    problems = []
    for schema in sorted(set(baseline) - set(candidate)):
        problems.append(f"{schema}: the candidate does not run this indexer")
    for schema in sorted(set(candidate) - set(baseline)):
        problems.append(f"{schema}: the baseline does not run this indexer")
    for schema in sorted(set(baseline) & set(candidate)):
        ahead, behind = baseline[schema].get("blockNumber"), candidate[schema].get("blockNumber")
        if behind is None:
            problems.append(f"{schema}: the candidate has indexed no block")
        elif ahead is not None and ahead - behind > max_gap:
            problems.append(f"{schema}: {ahead - behind} blocks behind the baseline")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-url", required=True)
    parser.add_argument("--candidate-url", required=True)
    parser.add_argument("--max-gap", type=int, default=100, help="Blocks the candidate may trail by")
    parser.add_argument("--timeout", type=int, default=30)
    args = parser.parse_args()

    try:
        baseline = fetch_status(args.baseline_url, args.timeout)
        candidate = fetch_status(args.candidate_url, args.timeout)
    except Exception as exc:
        print(f"Could not read {STATUS_PATH} from both colours: {exc}", file=sys.stderr)
        return 2

    problems = compare(baseline, candidate, args.max_gap)
    for problem in problems:
        print(f"  {problem}", file=sys.stderr)
    print(
        f"{len(baseline)} indexers compared, {len(problems)} outside a {args.max_gap}-block gap",
        file=sys.stderr,
    )
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
