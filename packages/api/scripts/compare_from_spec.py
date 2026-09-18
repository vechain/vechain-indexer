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

Cases per operation:
  base     required params, plus optional params that path_overrides[path] configures
           or that carry a spec default. parameters[name] only supplies values.
  variant  base plus one remaining optional param, so each filter is exercised alone;
           up to VARIANT_VALUE_LIMIT values of it, which is where deep pages and the
           maximum page size come from
  extra    base with the next value of any list the base case drew from

A case whose baseline response is empty or an error is "vacuous": it compares nothing.
An operation with only vacuous cases fails the run unless --allow-vacuous-operations.

Exit codes:
  0 -> All responses match across endpoints
  1 -> Some responses differ, or an operation compared no data
  2 -> Runtime or input error
"""

import argparse
import itertools
import json
import os
import re
import time
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable, Dict, List, Optional, Set, Tuple

# Ensure sibling modules can be imported regardless of cwd
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from compare_endpoints import (
    HttpResponseError,
    compare_json,
    fetch_json_with_headers,
    ignored_paths_for_matching_error_statuses,
    is_primitive,
    normalize_ignored_paths,
    ssl_context_for,
)

Path = str

SUITES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "test_suites.json")


def load_suites() -> Dict[str, str]:
    try:
        with open(SUITES_FILE) as f:
            return json.load(f)
    except FileNotFoundError:
        return {}


def resolve_path_filter(value: str, suites: Dict[str, str]) -> str:
    """A suite name stands for that suite's paths; anything else is taken as a regex."""
    return suites.get(value, value)


# CloudFront keys on the whole query string, so an unknown parameter forces a miss.
CACHE_BUST_PARAM = "_rt"
_RUN_ID = f"{os.getpid()}-{int(datetime.now().timestamp())}"
_REQUEST_SEQUENCE = itertools.count(1)


def fresh_nonce(_attempt: int) -> str:
    """Unique per request, since two cases can resolve to the same URL."""
    return f"{_RUN_ID}-{next(_REQUEST_SEQUENCE)}"


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
    # Endpoint-specific JSON paths to ignore during comparison. Supports the
    # same [*] wildcard as global --ignore-path. Populated from
    # ``path_overrides[path].ignore_paths`` in test_values.json.
    extra_ignore_paths: List[str] = field(default_factory=list)
    # Unordered list path -> its element's identifying field, from path_overrides.
    unordered_by: Dict[str, str] = field(default_factory=dict)
    expected_failure: Optional[str] = None

    @property
    def resolved_path(self) -> str:
        path = self.operation.path
        for name, value in self.path_params.items():
            path = path.replace(f"{{{name}}}", str(value))
        return path

    @property
    def full_path(self) -> str:
        return self.path_with(None)

    def path_with(self, nonce: Optional[str]) -> str:
        """The request path; *nonce* adds a parameter the API ignores and a CDN keys on."""
        path = self.resolved_path
        query = dict(self.query_params)
        if nonce is not None:
            query[CACHE_BUST_PARAM] = nonce
        if query:
            path = f"{path}?{urllib.parse.urlencode(query, doseq=True)}"
        return path


