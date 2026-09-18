#!/usr/bin/env python3
"""
Build seeded test values for API regression comparison.

Every endpoint family draws its identifiers from what the baseline serves right now:
hot addresses and token contracts from the latest transfers, NFT triples from the
latest NFT transfer, Stargate tokens, proposals with comments, and so on. Each seeder
is independent and skipped on any error, so a broken family costs its own cases only.
The validator sample stays reproducible through the seed.
"""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
import random
import sys
import urllib.parse
import urllib.request
from typing import Any, Callable, Dict, Iterable, List, Optional

FetchJson = Callable[[str, int], Any]
DAY = 86_400

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

    validator_detail = updated["path_overrides"].setdefault("/api/v2/validators/{validatorId}", {})
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


class Baseline:
    """Read-only view of the baseline API; `rows` never raises."""

    def __init__(self, base_url: str, fetcher: FetchJson, timeout: int) -> None:
        self.base = base_url.rstrip("/")
        self.fetcher = fetcher
        self.timeout = timeout

    def get(self, path: str, **query: Any) -> Any:
        url = self.base + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        return self.fetcher(url, self.timeout)

    def rows(self, path: str, **query: Any) -> List[Dict[str, Any]]:
        try:
            payload = self.get(path, **query)
        except Exception:
            return []
        if isinstance(payload, dict):
            payload = payload.get("data", [])
        return [row for row in payload if isinstance(row, dict)] if isinstance(payload, list) else []

    def has_data(self, path: str, **query: Any) -> bool:
        return bool(self.rows(path, **query))

    def pick(self, candidates: Iterable[Any], answers: Callable[[Any], bool]) -> Any:
        """The first candidate the baseline actually answers with data; an error is a no."""
        for candidate in dict.fromkeys(c for c in candidates if c is not None):
            try:
                if answers(candidate):
                    return candidate
            except Exception:
                continue
        raise LookupError("no candidate returned data from the baseline")


def merge_values(base: Dict[str, Any], overlay: Dict[str, Any]) -> Dict[str, Any]:
    """Lay a network's chain-specific values over the shared ones, path override by path override."""
    merged = json.loads(json.dumps(base))
    merged.setdefault("parameters", {}).update(overlay.get("parameters", {}))
    paths = merged.setdefault("path_overrides", {})
    for path, params in overlay.get("path_overrides", {}).items():
        if isinstance(params, dict) and isinstance(paths.get(path), dict):
            paths[path] = {**paths[path], **params}
        else:
            paths[path] = params
    return merged


def override(values: Dict[str, Any], path: str, **params: Any) -> None:
    target = values.setdefault("path_overrides", {}).setdefault(path, {})
    for name, value in params.items():
        target[name] = value if isinstance(value, list) or value is None else [value]


def param(values: Dict[str, Any], name: str, *vals: Any) -> None:
    values.setdefault("parameters", {})[name] = list(vals)


def most_common(items: Iterable[Any], skip: Iterable[Any] = ()) -> List[Any]:
    counter = collections.Counter(x for x in items if x is not None and x not in set(skip))
    return [value for value, _ in counter.most_common()]


def first(rows: List[Dict[str, Any]], **where: Any) -> Dict[str, Any]:
    for row in rows:
        if all(row.get(k) == v if v is not None else row.get(k) is None for k, v in where.items()):
            return row
    raise LookupError(f"no row matching {where}")


def seed_head_windows(values: Dict[str, Any], api: Baseline) -> None:
    head = api.get("/api/v1/blocks", size=1)["data"][0]
    number, ts = int(head["number"]), int(head["timestamp"])
    param(values, "startTimestamp", ts - 8 * DAY)
    param(values, "endTimestamp", ts - DAY)
    param(values, "after", ts - 30 * DAY)
    param(values, "before", ts)
    param(values, "blockNumber", number - 1000)
    override(values, "/api/v1/blocks", **{"from": [number - 1000, 22343000]})
    for path in ("/api/v1/validators/block-rewards", "/api/v1/validators/block-rewards/{blockNumber}"):
        override(values, path, blockNumber=number - 1000)
    day = dt.datetime.fromtimestamp(ts, dt.timezone.utc).date()
    override(
        values,
        "/api/v1/b3tr/actions/users/{wallet}/daily-summaries",
        startDate=(day - dt.timedelta(days=30)).isoformat(),
        endDate=day.isoformat(),
    )


