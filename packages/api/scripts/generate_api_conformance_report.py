#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
import textwrap
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


SOFT_CHECKS = {"max_response_time"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Summarize Schemathesis results and classify hard vs soft issues."
    )
    parser.add_argument("--results-xml", required=True, help="Path to the Schemathesis JUnit XML")
    parser.add_argument("--log", required=False, help="Path to the Schemathesis console log")
    parser.add_argument("--output-json", required=True, help="Where to write the JSON report")
    parser.add_argument("--output-markdown", required=True, help="Where to write the markdown report")
    parser.add_argument(
        "--schemathesis-exit-code",
        type=int,
        default=0,
        help="Raw Schemathesis process exit code",
    )
    parser.add_argument(
        "--base-url",
        required=False,
        help="Base URL under test, for report metadata",
    )
    return parser.parse_args()


def infer_check(text: str) -> str:
    lowered = text.lower()
    if "response time limit exceeded" in lowered or "max_response_time" in lowered:
        return "max_response_time"
    if (
        "request timeout" in lowered
        or "response timeout" in lowered
        or "read timed out" in lowered
        or "network error" in lowered
    ):
        return "request_timeout"
    if "content type" in lowered or "content_type_conformance" in lowered:
        return "content_type_conformance"
    if "status code" in lowered or "status_code_conformance" in lowered:
        return "status_code_conformance"
    if "server error" in lowered or "not_a_server_error" in lowered:
        return "not_a_server_error"
    return "unknown"


def summarize_issue_text(text: str) -> str:
    summary_text = strip_reproduction_section(text)

    for line in summary_text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("1. Test Case ID:"):
            continue
        stripped = re.sub(r"^\d+\.\s*Test Case ID:\s*[A-Za-z0-9_-]+\s*-\s*", "", stripped)
        if stripped.startswith("- "):
            return stripped[2:].strip()
        if stripped.startswith("[") and "]" in stripped:
            continue
        return stripped.strip()

    collapsed = collapse_whitespace(summary_text)
    collapsed = re.sub(r"^\d+\.\s*Test Case ID:\s*[A-Za-z0-9_-]+\s*-\s*", "", collapsed)
    return collapsed or "Unclassified issue"


