# test_indexer_api.py
# pip install pytest requests python-dotenv

import json
import os
from urllib.parse import urljoin
import pytest
import requests

# ---------- Config ----------
BASE_URL = os.getenv("BASE_URL", "https://indexer.mainnet.vechain.org")
# Try this first; we'll auto-discover via swagger-config if it 404s.
OPENAPI_URL = os.getenv("OPENAPI_URL", urljoin(BASE_URL, "/api-docs"))

# Behavior switches
FAIL_ON_4XX = os.getenv("FAIL_ON_4XX", "0") == "1"   # default: only fail hard on 5xx
TIMEOUT_S   = float(os.getenv("TIMEOUT_S", "20"))

# Enable methods beyond GET by exporting TEST_POST=1, etc.
def should_skip_method(method: str) -> bool:
    return {
        "GET":  False,
        "HEAD": os.getenv("TEST_HEAD", "0") != "1",
        "OPTIONS": os.getenv("TEST_OPTIONS", "0") != "1",
        "POST": os.getenv("TEST_POST", "0") != "1",
        "PUT": os.getenv("TEST_PUT", "0") != "1",
        "PATCH": os.getenv("TEST_PATCH", "0") != "1",
        "DELETE": os.getenv("TEST_DELETE", "0") != "1",
    }.get(method.upper(), True)

# ---------- Helpers ----------
def _endpoint_id(val):
    # Handle tuple/list: (METHOD, /path, op)
    if isinstance(val, (list, tuple)) and len(val) >= 2:
        return f"{val[0]} {val[1]}"
    # Fallbacks for odd shapes
    try:
        m = val.get("method") if hasattr(val, "get") else None
        p = val.get("path") if hasattr(val, "get") else None
        if m and p:
            return f"{m} {p}"
    except Exception:
        pass
    return str(val)

def get_param_value(name: str, schema: dict | None = None):
    """
    Resolve a value for a parameter. Order of preference:
    1) Hard-coded known params (wallet, appId) with env override
    2) PARAM_<NAME> environment variable
    3) Schema hints (example/enum/type)
    4) Generic fallback
    """
    lname = name.lower()

    # ---- Hard-coded known params (with env overrides) ----
    if lname in ("wallet", "walletid", "address"):
        return os.getenv("PARAM_WALLET", "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")

    if lname in ("appid", "app_id", "applicationid"):
        return os.getenv("PARAM_APPID", "0db3dbaf9d5f337f5aabb3ef398d054d3d000062")

    # ---- Generic env override ----
    env_key = f"PARAM_{name}".upper()
    v = os.getenv(env_key)
    if v:
        return v

    # ---- Schema-driven defaults ----
    if schema:
        if "example" in schema:
            return schema["example"]
        if "enum" in schema and schema["enum"]:
            return schema["enum"][0]

        typ = (schema.get("type") or "").lower()
        fmt = (schema.get("format") or "").lower()

        if typ == "integer":
            return 1
        if typ == "number":
            return 1
        if typ == "boolean":
            # send a string for query/header; requests will serialize as needed
            return "true"
        if typ == "string":
            if fmt in ("uuid", "uri"):
                return "00000000-0000-0000-0000-000000000000"
            if fmt in ("date-time", "date"):
                return "1970-01-01T00:00:00Z"
            return "test"
        if typ == "array":
            return [get_param_value(name + "_item", schema.get("items", {}))]
        if typ == "object":
            return {}

    # ---- Last resort ----
    return "test"

def make_url(path: str, path_params: dict) -> str:
    url = path
    for key, val in path_params.items():
        url = url.replace("{" + key + "}", str(val))
    return urljoin(BASE_URL, url)

def is_server_error(status: int) -> bool:
    return 500 <= status <= 599

def is_client_error(status: int) -> bool:
    return 400 <= status <= 499

def collect_endpoints(spec: dict):
    """Return list of (method, path, operationObject)."""
    eps = []
    for path, item in (spec.get("paths") or {}).items():
        for method, op in item.items():
            mu = method.upper()
            if mu in ("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"):
                eps.append((mu, path, op))
    return eps

def autodiscover_openapi_from_swagger_config() -> dict | None:
    """
    Try common Swagger UI config endpoints to locate the actual OpenAPI doc URL.
    """
    candidates = [
        "/swagger-ui/swagger-config",
        "/swagger-ui/swagger-config.json",
        "/v3/api-docs/swagger-config",
    ]
    tried = []
    for rel in candidates:
        cfg_url = urljoin(BASE_URL, rel)
        tried.append(cfg_url)
        try:
            rc = requests.get(cfg_url, timeout=TIMEOUT_S)
        except requests.RequestException:
            continue
        if rc.status_code == 200:
            try:
                cfg = rc.json()
            except Exception:
                continue
            urls = []
            if "url" in cfg:
                urls.append(cfg["url"])
            if "urls" in cfg:
                urls += [u.get("url") for u in cfg["urls"] if isinstance(u, dict) and u.get("url")]
            for rel_spec in urls:
                spec_url = rel_spec if rel_spec.startswith("http") else urljoin(BASE_URL, rel_spec)
                try:
                    rs = requests.get(spec_url, timeout=TIMEOUT_S)
                    if rs.status_code == 200:
                        return rs.json()
                except requests.RequestException:
                    continue
    raise RuntimeError(f"Could not locate OpenAPI spec via swagger-config. Tried: {tried}")

