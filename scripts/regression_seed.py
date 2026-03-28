#!/usr/bin/env python3
"""
Build seeded test values for API regression comparison.

The validator sample should be broad enough to catch response-shape regressions,
while still being reproducible across reruns of the same manual workflow.
"""

from __future__ import annotations

import argparse
import json
import random
import sys
import urllib.parse
import urllib.request
from typing import Any, Callable

FetchJson = Callable[[str, int], Any]

DEFAULT_SORT_FIELDS = ["validatorTvl", "totalTvl", "delegatorTvl", "blockProbability"]
DEFAULT_STATUSES = ["ACTIVE"]


def fetch_json(url: str, timeout: int) -> Any:
    req = urllib.request.Request(url, headers={"User-Agent": "regression-seed/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def dedupe_preserving_order(values: list[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        ordered.append(value)
    return ordered


def build_validator_query_urls(
    baseline_url: str,
    *,
    statuses: list[str],
    sort_fields: list[str],
    page_count: int,
    page_size: int,
) -> list[str]:
    urls: list[str] = []
    base = baseline_url.rstrip("/")
    for status in statuses:
        for sort_by in sort_fields:
            for page in range(page_count):
                query = urllib.parse.urlencode(
                    {
                        "status": status,
                        "page": page,
                        "size": page_size,
                        "sortBy": sort_by,
                    }
                )
                urls.append(f"{base}/api/v1/validators?{query}")
    return urls


def collect_validator_candidates(
    baseline_url: str,
    *,
    timeout: int,
    page_count: int,
    page_size: int,
    sort_fields: list[str] | None = None,
    statuses: list[str] | None = None,
    fetcher: FetchJson = fetch_json,
) -> tuple[list[str], list[str]]:
    urls = build_validator_query_urls(
        baseline_url,
        statuses=statuses or DEFAULT_STATUSES,
        sort_fields=sort_fields or DEFAULT_SORT_FIELDS,
        page_count=page_count,
        page_size=page_size,
    )

    all_ids: list[str] = []
    preferred_ids: list[str] = []

    for url in urls:
        payload = fetcher(url, timeout)
        for validator in payload.get("data", []):
            validator_id = validator.get("id")
            if not validator_id:
                continue
            all_ids.append(validator_id)
            if validator.get("nftYieldsNextCycle"):
                preferred_ids.append(validator_id)

    return dedupe_preserving_order(all_ids), dedupe_preserving_order(preferred_ids)


def choose_validator_sample(
    candidate_ids: list[str],
    preferred_ids: list[str],
    *,
    sample_size: int,
    seed: int,
) -> list[str]:
    preferred = [validator_id for validator_id in preferred_ids if validator_id in candidate_ids]
    preferred_set = set(preferred)
    remaining = [validator_id for validator_id in candidate_ids if validator_id not in preferred_set]

    rng = random.Random(seed)
    rng.shuffle(preferred)
    rng.shuffle(remaining)

    sample = preferred[:sample_size]
    if len(sample) < sample_size:
        sample.extend(remaining[: sample_size - len(sample)])
    return sample


def apply_validator_sample(
    test_values: dict[str, Any],
    sampled_validator_ids: list[str],
    *,
    validator_pages: list[int],
    validator_sort_fields: list[str],
) -> dict[str, Any]:
    updated = json.loads(json.dumps(test_values))
    updated.setdefault("parameters", {})
    updated.setdefault("path_overrides", {})

    if not sampled_validator_ids:
        return updated

    validator_detail = updated["path_overrides"].setdefault("/api/v1/validators/{validatorId}", {})
    validator_detail["validatorId"] = sampled_validator_ids

    validator_list = updated["path_overrides"].setdefault("/api/v1/validators", {})
    validator_list["page"] = validator_pages
    validator_list["sortBy"] = validator_sort_fields

    validator_query_sample = sampled_validator_ids[: min(5, len(sampled_validator_ids))]
    validator_historic = updated["path_overrides"].setdefault(
        "/api/v1/validators/blocks/historic/{validator}",
        {},
    )
    validator_historic["validator"] = validator_query_sample

    # Keep generic validator parameters narrow so unrelated endpoints do not explode in case count.
    updated["parameters"]["validator"] = validator_query_sample[:1]
    updated["parameters"]["validatorId"] = sampled_validator_ids[:1]
    return updated


def build_seed_metadata(
    *,
    seed: int,
    sample_size: int,
    page_count: int,
    page_size: int,
    sort_fields: list[str],
    statuses: list[str],
    candidate_ids: list[str],
    preferred_ids: list[str],
    sampled_validator_ids: list[str],
) -> dict[str, Any]:
    return {
        "validatorSeed": seed,
        "requestedSampleSize": sample_size,
        "actualSampleSize": len(sampled_validator_ids),
        "validatorQueryPageCount": page_count,
        "validatorQueryPageSize": page_size,
        "validatorQuerySortFields": sort_fields,
        "validatorQueryStatuses": statuses,
        "candidatePoolSize": len(candidate_ids),
        "preferredPoolSize": len(preferred_ids),
        "sampledValidatorIds": sampled_validator_ids,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed regression test values from a baseline API.")
    parser.add_argument("--baseline-url", required=True)
    parser.add_argument("--input", required=True, help="Base test_values.json input")
    parser.add_argument("--output", required=True, help="Output path for seeded test values")
    parser.add_argument("--metadata-output", help="Optional output path for sampling metadata")
    parser.add_argument("--timeout", type=int, default=15)
    parser.add_argument("--validator-sample-size", type=int, default=20)
    parser.add_argument("--validator-page-size", type=int, default=20)
    parser.add_argument("--validator-page-count", type=int, default=3)
    parser.add_argument("--validator-seed", type=int, default=1337)
    args = parser.parse_args()

    with open(args.input) as f:
        test_values = json.load(f)

    sort_fields = list(DEFAULT_SORT_FIELDS)
    statuses = list(DEFAULT_STATUSES)
    candidate_ids, preferred_ids = collect_validator_candidates(
        args.baseline_url,
        timeout=args.timeout,
        page_count=args.validator_page_count,
        page_size=args.validator_page_size,
        sort_fields=sort_fields,
        statuses=statuses,
    )

    if not candidate_ids:
        raise RuntimeError("No validator IDs discovered from baseline")

    sampled_validator_ids = choose_validator_sample(
        candidate_ids,
        preferred_ids,
        sample_size=args.validator_sample_size,
        seed=args.validator_seed,
    )
    seeded_values = apply_validator_sample(
        test_values,
        sampled_validator_ids,
        validator_pages=list(range(args.validator_page_count)),
        validator_sort_fields=sort_fields,
    )

    with open(args.output, "w") as f:
        json.dump(seeded_values, f, indent=2)

    metadata = build_seed_metadata(
        seed=args.validator_seed,
        sample_size=args.validator_sample_size,
        page_count=args.validator_page_count,
        page_size=args.validator_page_size,
        sort_fields=sort_fields,
        statuses=statuses,
        candidate_ids=candidate_ids,
        preferred_ids=preferred_ids,
        sampled_validator_ids=sampled_validator_ids,
    )

    if args.metadata_output:
        with open(args.metadata_output, "w") as f:
            json.dump(metadata, f, indent=2)

    print(
        "  Validator sampling seed="
        f"{args.validator_seed} pool={len(candidate_ids)} preferred={len(preferred_ids)} "
        f"sample={len(sampled_validator_ids)}",
        file=sys.stderr,
    )
    print(
        "  Sampled validator IDs: " + ", ".join(sampled_validator_ids),
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