def collapse_whitespace(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def extract_reproduction_command(text: str) -> str | None:
    lines = text.splitlines()
    for idx, line in enumerate(lines):
        if line.strip().lower() != "reproduce with:":
            continue

        command_lines: list[str] = []
        for candidate in lines[idx + 1 :]:
            stripped = candidate.strip()
            if not stripped:
                if command_lines:
                    break
                continue
            if command_lines and not candidate.startswith((" ", "\t")):
                break
            if stripped.lower().startswith("need more help?"):
                break
            command_lines.append(stripped)

        if command_lines:
            return " ".join(command_lines)

    inline_match = re.search(r"Reproduce with:\s*(curl\b.+)$", text, flags=re.IGNORECASE)
    if inline_match:
        return collapse_whitespace(inline_match.group(1))

    return None


def strip_reproduction_section(text: str) -> str:
    lines = text.splitlines()
    kept_lines: list[str] = []
    skipping_reproduction = False

    for line in lines:
        stripped = line.strip()

        if stripped.lower() == "reproduce with:":
            skipping_reproduction = True
            continue

        if skipping_reproduction:
            if not stripped:
                continue
            if line.startswith((" ", "\t")) or stripped.lower().startswith("curl "):
                continue
            if stripped.lower().startswith("need more help?"):
                break
            skipping_reproduction = False

        kept_lines.append(line)

    cleaned = "\n".join(kept_lines)
    cleaned = re.sub(r"\s+Reproduce with:\s*curl\b.+$", "", cleaned, flags=re.IGNORECASE)
    return cleaned.strip()


def extract_test_case_id(text: str) -> str | None:
    match = re.search(r"Test Case ID:\s*([A-Za-z0-9_-]+)", text)
    return match.group(1) if match else None


def parse_junit_xml(results_xml: Path) -> tuple[list[dict], dict]:
    if not results_xml.exists():
        return [], {"tests": 0, "failures": 0, "errors": 0}

    tree = ET.parse(results_xml)
    root = tree.getroot()

    tests = failures = errors = 0
    issues: list[dict] = []

    for suite in root.iter():
        if suite.tag.endswith("testsuite"):
            tests += int(suite.attrib.get("tests", 0) or 0)
            failures += int(suite.attrib.get("failures", 0) or 0)
            errors += int(suite.attrib.get("errors", 0) or 0)

    for testcase in root.iter():
        if not testcase.tag.endswith("testcase"):
            continue

        endpoint = testcase.attrib.get("name") or testcase.attrib.get("classname") or "Unknown test"

        for child in testcase:
            tag = child.tag.rsplit("}", 1)[-1]
            if tag not in {"failure", "error"}:
                continue

            raw_text = "\n".join(
                part for part in [child.attrib.get("message", "").strip(), (child.text or "").strip()] if part
            ).strip()
            check = infer_check(raw_text)
            severity = "soft" if check in SOFT_CHECKS else "hard"

            issues.append(
                {
                    "endpoint": endpoint,
                    "kind": tag,
                    "severity": severity,
                    "check": check,
                    "title": summarize_issue_text(raw_text),
                    "test_case_id": extract_test_case_id(raw_text),
                    "details": strip_reproduction_section(raw_text),
                    "reproduce_with": extract_reproduction_command(raw_text),
                    "raw_details": raw_text,
                }
            )

    return issues, {"tests": tests, "failures": failures, "errors": errors}


def parse_log(log_path: Path | None) -> tuple[dict, list[str]]:
    if log_path is None or not log_path.exists():
        return {}, []

    text = log_path.read_text()
    checks: dict[str, dict] = {}
    warnings: list[str] = []

    check_pattern = re.compile(
        r"^\s*([a-zA-Z0-9_]+)\s+(\d+)\s*/\s*(\d+)\s+passed\s+(PASSED|FAILED)\s*$"
    )
    warning_pattern = re.compile(r"^\s*-\s+(.*)$")

    in_warnings = False
    for line in text.splitlines():
        match = check_pattern.match(line)
        if match:
            checks[match.group(1)] = {
                "passed": int(match.group(2)),
                "total": int(match.group(3)),
                "status": match.group(4),
            }
            continue

        stripped = line.strip()
        if stripped in {"WARNINGS:", "Warnings:"}:
            in_warnings = True
            continue

        if in_warnings:
            warning_match = warning_pattern.match(line)
            if warning_match:
                warnings.append(warning_match.group(1))
                continue
            if stripped.startswith("Note:") or stripped.startswith("💡"):
                continue
            if stripped.startswith("=") or stripped.startswith("SUMMARY") or not stripped:
                if stripped.startswith("=") or stripped.startswith("SUMMARY"):
                    in_warnings = False
                continue
            in_warnings = False

    return checks, warnings


def build_report(args: argparse.Namespace) -> dict:
    results_xml = Path(args.results_xml)
    log_path = Path(args.log) if args.log else None

    issues, junit_summary = parse_junit_xml(results_xml)
    checks, warnings = parse_log(log_path)

    hard_issues = [issue for issue in issues if issue["severity"] == "hard"]
    soft_issues = [issue for issue in issues if issue["severity"] == "soft"]
    check_counts = Counter(issue["check"] for issue in issues)

    report = {
        "metadata": {
            "results_xml": str(results_xml),
            "log": str(log_path) if log_path else None,
            "base_url": args.base_url,
            "schemathesis_exit_code": args.schemathesis_exit_code,
        },
        "summary": {
            "tests": junit_summary["tests"],
            "failures": junit_summary["failures"],
            "errors": junit_summary["errors"],
            "issues_total": len(issues),
            "hard_issues": len(hard_issues),
            "soft_issues": len(soft_issues),
            "warnings_total": len(warnings),
            "status": "fail" if hard_issues else "pass",
            "checks": checks,
            "issue_counts_by_check": dict(sorted(check_counts.items())),
        },
        "issues": issues,
        "warnings": warnings,
    }

    if not results_xml.exists():
        report["summary"]["status"] = "fail" if args.schemathesis_exit_code else "pass"
        report["summary"]["missing_results_xml"] = True

    return report


def render_markdown(report: dict) -> str:
    summary = report["summary"]
    lines = [
        "### API Conformance Report",
        "",
        "| | |",
        "|---|---|",
        f"| **Status** | {'Fail' if summary['status'] == 'fail' else 'Pass'} |",
        f"| **Hard issues** | {summary['hard_issues']} |",
        f"| **Soft issues** | {summary['soft_issues']} |",
        f"| **Warnings** | {summary['warnings_total']} |",
        f"| **Schemathesis exit code** | {report['metadata']['schemathesis_exit_code']} |",
    ]

    if report["metadata"].get("base_url"):
        lines.append(f"| **Base URL** | `{report['metadata']['base_url']}` |")

    checks = summary.get("checks") or {}
    if checks:
        lines.extend(["", "### Check Summary", "", "| Check | Passed | Total | Status |", "|---|---:|---:|---|"])
        for name, data in checks.items():
            lines.append(
                f"| `{name}` | {data['passed']} | {data['total']} | {data['status']} |"
            )

    for section_name, severity in [("Hard issues", "hard"), ("Soft issues", "soft")]:
        matching = [issue for issue in report["issues"] if issue["severity"] == severity]
        if not matching:
            continue

        lines.extend(["", f"### {section_name}", ""])
        for issue in matching[:20]:
            lines.append("<details>")
            lines.append(
                f"<summary><code>{issue['endpoint']}</code> — {issue['title']} [{issue['check']}]</summary>"
            )
            lines.append("")
            if issue.get("test_case_id"):
                lines.append(f"- Test case ID: `{issue['test_case_id']}`")
            lines.append(f"- Kind: `{issue['kind']}`")
            if issue.get("reproduce_with"):
                lines.append("- Reproduce with:")
                lines.append("")
                lines.append("```sh")
                lines.append(issue["reproduce_with"])
                lines.append("```")
            lines.append("")
            lines.append("```text")
            lines.append(textwrap.shorten(issue["details"], width=4000, placeholder=" ..."))
            lines.append("```")
            lines.append("</details>")
            lines.append("")

        if len(matching) > 20:
            lines.append(f"... and {len(matching) - 20} more {severity} issues.")

    if report["warnings"]:
        lines.extend(["", "### Warnings", ""])
        for warning in report["warnings"][:20]:
            lines.append(f"- {warning}")
        if len(report["warnings"]) > 20:
            lines.append(f"- ... and {len(report['warnings']) - 20} more warnings")

    if summary.get("missing_results_xml"):
        lines.extend(
            [
                "",
                "> Results XML was not generated. Treating this as an infrastructure/reporting failure.",
            ]
        )

    return "\n".join(lines) + "\n"


def main() -> int:
    args = parse_args()
    report = build_report(args)

    output_json = Path(args.output_json)
    output_markdown = Path(args.output_markdown)
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_markdown.parent.mkdir(parents=True, exist_ok=True)

    output_json.write_text(json.dumps(report, indent=2) + "\n")
    output_markdown.write_text(render_markdown(report))

    print(json.dumps(report["summary"], indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