@dataclass
class ComparisonResult:
    test_case: TestCase
    responses: Dict[str, Any]
    status_codes: Dict[str, int]
    diffs: Dict[str, List[Tuple[str, str]]]
    errors: Dict[str, str]
    tolerated_diffs: Dict[str, List[Tuple[str, str]]] = field(default_factory=dict)
    baseline_shape: str = "data"
    attempts: int = 1
    cache_headers: Dict[str, Dict[str, str]] = field(default_factory=dict)
    expected_failure: Optional[str] = None

    @property
    def error_match(self) -> bool:
        """Both sides failed the same way, so the case compared two failures."""
        codes = set(self.status_codes.values())
        return bool(self.errors) or (len(codes) == 1 and next(iter(codes)) >= 400)

    @property
    def transient_error_match(self) -> bool:
        return self.error_match and (
            bool(self.errors) or not TRANSIENT_STATUSES.isdisjoint(self.status_codes.values())
        )

    @property
    def cache_control(self) -> Dict[str, str]:
        return {name: headers.get("cache-control", "") for name, headers in self.cache_headers.items()}

    @property
    def vacuous(self) -> bool:
        return self.baseline_shape in ("empty", "error") and not self.has_mismatch

    @property
    def all_match(self) -> bool:
        return (
            not self.errors
            and all(len(d) == 0 for d in self.diffs.values())
            and all(len(d) == 0 for d in self.tolerated_diffs.values())
        )

    @property
    def has_mismatch(self) -> bool:
        # Tolerated diffs do not count as a mismatch for pass/fail purposes.
        return bool(self.errors) or any(len(d) > 0 for d in self.diffs.values())

    @property
    def has_tolerated(self) -> bool:
        return any(len(d) > 0 for d in self.tolerated_diffs.values())

    @property
    def tolerated_only(self) -> bool:
        return not self.has_mismatch and self.has_tolerated

    @property
    def ignored_due_to_deprecation(self) -> bool:
        return bool(self.expected_failure) and self.has_mismatch

    @property
    def effective_pass(self) -> bool:
        if self.transient_error_match:
            return False
        return not self.has_mismatch or self.ignored_due_to_deprecation


def response_shape(body: Any, status_code: int) -> str:
    """'error', 'empty', 'scalar' or 'data': what a comparison against *body* can prove."""
    if status_code >= 400:
        return "error"
    if body is None or body == [] or body == {}:
        return "empty"
    if isinstance(body, dict):
        for key in ("data", "content"):
            if key in body and body[key] in ([], None):
                return "empty"
    if is_primitive(body):
        return "scalar"
    return "data"


def normalize_response_for_test_case(tc: TestCase, response: Any) -> Any:
    if response is None:
        return None

    if (
        tc.operation.method == "GET"
        and tc.operation.path == "/api/v1/b3tr/galaxy-members/level-overview"
        and isinstance(response, list)
    ):
        # The API sorts by totalNFTs only, so equal totals can appear in different order.
        # Canonicalize ties for regression comparison without changing endpoint behavior.
        return sorted(
            response,
            key=lambda item: (
                -int(item.get("totalNFTs", 0)),
                str(item.get("level", "")),
            )
            if isinstance(item, dict)
            else (0, str(item)),
        )

    return response


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


def merge_operations(
    baseline: List[Operation], candidate: List[Operation]
) -> Tuple[List[Operation], List[str]]:
    """Every operation either side declares, plus the ones only one side has."""
    by_key = {(op.method, op.path): op for op in baseline}
    only_candidate = [
        f"{op.method} {op.path}" for op in candidate if (op.method, op.path) not in by_key
    ]
    only_baseline = [
        f"{op.method} {op.path}"
        for op in baseline
        if (op.method, op.path) not in {(o.method, o.path) for o in candidate}
    ]
    for op in candidate:
        by_key.setdefault((op.method, op.path), op)
    return [by_key[key] for key in sorted(by_key)], sorted(only_baseline + only_candidate)


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
_PLACEHOLDER_EXAMPLE_PARAMS = frozenset(
    {"starttimestamp", "endtimestamp", "after", "before", "from", "to", "addresses",
     "excludecollections"}
)

# How many configured values one optional filter contributes, so a long list cannot explode the run.
VARIANT_VALUE_LIMIT = 2

# path_overrides keys that configure the comparison rather than name a parameter.
RESERVED_OVERRIDE_KEYS = frozenset({"ignore_paths", "expect_fail", "unordered_by"})


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

    nl = name.lower()

    # 2. Spec default / example / enum; window and address-list examples are placeholders.
    if param.default is not None:
        return param.default
    if param.example is not None and nl not in _PLACEHOLDER_EXAMPLE_PARAMS:
        return param.example
    if param.enum:
        return param.enum[0]

    # 3. Name-based heuristics

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
        return 1789000000
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