def load_spec() -> dict:
    # 1) Try OPENAPI_URL (env or default /v3/api-docs)
    try:
        r = requests.get(OPENAPI_URL, timeout=TIMEOUT_S)
        if r.status_code == 200 and "json" in r.headers.get("content-type", "").lower():
            return r.json()
    except requests.RequestException:
        pass
    # 2) Auto-discover from swagger-config
    return autodiscover_openapi_from_swagger_config()

# ---------- Load spec & endpoints ----------
SPEC = load_spec()
ENDPOINTS = collect_endpoints(SPEC)

# ---------- Tests ----------
@pytest.mark.parametrize("method,path,op", ENDPOINTS, ids=_endpoint_id)
def test_endpoint_smoke(method, path, op):
    if should_skip_method(method):
        pytest.skip(f"Skipping {method} by default; enable via TEST_{method}=1")

    # Collect parameters from both the path-item (shared) and the operation (local)
    path_item_obj = (SPEC.get("paths", {}).get(path) or {})
    op_params = op.get("parameters") or []
    path_params_decl = path_item_obj.get("parameters") or []
    all_params = path_params_decl + op_params

    path_params = {}
    query_params_decl = []
    header_params = {}
    cookie_params = {}

    for p in all_params:
        where = p.get("in")
        name = p.get("name")
        schema = (p.get("schema") or {})
        if where == "path":
            # Always provide a value for path params
            path_params[name] = get_param_value(name, schema)
        elif where == "query":
            query_params_decl.append(p)
        elif where == "header":
            if p.get("required", False):
                header_params[name] = get_param_value(name, schema)
        elif where == "cookie":
            if p.get("required", False):
                cookie_params[name] = get_param_value(name, schema)

    # Build query string values (only required or env-overridden by default)
    query = {}
    for p in query_params_decl:
        name = p["name"]
        schema = (p.get("schema") or {})
        required = p.get("required", False)
        # If required OR provided via env, include it
        has_env = os.getenv(f"PARAM_{name}".upper()) is not None
        if required or has_env:
            query[name] = get_param_value(name, schema)

    url = make_url(path, path_params)

    # Prepare request body if needed (POST/PUT/PATCH and requestBody is required)
    json_body = None
    data_body = None
    if method in ("POST", "PUT", "PATCH"):
        rb = (op.get("requestBody") or {})
        content = (rb.get("content") or {})
        raw_env = os.getenv("RAW_JSON_BODY")
        if raw_env:
            # User-provided raw JSON wins
            try:
                json_body = json.loads(raw_env)
            except Exception:
                data_body = raw_env
        elif rb.get("required", False):
            # naive minimal body for application/json
            app_json = content.get("application/json")
            if app_json and "schema" in app_json:
                # Just send an empty object by default; customize via RAW_JSON_BODY if needed
                json_body = {}
            else:
                pytest.skip(f"{method} {path} requires a body; provide RAW_JSON_BODY env to test")

    # Auth header (optional)
    headers = {}
    headers.update(header_params)
    token = os.getenv("AUTH_TOKEN")
    if token:
        header_name = os.getenv("AUTH_HEADER_NAME", "Authorization")
        headers[header_name] = f"Bearer {token}"

    # Fire the request
    http = getattr(requests, method.lower())
    resp = http(url, params=query, headers=headers, json=json_body, data=data_body, timeout=TIMEOUT_S)

    # Assertions
    if is_server_error(resp.status_code):
        pytest.fail(f"{method} {url} -> {resp.status_code} (server error)\n{resp.text[:500]}")
    if FAIL_ON_4XX and is_client_error(resp.status_code):
        pytest.fail(f"{method} {url} -> {resp.status_code} (client error)\n{resp.text[:500]}")

    # If spec declares responses, accept exact code or matching class (e.g., any 2xx if 200/201 declared)
    declared = list((op.get("responses") or {}).keys())
    if declared:
        ok = str(resp.status_code) in declared or any(
            k.endswith("XX") and int(k[0]) == resp.status_code // 100 for k in declared
        )
        if not ok:
            # Not a hard failure to avoid false negatives on testnet variability
            pytest.xfail(f"Undeclared status {resp.status_code} for {method} {path}")



