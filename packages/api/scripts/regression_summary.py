#!/usr/bin/env python3
"""Render the regression report as the GitHub step summary."""

from __future__ import annotations

import argparse
import html
import json
import pathlib
from typing import Any, Dict, List

esc = html.escape


def _codes(result: Dict[str, Any]) -> str:
    return ", ".join(f"{esc(str(name))}: {code}" for name, code in result.get("status_codes", {}).items())


def _diff_block(title: str, diffs: Dict[str, List], marker: str) -> List[str]:
    lines: List[str] = []
    for pair, diff_list in diffs.items():
        if not diff_list:
            continue
        lines += [f"**{title}** (`{esc(pair)}`): {len(diff_list)}", "", "```"]
        lines += [f"  {marker}{json_path}: {description}" for json_path, description in diff_list[:20]]
        if len(diff_list) > 20:
            lines.append(f"  ... and {len(diff_list) - 20} more")
        lines += ["```", ""]
    return lines


def _details(result: Dict[str, Any], body: List[str]) -> List[str]:
    method = esc(result["method"].upper())
    return [
        "<details>",
        f"<summary><code>{method} {esc(result['path'])}</code> — {esc(result.get('label', ''))} "
        f"(status: {_codes(result)})</summary>",
        "",
        *body,
        "</details>",
        "",
    ]


def render(report: Dict[str, Any], seed: Dict[str, Any] | None, candidate: str, baseline: str) -> str:
    s = report["summary"]
    results = report["results"]
    by_status: Dict[str, List[Dict[str, Any]]] = {}
    for r in results:
        by_status.setdefault(r["status"], []).append(r)

    lines = [
        "### Regression Comparison (candidate vs baseline)",
        "",
        "| | |",
        "|---|---|",
        f"| **Candidate** | `{candidate}` |",
        f"| **Baseline** | `{baseline}` |",
        f"| **Total** | {s['total']} |",
        f"| **Passed with data** | {s['passed']} |",
        f"| **Vacuous** | {s.get('vacuous', 0)} |",
        f"| **Tolerated drift** | {s.get('tolerated', 0)} |",
        f"| **Deprecated Reported** | {s.get('deprecated', 0)} |",
        f"| **Failed** | {s['failed']} |",
        "",
    ]

    if seed:
        lines += [
            "### Validator Sampling",
            "",
            "| | |",
            "|---|---|",
            f"| **Seed** | {seed.get('validatorSeed')} |",
            f"| **Candidate pool** | {seed.get('candidatePoolSize')} |",
            f"| **Preferred pool** | {seed.get('preferredPoolSize')} |",
            f"| **Sample size** | {seed.get('actualSampleSize')} / {seed.get('requestedSampleSize')} |",
            f"| **Pages per sort** | {seed.get('validatorQueryPageCount')} |",
            f"| **Page size** | {seed.get('validatorQueryPageSize')} |",
            f"| **Sort fields** | `{', '.join(seed.get('validatorQuerySortFields', []))}` |",
            "",
        ]
        if seed.get("sampledValidatorIds"):
            lines += ["Sampled validator IDs:", ""]
            lines += [f"- `{esc(str(v))}`" for v in seed["sampledValidatorIds"]]
            lines.append("")

    no_data = s.get("operations_without_data", [])
    if no_data:
        lines += [
            "### Operations that compared no data",
            "",
            "Every case for these operations returned an empty or error response from the "
            "baseline, so the run proved nothing about them. Fix the seed values.",
            "",
        ]
        lines += [f"- <code>{esc(op)}</code>" for op in no_data]
        lines.append("")

    if by_status.get("fail"):
        lines += ["### Failed endpoints", ""]
        for r in by_status["fail"]:
            body: List[str] = []
            if r.get("errors"):
                body.append("**Errors:**")
                body += [f"- `{esc(str(ep))}`: {esc(str(msg))}" for ep, msg in r["errors"].items()]
                body.append("")
            body += _diff_block("Diffs", r.get("diffs", {}), "")
            lines += _details(r, body)
    elif not by_status.get("deprecated") and not by_status.get("tolerated"):
        lines.append("All endpoints matched.")
    else:
        lines.append("No non-deprecated endpoints failed.")

    if by_status.get("tolerated"):
        lines += [
            "",
            "### Tolerated drift",
            "",
            "These endpoints differed by amounts within the configured numeric tolerance and "
            "did not fail the run. Typically caused by chain-tip drift between the two indexers "
            "(e.g. `endBlock` ±1, holder counts ±1).",
            "",
        ]
        for r in by_status["tolerated"]:
            lines += _details(r, _diff_block("Tolerated diffs", r.get("tolerated_diffs", {}), "~ "))

    if by_status.get("deprecated"):
        lines += ["", "### Deprecated Endpoints Reported", ""]
        lines += [
            f"- <code>{esc(r['method'].upper())} {esc(r['path'])}</code> — {esc(r.get('label', ''))}"
            for r in by_status["deprecated"]
        ]

    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True)
    parser.add_argument("--seed-metadata")
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--output", required=True, help="File to append the markdown to")
    args = parser.parse_args()

    report_path = pathlib.Path(args.report)
    if not report_path.exists():
        text = (
            "### Regression Comparison\n"
            "> **No report generated** — the regression script may have failed before writing output.\n"
        )
    else:
        seed = None
        if args.seed_metadata and pathlib.Path(args.seed_metadata).exists():
            seed = json.loads(pathlib.Path(args.seed_metadata).read_text())
        text = render(json.loads(report_path.read_text()), seed, args.candidate, args.baseline)

    with open(args.output, "a") as f:
        f.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