def _normalize_ignore_paths(raw: Any) -> List[str]:
    """Coerce a ``ignore_paths`` config value into a list of pattern strings.

    JSON authors may reasonably write a single string instead of a list. Without
    this guard, ``list("root.data")`` would silently split it into characters
    and produce nonsense ignore patterns. Non-strings and empty entries are
    dropped so a stray null or 0 can't slip through.
    """
    if raw is None:
        return []
    if isinstance(raw, str):
        return [raw]
    if not isinstance(raw, list):
        return []
    return [item for item in raw if isinstance(item, str) and item]


def _configured_values(
    name: str, path_overrides: Dict, test_values: Dict
) -> Tuple[bool, List[Any]]:
    """(overridden, values); an explicit null override drops the parameter."""
    if name in path_overrides:
        raw = path_overrides[name]
        if raw is None:
            return True, []
        return True, list(raw) if isinstance(raw, list) else [raw]
    raw = test_values.get("parameters", {}).get(name)
    if raw is None:
        return False, []
    return False, list(raw) if isinstance(raw, list) else [raw]


def _with_param(tc: TestCase, param: Parameter, value: Any, label: str) -> TestCase:
    extra = TestCase(
        operation=tc.operation,
        path_params=dict(tc.path_params),
        query_params=dict(tc.query_params),
        headers=dict(tc.headers),
        body=tc.body,
        label=label,
        extra_ignore_paths=list(tc.extra_ignore_paths),
        unordered_by=dict(tc.unordered_by),
        expected_failure=tc.expected_failure,
    )
    if param.location == "path":
        extra.path_params[param.name] = value
    elif param.location == "query":
        extra.query_params[param.name] = value
    elif param.location == "header":
        extra.headers[param.name] = str(value)
    return extra


def generate_test_cases(
    op: Operation, test_values: Dict, spec: Dict
) -> List[TestCase]:
    path_overrides = test_values.get("path_overrides", {}).get(op.path, {})
    extra_ignore_paths = _normalize_ignore_paths(path_overrides.get("ignore_paths"))

    base = TestCase(
        operation=op,
        body=generate_body(op.request_body, test_values, spec),
        label=f"{op.method} {op.path}" + (f" ({op.summary})" if op.summary else ""),
        extra_ignore_paths=list(extra_ignore_paths),
        unordered_by=dict(path_overrides.get("unordered_by") or {}),
        expected_failure=path_overrides.get("expect_fail"),
    )
    extra_values: List[Tuple[Parameter, List[Any]]] = []
    variants: List[Parameter] = []

    for param in op.parameters:
        if param.name in RESERVED_OVERRIDE_KEYS:
            continue
        overridden, values = _configured_values(param.name, path_overrides, test_values)
        if overridden and not values:
            continue
        if overridden or param.required:
            value = values[0] if values else generate_value(param, test_values)
        elif param.default is not None:
            value = param.default
        else:
            variants.append(param)
            continue

        if value is _SKIP:
            print(
                f"  Warning: Skipping required param '{param.name}' on "
                f"{op.method} {op.path} (no valid value available)",
                file=sys.stderr,
            )
            continue
        base = _with_param(base, param, value, base.label)
        if len(values) > 1:
            extra_values.append((param, values[1:]))

    # Fill any path placeholders that weren't covered by declared parameters
    for placeholder in re.findall(r"\{(\w+)\}", op.path):
        if placeholder not in base.path_params:
            uv = test_values.get("parameters", {}).get(placeholder)
            if uv is not None:
                base.path_params[placeholder] = uv[0] if isinstance(uv, list) else uv
            else:
                base.path_params[placeholder] = "unknown"

    cases = [base]
    for param, values in extra_values:
        for val in values:
            cases.append(_with_param(base, param, val, f"{base.label} [{param.name}={val}]"))
    for param in variants:
        _, configured = _configured_values(param.name, path_overrides, test_values)
        for value in configured[:VARIANT_VALUE_LIMIT] or [generate_value(param, test_values)]:
            if value is _SKIP:
                continue
            cases.append(_with_param(base, param, value, f"{base.label} [+{param.name}={value}]"))
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
        payload = e.read()
        body = None
        if payload:
            try:
                text = payload.decode("utf-8")
            except UnicodeDecodeError:
                text = payload.decode("latin-1")
            if text.strip():
                try:
                    body = json.loads(text)
                except json.JSONDecodeError:
                    body = None
        raise HttpResponseError(
            status_code=e.code,
            reason=e.reason,
            body=body,
            url=url,
        ) from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"Network error {method} {url}: {e.reason}") from e


