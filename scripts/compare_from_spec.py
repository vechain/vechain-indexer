#!/usr/bin/env python3
"""
compare_from_spec.py

Fetch the OpenAPI spec from an API endpoint, auto-generate test requests based
on the spec, and compare responses across multiple endpoint deployments.

Usage:
  # Basic: fetch spec from first endpoint, compare all operations
  python compare_from_spec.py --config-file endpoints.json

  # Specify spec URL explicitly
  python compare_from_spec.py --config-file endpoints.json --spec-url https://api.example.com/api-docs

  # Filter to specific paths
  python compare_from_spec.py --config-file endpoints.json --path-filter "/api/v1/stargate.*"

  # With custom test values
  python compare_from_spec.py --config-file endpoints.json --test-values test_values.json

  # Dry run to preview generated test cases
  python compare_from_spec.py --config-file endpoints.json --dry-run

  # Save detailed JSON report
  python compare_from_spec.py --config-file endpoints.json --output report.json

Exit codes:
  0 -> All responses match across endpoints
  1 -> Some responses differ
  2 -> Runtime or input error
"""

import argparse
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional, Set, Tuple

# Ensure sibling modules can be imported regardless of cwd
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from compare_endpoints import (
    compare_json,
    fetch_json,
    normalize_ignored_paths,
    ssl_context_for,
)

Path = str


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class Parameter:
    name: str
    location: str  # path, query, header, cookie
    required: bool = False
    schema_type: str = "string"
    schema_format: Optional[str] = None
    enum: Optional[List[Any]] = None
    default: Optional[Any] = None
    example: Optional[Any] = None
    description: str = ""


@dataclass
class RequestBody:
    content_type: str = "application/json"
    schema: Optional[Dict] = None
    example: Optional[Any] = None
    required: bool = False


@dataclass
class Operation:
    path: str
    method: str
    operation_id: Optional[str] = None
    summary: str = ""
    deprecated: bool = False
    tags: List[str] = field(default_factory=list)
    parameters: List[Parameter] = field(default_factory=list)
    request_body: Optional[RequestBody] = None


@dataclass
class TestCase:
    operation: Operation
    path_params: Dict[str, Any] = field(default_factory=dict)
    query_params: Dict[str, Any] = field(default_factory=dict)
    headers: Dict[str, str] = field(default_factory=dict)
    body: Optional[Any] = None
    label: str = ""

    @property
    def resolved_path(self) -> str:
        path = self.operation.path
        for name, value in self.path_params.items():
            path = path.replace(f"{{{name}}}", str(value))
        return path

    @property
    def full_path(self) -> str:
        path = self.resolved_path
        if self.query_params:
            qs = urllib.parse.urlencode(self.query_params, doseq=True)
            path = f"{path}?{qs}"
        return path


@dataclass
class ComparisonResult:
    test_case: TestCase
    responses: Dict[str, Any]
    status_codes: Dict[str, int]
    diffs: Dict[str, List[Tuple[str, str]]]
    errors: Dict[str, str]

    @property
    def all_match(self) -> bool:
        return not self.errors and all(len(d) == 0 for d in self.diffs.values())

    @property
    def has_mismatch(self) -> bool:
        return bool(self.errors) or any(len(d) > 0 for d in self.diffs.values())

    @property
    def ignored_due_to_deprecation(self) -> bool:
        return self.test_case.operation.deprecated and self.has_mismatch

    @property
    def effective_pass(self) -> bool:
        return self.all_match or self.ignored_due_to_deprecation


# ---------------------------------------------------------------------------
# OpenAPI spec parsing
# ---------------------------------------------------------------------------

def resolve_ref(ref: str, spec: Dict) -> Dict:
    """Resolve a JSON $ref pointer within the spec."""
    if not ref.startswith("#/"):
        return {}
    parts = ref[2:].split("/")
    current: Any = spec
    for part in parts:
        part = part.replace("~1", "/").replace("~0", "~")
        if isinstance(current, dict) and part in current:
            current = current[part]
        else:
            return {}
    return current if isinstance(current, dict) else {}