def seed_transfers(values: Dict[str, Any], api: Baseline) -> None:
    rows = api.rows("/api/v1/transfers/latest", size=50)
    hot = most_common([r.get(k) for r in rows for k in ("from", "to")], skip=["0x" + "0" * 40])
    param(values, "address", *hot[:3])
    vet = first(rows, tokenAddress=None)
    override(values, "/api/v1/transfers", address=vet["from"])
    override(values, "/api/v1/transfers/from", address=vet["from"])
    override(values, "/api/v1/transfers/to", address=vet["to"])
    override(values, "/api/v1/transfers/forBlock", addresses=vet["to"], blockNumber=vet["blockNumber"])
    override(values, "/api/v1/transfers/fungible-tokens-contracts", address=hot[0])
    override(
        values,
        "/api/v1/accounts/balance/vet/{address}",
        address=vet["from"],
        startTimestamp=vet["blockTimestamp"] - 3600,
        endTimestamp=vet["blockTimestamp"] + 60,
    )
    token_rows = [r for r in rows if r.get("tokenAddress") and r.get("tokenId") is None]
    if token_rows:
        token = token_rows[0]
        param(values, "tokenAddress", token["tokenAddress"])
        override(values, "/api/v1/transactions/contract", contractAddress=token["tokenAddress"])
        override(values, "/api/v1/transfers/to", tokenAddress=None)
        override(values, "/api/v1/transfers/from", tokenAddress=None)
        override(values, "/api/v1/transfers", tokenAddress=None)


def seed_accounts(values: Dict[str, Any], api: Baseline) -> None:
    addresses = list(values.get("parameters", {}).get("address", []))
    addresses += [r.get("origin") for r in api.rows("/api/v1/transactions/latest", size=50)]
    known = api.pick(addresses, lambda a: bool(api.get(f"/api/v1/accounts/overview/{a}")))
    override(values, "/api/v1/accounts/overview/{address}", address=known)


def seed_transactions(values: Dict[str, Any], api: Baseline) -> None:
    rows = api.rows("/api/v1/transactions/latest", size=20)
    param(values, "txId", rows[0]["id"])
    param(values, "txHash", rows[0]["id"])
    param(values, "origin", rows[0]["origin"])
    delegated = [r for r in rows if r.get("gasPayer") and r["gasPayer"] != r["origin"]]
    if delegated:
        param(values, "delegator", delegated[0]["gasPayer"])


def seed_nfts(values: Dict[str, Any], api: Baseline) -> None:
    transfers = api.rows("/api/v1/transfers/latest", size=50, eventType="NFT")
    # A blacklisted collection is transferred but never served, so take one that answers.
    owner = api.pick(
        (r["to"] for r in transfers), lambda address: api.has_data("/api/v1/nfts", address=address)
    )
    nft = api.rows("/api/v1/nfts", address=owner, size=1)[0]
    override(values, "/api/v1/nfts", address=owner, contractAddress=nft["contractAddress"], tokenId=nft["tokenId"])
    override(values, "/api/v1/nfts/contracts", owner=owner)
    override(values, "/api/v1/nfts/history", contractAddress=nft["contractAddress"], tokenId=nft["tokenId"])


STARGATE_SERIES = ("vtho-generated", "vtho-claimed", "vet-delegated", "vet-staked", "nft-holders")


def seed_stargate_series(values: Dict[str, Any], api: Baseline) -> None:
    """Window each series on its own newest point: a stalled series has none near the head.

    The window is wide because the aggregated periods carry one tagged row per bucket, and a
    sparse series on testnet can leave a narrow window holding nothing to compare.
    """
    for series in STARGATE_SERIES:
        newest = api.rows(f"/api/v1/stargate/{series}/BLOCK", size=1, direction="DESC")[0]
        end = int(newest["blockTimestamp"])
        override(
            values, f"/api/v1/stargate/{series}/{{period}}", **{"from": end - 90 * DAY, "to": end}
        )


def seed_stargate(values: Dict[str, Any], api: Baseline) -> None:
    tokens = api.rows("/api/v1/stargate/tokens", size=20)
    token = tokens[0]
    override(values, "/api/v1/stargate/tokens", owner=token["owner"], tokenId=token["tokenId"])
    managed = [t for t in tokens if t.get("manager")]
    if managed:
        param(values, "manager", managed[0]["manager"])
    rewarded = api.pick(
        (t["tokenId"] for t in tokens),
        lambda token_id: api.has_data(f"/api/v1/stargate/token-rewards/{token_id}"),
    )
    override(values, "/api/v1/stargate/token-rewards/{tokenId}", tokenId=rewarded, validator=None)
    with_history = api.pick(
        (t["tokenId"] for t in tokens),
        lambda token_id: api.has_data(f"/api/v1/stargate/tokens/{token_id}/history"),
    )
    events = api.rows(f"/api/v1/stargate/tokens/{with_history}/history", size=5)
    override(
        values,
        "/api/v1/stargate/tokens/{tokenId}/history",
        tokenId=with_history,
        eventName=events[0]["eventName"],
    )
    override(values, "/api/v1/stargate/total-vtho-claimed/{account}", account=token["owner"])
    override(
        values,
        "/api/v1/stargate/total-vtho-claimed/{account}/{tokenId}",
        account=token["owner"],
        tokenId=token["tokenId"],
    )
    delegation = api.rows("/api/v1/validators/delegations", size=20)[0]
    override(
        values,
        "/api/v1/validators/delegations",
        validator=delegation["validator"],
        statuses=delegation["status"],
        tokenId=None,
    )