@dataclass
class Fetched:
    body: Any = None
    status_code: int = 0
    error: Optional[str] = None
    headers: Dict[str, str] = field(default_factory=dict)


# Keyset pagination: /blocks resumes through `from`, everything else through `cursor`.
CURSOR_PARAM_NAMES = ("cursor", "from")


def cursor_param(op: Operation) -> Optional[Parameter]:
    for param in op.parameters:
        if param.location == "query" and param.name in CURSOR_PARAM_NAMES:
            return param
    return None


def next_cursor(body: Any) -> Optional[str]:
    if not isinstance(body, dict):
        return None
    pagination = body.get("pagination")
    if not isinstance(pagination, dict) or not pagination.get("hasNext"):
        return None
    cursor = pagination.get("cursor")
    return None if cursor is None else str(cursor)


def cursor_follow_up(
    result: "ComparisonResult", baseline_name: str, page: int
) -> Optional[TestCase]:
    """The next page, resumed on both colours from the baseline's own cursor.

    Feeding the baseline's cursor to the candidate is what a client holding one across
    the cutover does, so a cursor the candidate cannot resume is a finding here.
    """
    param = cursor_param(result.test_case.operation)
    if param is None:
        return None
    cursor = next_cursor(result.responses.get(baseline_name))
    if cursor is None:
        return None
    stem = re.sub(r" \[page \d+ from the baseline cursor\]$", "", result.test_case.label)
    follow_up = _with_param(
        result.test_case, param, cursor, f"{stem} [page {page} from the baseline cursor]"
    )
    follow_up.expected_failure = result.test_case.expected_failure
    return follow_up


# Statuses that say "ask again", not "the two colours disagree".
TRANSIENT_STATUSES = frozenset({408, 425, 429, 500, 502, 503, 504})


def is_transient(fetched: "Fetched") -> bool:
    return fetched.error is not None or fetched.status_code in TRANSIENT_STATUSES


def fetch_case(
    tc: TestCase,
    base_url: str,
    headers: Dict[str, str],
    timeout: int,
    ctx: ssl.SSLContext | None,
    nonce: Optional[str],
) -> Fetched:
    url = base_url.rstrip("/") + tc.path_with(nonce)
    try:
        if tc.operation.method == "GET":
            body, response_headers = fetch_json_with_headers(url, headers, timeout, ctx)
        else:
            body = fetch_json_with_body(
                url, tc.operation.method, headers, tc.body, timeout, ctx
            )
            response_headers = {}
        return Fetched(
            body=normalize_response_for_test_case(tc, body),
            status_code=200,
            headers=response_headers,
        )
    except HttpResponseError as e:
        return Fetched(
            body=normalize_response_for_test_case(tc, e.body),
            status_code=e.status_code,
            headers=e.headers,
        )
    except RuntimeError as e:
        msg = str(e)
        m = re.search(r"(\d{3})", msg)
        return Fetched(status_code=int(m.group(1)) if m else 0, error=msg)
    except Exception as e:
        return Fetched(error=str(e))