def fetch_spec(url: str, timeout: int = 30, insecure: bool = False) -> Dict:
    """Fetch and parse an OpenAPI spec from a URL."""
    ctx = ssl_context_for(insecure, None)
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "compare-from-spec/1.0", "Accept": "application/json"},
    )
    try:
        if url.lower().startswith("https"):
            opener = urllib.request.build_opener(
                urllib.request.HTTPSHandler(context=ctx)
            )
            with opener.open(req, timeout=timeout) as resp:
                data = resp.read()
        else:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                data = resp.read()
        return json.loads(data.decode("utf-8"))
    except Exception as e:
        raise RuntimeError(f"Failed to fetch OpenAPI spec from {url}: {e}")


def detect_spec_url(base_url: str, insecure: bool = False) -> Optional[str]:
    """Probe common OpenAPI spec paths on *base_url*."""
    common_paths = [
        "/v3/api-docs",
        "/swagger.json",
        "/openapi.json",
        "/api-docs",
        "/v2/api-docs",
        "/swagger/v1/swagger.json",
        "/openapi/v3/api-docs",
    ]
    ctx = ssl_context_for(insecure, None)
    for path in common_paths:
        url = base_url.rstrip("/") + path
        try:
            req = urllib.request.Request(
                url, headers={"User-Agent": "compare-from-spec/1.0"}
            )
            if url.lower().startswith("https"):
                opener = urllib.request.build_opener(
                    urllib.request.HTTPSHandler(context=ctx)
                )
                with opener.open(req, timeout=10) as resp:
                    data = resp.read().decode("utf-8")
            else:
                with urllib.request.urlopen(req, timeout=10) as resp:
                    data = resp.read().decode("utf-8")
            spec = json.loads(data)
            if "openapi" in spec or "swagger" in spec:
                return url
        except Exception:
            continue
    return None


def parse_parameter(param_dict: Dict, spec: Dict) -> Parameter:
    if "$ref" in param_dict:
        param_dict = resolve_ref(param_dict["$ref"], spec)
    schema = param_dict.get("schema", {})
    if "$ref" in schema:
        schema = resolve_ref(schema["$ref"], spec)
    return Parameter(
        name=param_dict.get("name", ""),
        location=param_dict.get("in", "query"),
        required=param_dict.get("required", False),
        schema_type=schema.get("type", "string"),
        schema_format=schema.get("format"),
        enum=schema.get("enum"),
        default=schema.get("default"),
        example=param_dict.get("example") or schema.get("example"),
        description=param_dict.get("description", ""),
    )


def parse_request_body(body_dict: Dict, spec: Dict) -> Optional[RequestBody]:
    if not body_dict:
        return None
    if "$ref" in body_dict:
        body_dict = resolve_ref(body_dict["$ref"], spec)

    content = body_dict.get("content", {})
    preferred = [
        "application/json",
        "application/x-www-form-urlencoded",
        "multipart/form-data",
    ]
    for ct in preferred:
        if ct not in content:
            continue
        media = content[ct]
        schema = media.get("schema", {})
        if "$ref" in schema:
            schema = resolve_ref(schema["$ref"], spec)
        example = media.get("example")
        if example is None:
            examples = media.get("examples", {})
            if isinstance(examples, dict) and examples:
                first = next(iter(examples.values()))
                if isinstance(first, dict) and "value" in first:
                    example = first["value"]
        return RequestBody(
            content_type=ct,
            schema=schema,
            example=example,
            required=body_dict.get("required", False),
        )
    return None


def parse_operations(spec: Dict) -> List[Operation]:
    """Extract all operations from an OpenAPI spec."""
    operations: List[Operation] = []
    for path, path_item in spec.get("paths", {}).items():
        if not isinstance(path_item, dict):
            continue
        path_params = [
            parse_parameter(p, spec) for p in path_item.get("parameters", [])
        ]
        for method in ("get", "post", "put", "patch", "delete", "head", "options"):
            op_dict = path_item.get(method)
            if not isinstance(op_dict, dict):
                continue
            op_params = [
                parse_parameter(p, spec) for p in op_dict.get("parameters", [])
            ]
            op_param_names = {p.name for p in op_params}
            merged = op_params + [
                p for p in path_params if p.name not in op_param_names
            ]
            operations.append(
                Operation(
                    path=path,
                    method=method.upper(),
                    operation_id=op_dict.get("operationId"),
                    summary=op_dict.get("summary", ""),
                    deprecated=op_dict.get("deprecated", False),
                    tags=op_dict.get("tags", []),
                    parameters=merged,
                    request_body=parse_request_body(
                        op_dict.get("requestBody", {}), spec
                    ),
                )
            )
    return operations


