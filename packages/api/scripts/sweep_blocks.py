#!/usr/bin/env python3
"""Compare two colours block by block, so the migrated rows are checked, not the query layer.

For each window: page through /api/v1/blocks on both sides, then for every transaction in
those blocks compare /api/v1/transactions/{txId} in both views. This is where hex spelling,
JSONB key order and a dropped clause surface, which a filtered list endpoint can hide.
"""

from __future__ import annotations

import argparse
import json
import os
import random
import ssl
import sys
from typing import Any, Dict, Iterator, List, Optional, Tuple

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from compare_endpoints import (  # noqa: E402
    HttpResponseError,
    compare_json,
    fetch_json_with_headers,
    normalize_ignored_paths,
    ssl_context_for,
)

Window = Tuple[int, int]


def windows(head: int, count: int, size: int, seed: int, forks: List[int]) -> List[Window]:
    """Genesis, each named fork, the newest settled blocks, and a reproducible spread between."""
    chosen = [(0, size)]
    chosen += [(fork, size) for fork in forks if fork + size <= head]
    chosen.append((max(0, head - size), size))
    rng = random.Random(seed)
    span = max(1, head - size)
    chosen += [(rng.randrange(span), size) for _ in range(max(0, count - len(chosen)))]
    return sorted({(start, length) for start, length in chosen})[:count]


def fetch(url: str, timeout: int, ctx: Optional[ssl.SSLContext]) -> Tuple[Any, int]:
    try:
        body, _ = fetch_json_with_headers(url, {"User-Agent": "sweep-blocks/1.0"}, timeout, ctx)
        return body, 200
    except HttpResponseError as e:
        return e.body, e.status_code


def blocks_in(base_url: str, start: int, count: int, page_size: int, timeout: int,
              ctx: Optional[ssl.SSLContext]) -> Iterator[Dict[str, Any]]:
    """Blocks [start, start+count), oldest first; /blocks walks backwards from `from`."""
    cursor = start + count - 1
    served = 0
    while served < count and cursor >= start:
        size = min(page_size, count - served)
        body, status = fetch(f"{base_url}/api/v1/blocks?from={cursor}&size={size}", timeout, ctx)
        rows = body.get("data", []) if isinstance(body, dict) else []
        if status != 200 or not rows:
            return
        for row in rows:
            yield row
        served += len(rows)
        cursor = rows[-1]["number"] - 1


def compare_bodies(a: Any, b: Any, ignored: List[str]) -> List[Tuple[str, str]]:
    return compare_json(a, b, ignored_paths=normalize_ignored_paths(ignored))


def sweep_window(
    endpoints: List[Tuple[str, str]],
    window: Window,
    page_size: int,
    timeout: int,
    ctx: Optional[ssl.SSLContext],
    ignored: List[str],
    max_transactions: int,
) -> Dict[str, Any]:
    start, count = window
    (baseline_name, baseline_url), (candidate_name, candidate_url) = endpoints[0], endpoints[1]
    findings: List[Dict[str, Any]] = []

    baseline_blocks = list(blocks_in(baseline_url, start, count, page_size, timeout, ctx))
    candidate_blocks = list(blocks_in(candidate_url, start, count, page_size, timeout, ctx))
    by_number = {block["number"]: block for block in candidate_blocks}

    transactions: List[str] = []
    for block in baseline_blocks:
        mirror = by_number.get(block["number"])
        if mirror is None:
            findings.append({"block": block["number"], "diffs": [["root", "missing on the candidate"]]})
            continue
        diffs = compare_bodies(block, mirror, ignored)
        if diffs:
            findings.append({"block": block["number"], "diffs": [list(d) for d in diffs]})
        transactions.extend(block.get("transactions", []))

    checked = 0
    for tx_id in transactions[:max_transactions]:
        for expanded in ("false", "true"):
            path = f"/api/v1/transactions/{tx_id}?expanded={expanded}"
            first, first_status = fetch(baseline_url + path, timeout, ctx)
            second, second_status = fetch(candidate_url + path, timeout, ctx)
            checked += 1
            if first_status != second_status:
                findings.append({
                    "transaction": tx_id,
                    "diffs": [["status", f"{first_status} != {second_status}"]],
                })
                continue
            diffs = compare_bodies(first, second, ignored)
            if diffs:
                findings.append({"transaction": tx_id, "diffs": [list(d) for d in diffs]})

    return {
        "window": {"start": start, "count": count},
        "blocks_compared": len(baseline_blocks),
        "blocks_missing": max(0, count - len(baseline_blocks)),
        "transactions_compared": checked,
        "findings": findings,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config-file", required=True, help="The endpoints file the suite uses")
    parser.add_argument("--windows", type=int, default=6)
    parser.add_argument("--window-size", type=int, default=50, help="Blocks per window")
    parser.add_argument("--page-size", type=int, default=50)
    parser.add_argument("--max-transactions", type=int, default=50, help="Per window")
    parser.add_argument("--seed", type=int, default=1337)
    parser.add_argument("--forks", default="", help="Comma-separated fork block numbers to include")
    parser.add_argument("--ignore-path", action="append", default=[])
    parser.add_argument("--timeout", type=int, default=30)
    parser.add_argument("--insecure", action="store_true")
    parser.add_argument("--cafile")
    parser.add_argument("--output")
    args = parser.parse_args()

    with open(args.config_file) as f:
        endpoints = list(json.load(f)["endpoints"].items())
    if len(endpoints) < 2:
        print("Error: Need at least 2 endpoints to compare", file=sys.stderr)
        return 2

    ctx = ssl_context_for(args.insecure, args.cafile)
    head_body, _ = fetch(f"{endpoints[1][1]}/api/v1/blocks?size=1", args.timeout, ctx)
    rows = head_body.get("data", []) if isinstance(head_body, dict) else []
    if not rows:
        print("Error: the candidate served no head block", file=sys.stderr)
        return 2
    head = rows[0]["number"]
    forks = [int(f) for f in args.forks.split(",") if f.strip()]

    reports = []
    for window in windows(head, args.windows, args.window_size, args.seed, forks):
        print(f"Sweeping blocks {window[0]}-{window[0] + window[1] - 1}", file=sys.stderr)
        report = sweep_window(
            endpoints, window, args.page_size, args.timeout, ctx, args.ignore_path,
            args.max_transactions,
        )
        print(
            f"  {report['blocks_compared']} blocks, {report['transactions_compared']} transactions,"
            f" {len(report['findings'])} finding(s)",
            file=sys.stderr,
        )
        reports.append(report)

    findings = sum(len(r["findings"]) for r in reports)
    summary = {
        "candidate_head": head,
        "windows": len(reports),
        "blocks_compared": sum(r["blocks_compared"] for r in reports),
        "transactions_compared": sum(r["transactions_compared"] for r in reports),
        "findings": findings,
    }
    print(json.dumps(summary, indent=2))
    if args.output:
        with open(args.output, "w") as f:
            json.dump({"summary": summary, "windows": reports}, f, indent=2)
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