def seed_b3tr_actions(values: Dict[str, Any], api: Baseline) -> None:
    apps = api.rows("/api/v1/b3tr/actions/leaderboards/apps", size=5)
    users = api.rows("/api/v1/b3tr/actions/leaderboards/users", size=5)
    param(values, "appId", apps[0]["appId"])
    param(values, "wallet", users[0]["wallet"])
    param(values, "user", users[0]["wallet"])
    proposals = api.rows("/api/v2/b3tr/proposals/results", size=20)
    rounds = [int(p["startRoundId"]) for p in proposals if p.get("startRoundId") is not None]
    round_id = api.pick(
        sorted(rounds, reverse=True),
        lambda r: bool(api.get("/api/v1/b3tr/xallocations/earnings", roundId=r)),
    )
    param(values, "roundId", round_id)
    override(values, "/api/v1/b3tr/xallocations/earnings", roundId=round_id, appId=None)
    override(values, "/api/v1/b3tr/xallocations/{roundId}/results", roundId=round_id, appId=None)


def seed_challenges(values: Dict[str, Any], api: Baseline) -> None:
    row = api.rows("/api/v1/b3tr/challenges", size=20)[0]
    param(values, "challengeId", row["challengeId"])
    override(values, "/api/v1/b3tr/challenges", status=row["status"])
    override(values, "/api/v1/b3tr/users/{wallet}/challenges", wallet=row["creator"], filter="MyChallenges")


def seed_navigators(values: Dict[str, Any], api: Baseline) -> None:
    rows = api.rows("/api/v1/b3tr/navigators", size=20)
    navigator = next(r for r in rows if int(r.get("citizenCount") or 0) > 0)["address"]
    param(values, "navigatorId", navigator)
    param(values, "navigator", navigator)
    citizen = api.rows("/api/v1/b3tr/navigators/citizens", navigator=navigator, size=1)[0]["address"]
    param(values, "citizen", citizen)
    # navigator and citizen are both optional but the endpoint demands one of them.
    override(values, "/api/v1/b3tr/navigators/delegations", navigator=navigator, citizen=None)


def seed_proposals(values: Dict[str, Any], api: Baseline) -> None:
    rows = api.rows("/api/v2/b3tr/proposals/results", size=20)
    ids = [r["proposalId"] for r in rows]
    param(values, "proposalId", *ids[:2])
    override(values, "/api/v2/b3tr/proposals/results", states=rows[0]["state"])
    for proposal_id in ids[:10]:
        comments = api.rows(f"/api/v1/b3tr/proposals/{proposal_id}/comments", size=1)
        if comments:
            override(values, "/api/v1/b3tr/proposals/{proposalId}/comments", proposalId=proposal_id)
            override(
                values,
                "/api/v1/b3tr/users/{wallet}/proposals/comments",
                wallet=comments[0]["voter"],
                proposalId=None,
            )
            return
    raise LookupError("no proposal with comments among the latest 10")


def seed_vevote_historic(values: Dict[str, Any], api: Baseline) -> None:
    historic = api.rows("/api/v1/vevote/historic-proposals", size=5)
    if not historic:
        raise LookupError("no historic vevote proposals")
    override(
        values,
        "/api/v1/vevote/historic-proposals",
        proposalId=historic[0]["proposalId"],
        contractAddress=historic[0]["contractAddress"],
    )


def seed_vevote_results(values: Dict[str, Any], api: Baseline) -> None:
    results = api.rows("/api/v1/vevote/proposal/results", support="FOR", size=5)
    if not results:
        raise LookupError("no vevote proposal results")
    override(values, "/api/v1/vevote/proposal/results", proposalId=results[0]["proposalId"])


def seed_treasury(values: Dict[str, Any], api: Baseline) -> None:
    categories = most_common(r.get("category") for r in api.rows("/api/v1/b3tr/treasury/transfers", size=50))
    override(values, "/api/v1/b3tr/treasury/transfers", category=categories[:3] or [None])
    if not categories:
        raise LookupError("no treasury transfers to draw a category from")