# ---------------------------------------------------------------------------
# Test-value generation
# ---------------------------------------------------------------------------

_SKIP = object()  # sentinel: "don't send this parameter"

_DEFAULT_ADDRESS = "0xc5213085d3fc19b6a883a92a5703f7733360f063"
_DEFAULT_CONTRACT = "0x0000000000000000000000000000456e65726779"  # VTHO token


def generate_value(param: Parameter, test_values: Dict) -> Any:
    """Pick the best test value for *param*.

    Returns ``_SKIP`` when we have no confident value and the parameter
    is optional -- callers must check for it and omit those params.
    """
    name = param.name

    # 1. User-provided values (always trusted)
    user_vals = test_values.get("parameters", {}).get(name)
    if user_vals is not None:
        return user_vals[0] if isinstance(user_vals, list) else user_vals

    # 2. Spec example / default / enum
    if param.example is not None:
        return param.example
    if param.default is not None:
        return param.default
    if param.enum:
        return param.enum[0]

    # 3. Name-based heuristics
    nl = name.lower()

    # -- addresses (various naming conventions) --
    if "contract" in nl and "address" in nl:
        return _DEFAULT_CONTRACT
    if nl in ("tokenaddress", "token_address"):
        return _DEFAULT_CONTRACT
    if any(k in nl for k in (
        "address", "account", "wallet", "user", "owner",
        "sender", "recipient", "validator", "delegator",
        "endorser", "origin", "manager",
    )):
        return _DEFAULT_ADDRESS

    # -- block / pagination / sorting --
    if "block" in nl and ("number" in nl or "num" in nl):
        return 22343000
    if nl == "blocknumber":
        return 22343000
    if nl == "page":
        return 0
    if nl in ("size", "limit", "pagesize", "page_size"):
        return 20
    if nl in ("offset", "skip"):
        return 0
    if nl in ("direction", "sort", "order", "sortdirection"):
        return "DESC"

    # -- identifiers --
    if nl in ("tokenid", "token_id", "nftid"):
        return "1"
    if nl in ("roundid", "round_id"):
        return 1
    if nl in ("appid", "app_id"):
        return _SKIP  # opaque hex -- skip unless user supplies it

    # -- events / types --
    if nl in ("txtype", "tx_type"):
        return "B3TR_ACTION"
    if nl in ("eventname", "event_name"):
        return _SKIP  # varies per endpoint -- skip unless user supplies
    if nl in ("eventtype", "event_type"):
        return _SKIP  # enum differs per endpoint
    if nl in ("searchby", "search_by"):
        return "to"

    # -- dates / timestamps --
    if nl == "date":
        return _SKIP
    if nl in ("startdate", "start_date", "enddate", "end_date"):
        return "2024-01-01"
    if nl in ("after", "from", "start", "starttime", "start_time", "starttimestamp"):
        return 1704067200
    if nl in ("before", "to", "end", "endtime", "end_time", "endtimestamp"):
        return 1735689600
    if nl in ("timeframe", "time_frame"):
        return "DAY"

    # -- proposals (too opaque to guess) --
    if "proposalid" in nl or "proposal_id" in nl:
        return _SKIP

    # -- cursors / opaque pagination --
    if "cursor" in nl:
        return _SKIP

    # -- array params with no enum --
    if param.schema_type == "array":
        return _SKIP

    # -- hash / tx id --
    if "hash" in nl or "txid" in nl:
        return _SKIP
    if nl == "id":
        return _SKIP

    # -- interval / period --
    if "interval" in nl or nl == "range":
        return "1-day"
    if nl == "period":
        return "HOUR"

    # -- booleans --
    if param.schema_type == "boolean":
        return True

    # 4. Type / format fallback
    if param.schema_format == "date":
        return "2024-01-01"
    if param.schema_format == "date-time":
        return "2024-01-01T00:00:00Z"
    if param.schema_format in ("int64", "int32"):
        return 1
    if param.schema_type == "integer":
        return 1
    if param.schema_type == "number":
        return 1.0

    # 5. Unknown -- skip optional, placeholder for required
    if param.required:
        return "UNKNOWN"
    return _SKIP