def _pairwise_diffs(
    responses: Dict[str, Any],
    status_codes: Dict[str, int],
    tc: TestCase,
    ignored_paths: Set[str],
    unordered_lists: bool,
    num_abs_tolerance: float,
    num_rel_tolerance: float,
) -> Tuple[Dict[str, List[Tuple[str, str]]], Dict[str, List[Tuple[str, str]]]]:
    diffs: Dict[str, List[Tuple[str, str]]] = {}
    tolerated: Dict[str, List[Tuple[str, str]]] = {}
    ep_names = list(responses.keys())
    for i in range(len(ep_names)):
        for j in range(i + 1, len(ep_names)):
            n1, n2 = ep_names[i], ep_names[j]
            key = f"{n1} vs {n2}"
            pair_diffs: List[Tuple[str, str]] = []
            pair_tolerated: List[Tuple[str, str]] = []
            if status_codes.get(n1) != status_codes.get(n2):
                pair_diffs.append(
                    ("status", f"status code mismatch: {status_codes.get(n1)} != {status_codes.get(n2)}")
                )
            combined_ignored = set(ignored_paths) | set(tc.extra_ignore_paths)
            effective_ignored_paths = ignored_paths_for_matching_error_statuses(
                status_codes.get(n1, 0),
                status_codes.get(n2, 0),
                combined_ignored,
            )
            pair_diffs.extend(
                compare_json(
                    responses[n1],
                    responses[n2],
                    tolerated_diffs=pair_tolerated,
                    ignored_paths=effective_ignored_paths,
                    unordered_lists=unordered_lists,
                    unordered_by=tc.unordered_by,
                    num_abs_tolerance=num_abs_tolerance,
                    num_rel_tolerance=num_rel_tolerance,
                )
            )
            diffs[key] = pair_diffs
            tolerated[key] = pair_tolerated
    return diffs, tolerated


def execute_test_case(
    tc: TestCase,
    endpoints: List[Tuple[str, str]],
    common_headers: Dict[str, str],
    timeout: int,
    insecure: bool,
    cafile: Optional[str],
    ignored_paths: Set[str],
    unordered_lists: bool,
    num_abs_tolerance: float = 0.0,
    num_rel_tolerance: float = 0.0,
    attempts: int = 1,
    cache_bust: bool = False,
    nonce_for: Callable[[int], str] = fresh_nonce,
    backoff_seconds: float = 0.0,
) -> ComparisonResult:
    """Compare one case, re-fetching while the baseline is still moving.

    The baseline is read before and after the candidate. A baseline that changed
    between those two reads was mid-block, so the comparison is inconclusive and the
    attempt is spent rather than reported as a difference.
    """
    ctx = ssl_context_for(insecure, cafile)
    merged_headers = {**common_headers, **tc.headers}
    merged_headers.setdefault("User-Agent", "compare-from-spec/1.0")
    baseline_name = endpoints[0][0]
    result: Optional[ComparisonResult] = None

    for attempt in range(1, max(1, attempts) + 1):
        nonce = nonce_for(attempt) if cache_bust else None
        fetched = {name: fetch_case(tc, url, merged_headers, timeout, ctx, nonce)
                   for name, url in endpoints}
        recheck = fetch_case(tc, endpoints[0][1], merged_headers, timeout, ctx, nonce)

        responses = {name: f.body for name, f in fetched.items() if f.error is None}
        status_codes = {name: f.status_code for name, f in fetched.items() if f.status_code}
        errors = {name: f.error for name, f in fetched.items() if f.error is not None}

        diffs, tolerated = _pairwise_diffs(
            responses, status_codes, tc, ignored_paths, unordered_lists,
            num_abs_tolerance, num_rel_tolerance,
        )
        result = ComparisonResult(
            test_case=tc,
            responses=responses,
            status_codes=status_codes,
            diffs=diffs,
            errors=errors,
            tolerated_diffs=tolerated,
            baseline_shape=(
                "error" if baseline_name in errors
                else response_shape(responses.get(baseline_name), status_codes.get(baseline_name, 0))
            ),
            attempts=attempt,
            cache_headers={name: f.headers for name, f in fetched.items() if f.headers},
            expected_failure=tc.expected_failure,
        )
        if any(is_transient(f) for f in fetched.values()) and attempt < attempts:
            time.sleep(backoff_seconds * attempt)
            continue
        if recheck.error is not None or recheck.body != fetched[baseline_name].body:
            continue  # the baseline moved mid-comparison, so the diff proves nothing
        if not result.has_mismatch:
            return result
        # A settled baseline and a difference: the candidate may have been mid-block, so retry.

    assert result is not None
    return result


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------