def seed_contracts(values: Dict[str, Any], api: Baseline) -> None:
    candidates = values.get("parameters", {}).get("contractAddress", [])
    candidates += [r.get("tokenAddress") for r in api.rows("/api/v1/transfers/latest", size=50)]
    known: List[Dict[str, Any]] = []
    for address in dict.fromkeys(a for a in candidates if a):
        try:
            known.append(api.get(f"/api/v1/contracts/{address}"))
        except Exception:
            continue
        if len(known) == 4:
            break
    override(values, "/api/v1/contracts/{address}", address=[c["address"] for c in known])
    override(
        values,
        "/api/v1/contracts/by-master/{address}",
        address=api.pick(
            (c.get("master") for c in known),
            lambda master: api.has_data(f"/api/v1/contracts/by-master/{master}"),
        ),
    )


def seed_safes(values: Dict[str, Any], api: Baseline) -> None:
    configured = values.get("path_overrides", {}).get("/api/v1/safes/owner/{address}", {})
    candidates = list(configured.get("address") or [])
    candidates += list(values.get("parameters", {}).get("address", []))
    candidates += [r.get(k) for r in api.rows("/api/v1/transfers/latest", size=50) for k in ("from", "to")]
    candidates += [r.get("origin") for r in api.rows("/api/v1/transactions/latest", size=50)]
    for owner in dict.fromkeys(a for a in candidates if a):
        memberships = api.rows(f"/api/v1/safes/owner/{owner}", size=1)
        if not memberships:
            continue
        safe = memberships[0]["safe"]
        override(values, "/api/v1/safes/owner/{address}", address=owner)
        override(values, "/api/v1/safes/{safe}/transactions", safe=safe)
        proposals = api.rows(f"/api/v1/safes/{safe}/transactions", size=1)
        if proposals:
            override(
                values,
                "/api/v1/safes/{safe}/transactions/{txHash}/state",
                safe=safe,
                txHash=proposals[0]["txHash"],
            )
        return
    raise LookupError("no safe owner among the sampled addresses")


def seed_history(values: Dict[str, Any], api: Baseline) -> None:
    account = api.pick(
        values["parameters"]["address"], lambda a: api.has_data(f"/api/v2/history/{a}")
    )
    row = api.rows(f"/api/v2/history/{account}", size=20)[0]
    # eventName and contractAddress together are usually over-restrictive, so each is a variant.
    for path in ("/api/v1/history/{account}", "/api/v2/history/{account}"):
        override(values, path, account=account, eventName=None, contractAddress=None)
    param(values, "eventName", row["eventName"])


SEEDERS: List[Callable[[Dict[str, Any], Baseline], None]] = [
    seed_head_windows,
    seed_transfers,
    seed_accounts,
    seed_transactions,
    seed_nfts,
    seed_stargate,
    seed_stargate_series,
    seed_b3tr_actions,
    seed_challenges,
    seed_navigators,
    seed_proposals,
    seed_vevote_historic,
    seed_vevote_results,
    seed_treasury,
    seed_contracts,
    seed_safes,
    seed_history,
]


def seed_families(values: Dict[str, Any], api: Baseline) -> Dict[str, str]:
    """Run every seeder against a copy of *values*; returns each seeder's outcome."""
    outcomes: Dict[str, str] = {}
    for seeder in SEEDERS:
        snapshot = json.loads(json.dumps(values))
        try:
            seeder(values, api)
            outcomes[seeder.__name__] = "ok"
        except Exception as exc:  # a family that cannot be seeded keeps its static values
            values.clear()
            values.update(snapshot)
            outcomes[seeder.__name__] = f"skipped: {type(exc).__name__}: {exc}"
    return outcomes


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
    seeders: Optional[Dict[str, str]] = None,
) -> dict[str, Any]:
    return {
        "seeders": seeders or {},
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
    parser.add_argument("--input", required=True, help="Shared test_values.json input")
    parser.add_argument("--overlay", help="Network test values laid over the shared ones")
    parser.add_argument(
        "--merge-only",
        action="store_true",
        help="Write the merged static values without reading the baseline",
    )
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

    if args.overlay:
        with open(args.overlay) as f:
            test_values = merge_values(test_values, json.load(f))

    if args.merge_only:
        with open(args.output, "w") as f:
            json.dump(test_values, f, indent=2)
        print("  seeding skipped; the network's static values stand alone", file=sys.stderr)
        return 0

    outcomes = seed_families(test_values, Baseline(args.baseline_url, fetch_json, args.timeout))
    for name, outcome in outcomes.items():
        print(f"  {name}: {outcome}", file=sys.stderr)

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
        seeders=outcomes,
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