def generate_from_schema(schema: Dict, test_values: Dict, spec: Dict) -> Any:
    """Recursively build a value from a JSON-Schema."""
    if "$ref" in schema:
        schema = resolve_ref(schema["$ref"], spec)
    if "example" in schema:
        return schema["example"]

    st = schema.get("type", "object")
    if st == "object":
        obj: Dict[str, Any] = {}
        for pname, pschema in schema.get("properties", {}).items():
            if "$ref" in pschema:
                pschema = resolve_ref(pschema["$ref"], spec)
            uv = test_values.get("parameters", {}).get(pname)
            if uv is not None:
                obj[pname] = uv[0] if isinstance(uv, list) else uv
            elif "example" in pschema:
                obj[pname] = pschema["example"]
            elif "default" in pschema:
                obj[pname] = pschema["default"]
            else:
                obj[pname] = generate_from_schema(pschema, test_values, spec)
        return obj
    if st == "array":
        return [generate_from_schema(schema.get("items", {}), test_values, spec)]
    if st == "string":
        return schema["enum"][0] if "enum" in schema else "test"
    if st == "integer":
        return 1
    if st == "number":
        return 1.0
    if st == "boolean":
        return True
    return None


def generate_body(
    rb: Optional[RequestBody], test_values: Dict, spec: Dict
) -> Optional[Any]:
    if rb is None:
        return None
    if rb.example:
        return rb.example
    if rb.schema:
        return generate_from_schema(rb.schema, test_values, spec)
    return None


# ---------------------------------------------------------------------------
# Test-case generation
# ---------------------------------------------------------------------------

def generate_test_cases(
    op: Operation, test_values: Dict, spec: Dict
) -> List[TestCase]:
    path_overrides = test_values.get("path_overrides", {}).get(op.path, {})

    path_params: Dict[str, Any] = {}
    query_params: Dict[str, Any] = {}
    headers: Dict[str, str] = {}

    for param in op.parameters:
        if param.name in path_overrides:
            v = path_overrides[param.name]
            value = v[0] if isinstance(v, list) else v
        else:
            value = generate_value(param, test_values)

        if value is _SKIP:
            if param.required:
                print(
                    f"  Warning: Skipping required param '{param.name}' on "
                    f"{op.method} {op.path} (no valid value available)",
                    file=sys.stderr,
                )
            continue

        if param.location == "path":
            path_params[param.name] = value
        elif param.location == "query":
            query_params[param.name] = value
        elif param.location == "header":
            headers[param.name] = str(value)

    # Fill any path placeholders that weren't covered by declared parameters
    for placeholder in re.findall(r"\{(\w+)\}", op.path):
        if placeholder not in path_params:
            uv = test_values.get("parameters", {}).get(placeholder)
            if uv is not None:
                path_params[placeholder] = uv[0] if isinstance(uv, list) else uv
            else:
                path_params[placeholder] = "unknown"

    body = generate_body(op.request_body, test_values, spec)

    label = f"{op.method} {op.path}"
    if op.summary:
        label += f" ({op.summary})"

    base_case = TestCase(
        operation=op,
        path_params=path_params,
        query_params=query_params,
        headers=headers,
        body=body,
        label=label,
    )
    cases = [base_case]

    # Extra cases for parameters with multiple user-supplied values
    multi: Dict[str, Tuple[str, List[Any]]] = {}
    for param in op.parameters:
        name = param.name
        source = path_overrides.get(name) or test_values.get("parameters", {}).get(name)
        if isinstance(source, list) and len(source) > 1:
            multi[name] = (param.location, source[1:])

    for pname, (loc, extras) in multi.items():
        for val in extras:
            extra = TestCase(
                operation=op,
                path_params=dict(path_params),
                query_params=dict(query_params),
                headers=dict(headers),
                body=body,
                label=f"{label} [{pname}={val}]",
            )
            if loc == "path":
                extra.path_params[pname] = val
            elif loc == "query":
                extra.query_params[pname] = val
            elif loc == "header":
                extra.headers[pname] = str(val)
            cases.append(extra)

    return cases