def cache_hits(result: ComparisonResult) -> Dict[str, str]:
    """Endpoints that served this case from a CDN cache, by their x-cache header."""
    return {
        name: headers["x-cache"]
        for name, headers in result.cache_headers.items()
        if "hit" in headers.get("x-cache", "").lower()
    }


def status_of(result: ComparisonResult) -> str:
    if result.transient_error_match:
        return "unavailable"
    if result.has_mismatch:
        return "expected-fail" if result.ignored_due_to_deprecation else "fail"
    if result.error_match:
        return "error-match"
    if result.vacuous:
        return "vacuous"
    if result.has_tolerated:
        return "tolerated"
    return "pass"


STATUSES = ("pass", "vacuous", "error-match", "unavailable", "tolerated", "expected-fail", "fail")


def summarize(results: List[ComparisonResult]) -> Dict[str, Any]:
    counts = {status: 0 for status in STATUSES}
    with_data: Dict[str, bool] = {}
    for r in results:
        status = status_of(r)
        counts[status] += 1
        key = f"{r.test_case.operation.method} {r.test_case.operation.path}"
        covered = status in ("pass", "tolerated") or bool(r.expected_failure)
        with_data[key] = with_data.get(key, False) or covered
    return {
        "total": len(results),
        "passed": counts["pass"],
        "vacuous": counts["vacuous"],
        "error_match": counts["error-match"],
        "unavailable": counts["unavailable"],
        "tolerated": counts["tolerated"],
        "deprecated": counts["expected-fail"],
        "failed": counts["fail"],
        "retried": sum(1 for r in results if r.attempts > 1),
        "cache_hits": sum(1 for r in results if cache_hits(r)),
        "operations_without_data": sorted(k for k, ok in with_data.items() if not ok),
    }


def print_summary(results: List[ComparisonResult]) -> None:
    s = summarize(results)
    print(f"\n{'=' * 70}")
    print("COMPARISON SUMMARY")
    print(f"{'=' * 70}")
    print(f"  Total test cases : {s['total']}")
    print(f"  Matching         : {s['passed']}")
    print(f"  Vacuous          : {s['vacuous']}")
    print(f"  Matching errors  : {s['error_match']}")
    print(f"  Unavailable      : {s['unavailable']}")
    print(f"  Tolerated drift  : {s['tolerated']}")
    print(f"  Expected failures: {s['deprecated']}")
    print(f"  Differing        : {s['failed']}")
    print(f"  Ops without data : {len(s['operations_without_data'])}")
    print(f"  Retried          : {s['retried']}")
    print(f"  Served from cache: {s['cache_hits']}")
    print(f"{'=' * 70}\n")

    for result in results:
        icon = {"pass": "PASS", "vacuous": "VACUOUS", "error-match": "ERROR-MATCH",
                "unavailable": "UNAVAILABLE", "tolerated": "WARN",
                "expected-fail": "EXPECTED-FAIL", "fail": "FAIL"}[status_of(result)]
        print(f"[{icon}] {result.test_case.label}")
        print(f"   Request: {result.test_case.operation.method} {result.test_case.full_path}")

        for ep, err in result.errors.items():
            print(f"   Warning: Error from {ep}: {err}")

        if result.has_mismatch:
            for pair, diffs in result.diffs.items():
                if diffs:
                    print(f"   {pair}: {len(diffs)} difference(s)")
                    for p, msg in diffs[:5]:
                        print(f"      - {p}: {msg}")
                    if len(diffs) > 5:
                        print(f"      ... and {len(diffs) - 5} more")

        if result.has_tolerated:
            for pair, tdiffs in result.tolerated_diffs.items():
                if tdiffs:
                    print(f"   {pair}: {len(tdiffs)} tolerated numeric drift(s)")
                    for p, msg in tdiffs[:5]:
                        print(f"      ~ {p}: {msg}")
                    if len(tdiffs) > 5:
                        print(f"      ... and {len(tdiffs) - 5} more")
        print()

    if s.get("operations_on_one_side_only"):
        print("Operations declared by one endpoint only:")
        for op in s["operations_on_one_side_only"]:
            print(f"  - {op}")
        print()

    if s["operations_without_data"]:
        print("Operations whose every case was vacuous (empty or error on the baseline):")
        for op in s["operations_without_data"]:
            print(f"  - {op}")
        print()