# ---------------------------------------------------------------------------
# Execution helpers
# ---------------------------------------------------------------------------

def fetch_json_with_body(
    url: str,
    method: str,
    headers: Dict[str, str],
    body: Any,
    timeout: int,
    context: ssl.SSLContext | None,
) -> Any:
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers = {**headers, "Content-Type": "application/json"}
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        if url.lower().startswith("https"):
            opener = urllib.request.build_opener(
                urllib.request.HTTPSHandler(context=context)
            )
            with opener.open(req, timeout=timeout) as resp:
                raw = resp.read()
        else:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read()
        text = raw.decode("utf-8")
        return json.loads(text) if text.strip() else None
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"HTTP error {method} {url}: {e.code} {e.reason}") from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"Network error {method} {url}: {e.reason}") from e


def execute_test_case(
    tc: TestCase,
    endpoints: List[Tuple[str, str]],
    common_headers: Dict[str, str],
    timeout: int,
    insecure: bool,
    cafile: Optional[str],
    ignored_paths: Set[str],
    unordered_lists: bool,
) -> ComparisonResult:
    ctx = ssl_context_for(insecure, cafile)
    responses: Dict[str, Any] = {}
    status_codes: Dict[str, int] = {}
    errors: Dict[str, str] = {}

    merged_headers = {**common_headers, **tc.headers}
    merged_headers.setdefault("User-Agent", "compare-from-spec/1.0")

    for name, base_url in endpoints:
        full_url = base_url.rstrip("/") + tc.full_path
        try:
            if tc.operation.method == "GET":
                resp = fetch_json(full_url, merged_headers, timeout, ctx)
            else:
                resp = fetch_json_with_body(
                    full_url, tc.operation.method, merged_headers, tc.body, timeout, ctx
                )
            responses[name] = resp
            status_codes[name] = 200
        except RuntimeError as e:
            msg = str(e)
            m = re.search(r"(\d{3})", msg)
            if m:
                status_codes[name] = int(m.group(1))
            errors[name] = msg
        except Exception as e:
            errors[name] = str(e)

    diffs: Dict[str, List[Tuple[str, str]]] = {}
    ep_names = list(responses.keys())
    for i in range(len(ep_names)):
        for j in range(i + 1, len(ep_names)):
            n1, n2 = ep_names[i], ep_names[j]
            key = f"{n1} vs {n2}"
            diffs[key] = compare_json(
                responses[n1],
                responses[n2],
                ignored_paths=ignored_paths,
                unordered_lists=unordered_lists,
            )

    return ComparisonResult(
        test_case=tc,
        responses=responses,
        status_codes=status_codes,
        diffs=diffs,
        errors=errors,
    )


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

def print_summary(results: List[ComparisonResult]) -> None:
    total = len(results)
    passed = sum(1 for r in results if r.all_match)
    deprecated = sum(1 for r in results if r.ignored_due_to_deprecation)
    failed = sum(1 for r in results if r.has_mismatch and not r.ignored_due_to_deprecation)

    print(f"\n{'=' * 70}")
    print("COMPARISON SUMMARY")
    print(f"{'=' * 70}")
    print(f"  Total test cases : {total}")
    print(f"  Matching         : {passed}")
    print(f"  Deprecated       : {deprecated}")
    print(f"  Differing        : {failed}")
    print(f"{'=' * 70}\n")

    for result in results:
        if result.all_match:
            icon = "PASS"
        elif result.ignored_due_to_deprecation:
            icon = "DEPRECATED"
        else:
            icon = "FAIL"
        print(f"[{icon}] {result.test_case.label}")
        print(f"   Request: {result.test_case.operation.method} {result.test_case.full_path}")

        for ep, err in result.errors.items():
            print(f"   Warning: Error from {ep}: {err}")

        if not result.all_match:
            for pair, diffs in result.diffs.items():
                if diffs:
                    print(f"   {pair}: {len(diffs)} difference(s)")
                    for p, msg in diffs[:5]:
                        print(f"      - {p}: {msg}")
                    if len(diffs) > 5:
                        print(f"      ... and {len(diffs) - 5} more")
        print()


def save_report(results: List[ComparisonResult], output_file: str) -> None:
    report: Dict[str, Any] = {
        "timestamp": datetime.now().isoformat(),
        "summary": {
            "total": len(results),
            "passed": sum(1 for r in results if r.all_match),
            "deprecated": sum(1 for r in results if r.ignored_due_to_deprecation),
            "failed": sum(1 for r in results if r.has_mismatch and not r.ignored_due_to_deprecation),
        },
        "results": [],
    }
    for r in results:
        if r.all_match:
            status = "pass"
        elif r.ignored_due_to_deprecation:
            status = "deprecated"
        else:
            status = "fail"
        report["results"].append(
            {
                "label": r.test_case.label,
                "method": r.test_case.operation.method,
                "path": r.test_case.full_path,
                "status": status,
                "deprecated": r.test_case.operation.deprecated,
                "errors": r.errors,
                "status_codes": r.status_codes,
                "diffs": {k: list(v) for k, v in r.diffs.items()},
            }
        )
    with open(output_file, "w") as f:
        json.dump(report, f, indent=2)
    print(f"Detailed report saved to {output_file}")


# ---------------------------------------------------------------------------
# Config loaders
# ---------------------------------------------------------------------------

def load_endpoints(config_file: str) -> List[Tuple[str, str]]:
    with open(config_file, "r") as f:
        config = json.load(f)
    if isinstance(config, dict) and "endpoints" in config:
        return [(n, u) for n, u in config["endpoints"].items()]
    raise ValueError(f"Invalid config file format: {config_file}")