def save_report(
    results: List[ComparisonResult], output_file: str, spec_only: Optional[List[str]] = None
) -> None:
    summary = summarize(results)
    summary["operations_on_one_side_only"] = list(spec_only or [])
    report: Dict[str, Any] = {
        "timestamp": datetime.now().isoformat(),
        "summary": summary,
        "results": [],
    }
    for r in results:
        report["results"].append(
            {
                "label": r.test_case.label,
                "method": r.test_case.operation.method,
                "operation": r.test_case.operation.path,
                "path": r.test_case.full_path,
                "status": status_of(r),
                "baseline_shape": r.baseline_shape,
                "attempts": r.attempts,
                "expected_failure": r.expected_failure,
                "cache_hits": cache_hits(r),
                "cache_control": r.cache_control,
                "deprecated": r.test_case.operation.deprecated,
                "errors": r.errors,
                "status_codes": r.status_codes,
                "diffs": {k: list(v) for k, v in r.diffs.items()},
                "tolerated_diffs": {k: list(v) for k, v in r.tolerated_diffs.items()},
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
    filt.add_argument(
        "--path-filter",
        help="A suite name from test_suites.json, or a regex over API paths",
    )
    filt.add_argument(
        "--list-suites", action="store_true", help="Print the named suites and exit"
    )
    filt.add_argument("--method-filter", help="HTTP method filter (e.g. GET)")
    filt.add_argument("--tag-filter", help="OpenAPI tag filter")

    comp = parser.add_argument_group("comparison")
    comp.add_argument("--ignore-path", action="append", default=[], help="JSON path to ignore (repeatable)")
    comp.add_argument("--unordered-lists", action="store_true", help="Treat primitive lists as unordered")
    comp.add_argument("--timeout", type=int, default=30, help="Request timeout in seconds (default: 30)")
    comp.add_argument("--headers", default="", help="JSON object of extra headers for all requests")
    comp.add_argument(
        "--num-abs-tolerance",
        type=float,
        default=0.0,
        help="Absolute numeric tolerance: leaf numeric mismatches with |a-b| within this bound are reported as tolerated drift (do not fail).",
    )
    comp.add_argument(
        "--num-rel-tolerance",
        type=float,
        default=0.0,
        help="Relative numeric tolerance (fraction of max(|a|,|b|)). Combined with --num-abs-tolerance via max().",
    )

    tls = parser.add_argument_group("TLS")
    tls.add_argument("--insecure", action="store_true", help="Skip TLS certificate verification")
    tls.add_argument("--cafile", default=None, help="Path to CA bundle file")

    comp.add_argument(
        "--cursor-pages",
        type=int,
        default=3,
        help="Follow this many further pages from the baseline's cursor on both sides (default: 3)",
    )
    comp.add_argument(
        "--candidate-spec-url",
        help="Also read operations from the candidate's spec, so an endpoint on one side only is a finding",
    )
    comp.add_argument(
        "--backoff-seconds",
        type=float,
        default=2.0,
        help="Base delay before re-fetching after an unreachable or rate-limited response (default: 2)",
    )
    comp.add_argument(
        "--attempts",
        type=int,
        default=3,
        help="Re-fetch a case up to this many times while the baseline is still moving (default: 3)",
    )
    comp.add_argument(
        "--no-cache-bust",
        action="store_true",
        help=f"Do not add the {CACHE_BUST_PARAM} parameter that forces a CDN miss on both sides",
    )
    comp.add_argument(
        "--allow-vacuous-operations",
        action="store_true",
        help="Do not fail when an operation's every case compared an empty or error baseline response",
    )

    out = parser.add_argument_group("output")
    out.add_argument("--output", help="Save detailed JSON report to this file")
    out.add_argument("--dry-run", action="store_true", help="Show generated test cases without executing")

    args = parser.parse_args()

    suites = load_suites()
    if args.list_suites:
        for name, pattern in sorted(suites.items()):
            print(f"{name:16s} {pattern}")
        sys.exit(0)

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
    spec_only: List[str] = []
    if args.candidate_spec_url:
        try:
            candidate_spec = fetch_spec(args.candidate_spec_url, args.timeout, args.insecure)
        except Exception as e:
            print(f"Error fetching the candidate spec: {e}", file=sys.stderr)
            sys.exit(2)
        operations, one_sided = merge_operations(operations, parse_operations(candidate_spec))
        declared = {
            path
            for path, config in test_values.get("path_overrides", {}).items()
            if config.get("expect_fail")
        }
        for entry in one_sided:
            print(f"  Warning: {entry} is declared by one endpoint only", file=sys.stderr)
        spec_only = [e for e in one_sided if e.split(" ", 1)[1] not in declared]
    print(f"\nFound {len(operations)} operations in spec")

    if args.path_filter:
        pattern = resolve_path_filter(args.path_filter, suites)
        operations = [o for o in operations if re.search(pattern, o.path)]
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
    baseline_name = endpoints[0][0]
    pending = list(all_cases)
    walked: Dict[str, int] = {}
    idx = 0
    while idx < len(pending):
        tc = pending[idx]
        idx += 1
        print(f"[{idx}/{len(pending)}] {tc.operation.method} {tc.full_path}")
        result = execute_test_case(
            tc,
            endpoints=endpoints,
            common_headers=common_headers,
            timeout=args.timeout,
            insecure=args.insecure,
            cafile=args.cafile,
            ignored_paths=ignored,
            unordered_lists=args.unordered_lists,
            num_abs_tolerance=args.num_abs_tolerance,
            num_rel_tolerance=args.num_rel_tolerance,
            attempts=args.attempts,
            cache_bust=not args.no_cache_bust,
            backoff_seconds=args.backoff_seconds,
        )
        results.append(result)
        page = walked.get(tc.label, 1) + 1
        if page <= args.cursor_pages + 1 and not result.has_mismatch:
            follow_up = cursor_follow_up(result, baseline_name, page)
            if follow_up is not None:
                walked[follow_up.label] = page
                pending.append(follow_up)
        print(
            "   "
            + {
                "pass": "All endpoints match",
                "vacuous": "Match, but the baseline returned nothing to compare",
                "error-match": "Both endpoints returned the same error",
                "unavailable": "An endpoint could not be reached",
                "tolerated": "Tolerated drift only",
                "expected-fail": "Differences found (declared expected)",
                "fail": "Differences found",
            }[status_of(result)]
        )

    print_summary(results)

    if args.output:
        save_report(results, args.output, spec_only)

    ok = all(r.effective_pass for r in results) and not spec_only
    if summarize(results)["operations_without_data"] and not args.allow_vacuous_operations:
        ok = False
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nInterrupted.", file=sys.stderr)
        sys.exit(2)