def load_test_values(path: Optional[str]) -> Dict:
    if not path:
        return {}
    try:
        with open(path, "r") as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"Warning: test values file not found: {path}", file=sys.stderr)
        return {}


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compare API responses across endpoints using their OpenAPI spec.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    parser.add_argument(
        "--config-file",
        default="endpoints.json",
        help="Endpoint configuration file (default: endpoints.json)",
    )
    parser.add_argument("--spec-url", help="URL to OpenAPI spec (auto-detected if omitted)")
    parser.add_argument("--spec-file", help="Local OpenAPI spec file (JSON or YAML)")
    parser.add_argument("--test-values", help="JSON file with test parameter values")

    filt = parser.add_argument_group("filtering")
    filt.add_argument("--path-filter", help="Regex to filter API paths (e.g. '/api/v1/stargate.*')")
    filt.add_argument("--method-filter", help="HTTP method filter (e.g. GET)")
    filt.add_argument("--tag-filter", help="OpenAPI tag filter")

    comp = parser.add_argument_group("comparison")
    comp.add_argument("--ignore-path", action="append", default=[], help="JSON path to ignore (repeatable)")
    comp.add_argument("--unordered-lists", action="store_true", help="Treat primitive lists as unordered")
    comp.add_argument("--timeout", type=int, default=30, help="Request timeout in seconds (default: 30)")
    comp.add_argument("--headers", default="", help="JSON object of extra headers for all requests")

    tls = parser.add_argument_group("TLS")
    tls.add_argument("--insecure", action="store_true", help="Skip TLS certificate verification")
    tls.add_argument("--cafile", default=None, help="Path to CA bundle file")

    out = parser.add_argument_group("output")
    out.add_argument("--output", help="Save detailed JSON report to this file")
    out.add_argument("--dry-run", action="store_true", help="Show generated test cases without executing")

    args = parser.parse_args()

    # ---- Load endpoints ----
    try:
        endpoints = load_endpoints(args.config_file)
    except Exception as e:
        print(f"Error loading endpoints: {e}", file=sys.stderr)
        sys.exit(2)

    if len(endpoints) < 2:
        print("Error: Need at least 2 endpoints to compare", file=sys.stderr)
        sys.exit(2)

    print(f"Loaded {len(endpoints)} endpoints:")
    for name, url in endpoints:
        print(f"   - {name}: {url}")

    # ---- Resolve spec ----
    spec_url = args.spec_url
    spec_data = None

    if args.spec_file:
        print(f"\nLoading spec from file: {args.spec_file}")
        try:
            with open(args.spec_file, "r") as f:
                content = f.read()
            try:
                spec_data = json.loads(content)
            except json.JSONDecodeError:
                try:
                    import yaml  # type: ignore[import-untyped]

                    spec_data = yaml.safe_load(content)
                except ImportError:
                    print(
                        "Error: YAML specs require PyYAML. Install: pip install pyyaml",
                        file=sys.stderr,
                    )
                    sys.exit(2)
        except FileNotFoundError:
            print(f"Error: Spec file not found: {args.spec_file}", file=sys.stderr)
            sys.exit(2)
    elif not spec_url:
        ref_name, ref_url = endpoints[0]
        print(f"\nAuto-detecting OpenAPI spec from {ref_name} ({ref_url})...")
        spec_url = detect_spec_url(ref_url, args.insecure)
        if not spec_url:
            print(
                "Error: Could not auto-detect spec URL. Provide --spec-url or --spec-file.",
                file=sys.stderr,
            )
            sys.exit(2)
        print(f"   Found: {spec_url}")

    if spec_data is None:
        assert spec_url is not None
        print(f"\nFetching OpenAPI spec from {spec_url}")
        try:
            spec_data = fetch_spec(spec_url, args.timeout, args.insecure)
        except Exception as e:
            print(f"Error: {e}", file=sys.stderr)
            sys.exit(2)

    version = spec_data.get("openapi", spec_data.get("swagger", "unknown"))
    title = spec_data.get("info", {}).get("title", "Unknown API")
    print(f"   API: {title} (spec version {version})")

    # ---- Spec-driven mode ----
    test_values = load_test_values(args.test_values)
    ignored = normalize_ignored_paths(args.ignore_path)

    common_headers: Dict[str, str] = {}
    if args.headers:
        try:
            common_headers = json.loads(args.headers)
        except json.JSONDecodeError as e:
            print(f"Error parsing --headers: {e}", file=sys.stderr)
            sys.exit(2)

    operations = parse_operations(spec_data)
    print(f"\nFound {len(operations)} operations in spec")

    if args.path_filter:
        operations = [o for o in operations if re.search(args.path_filter, o.path)]
    if args.method_filter:
        operations = [o for o in operations if o.method == args.method_filter.upper()]
    if args.tag_filter:
        operations = [o for o in operations if args.tag_filter in o.tags]

    if not operations:
        print("No operations matched the given filters.", file=sys.stderr)
        sys.exit(2)

    print(f"   Testing {len(operations)} operations (after filters)\n")

    all_cases: List[TestCase] = []
    for op in operations:
        all_cases.extend(generate_test_cases(op, test_values, spec_data))

    print(f"Generated {len(all_cases)} test case(s)\n")

    if args.dry_run:
        print("DRY RUN -- test cases that would be executed:\n")
        for idx, tc in enumerate(all_cases, 1):
            print(f"  {idx}. {tc.label}")
            print(f"     {tc.operation.method} {tc.full_path}")
            if tc.body:
                print(f"     Body: {json.dumps(tc.body)[:120]}")
            print()
        sys.exit(0)

    results: List[ComparisonResult] = []
    for idx, tc in enumerate(all_cases, 1):
        print(f"[{idx}/{len(all_cases)}] {tc.operation.method} {tc.full_path}")
        result = execute_test_case(
            tc,
            endpoints=endpoints,
            common_headers=common_headers,
            timeout=args.timeout,
            insecure=args.insecure,
            cafile=args.cafile,
            ignored_paths=ignored,
            unordered_lists=args.unordered_lists,
        )
        results.append(result)
        status = "All endpoints match" if result.all_match else "Differences found"
        print(f"   {status}")

    print_summary(results)

    if args.output:
        save_report(results, args.output)

    sys.exit(0 if all(r.effective_pass for r in results) else 1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nInterrupted.", file=sys.stderr)
        sys.exit(2)
