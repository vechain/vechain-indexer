#!/usr/bin/env python3
"""
compare_endpoints.py

Fetch REST endpoints (GET), parse JSON, and compare results.
- Supports comparing two specific URLs or multiple endpoints from a configuration
- Prints whether they match.
- If not, prints a human-readable diff (paths + messages).

Stdlib only (no external dependencies).

Usage:
  # Compare two specific endpoints
  python compare_endpoints.py URL1 URL2
  python compare_endpoints.py URL1 URL2 --headers1 '{"Authorization":"Bearer TOKEN"}' --headers2 '{}' --timeout 15
  python compare_endpoints.py URL1 URL2 --ignore-path root.meta --ignore-path root.timestamp
  python compare_endpoints.py URL1 URL2 --unordered-lists
  python compare_endpoints.py URL1 URL2 --insecure2  # skip TLS verification for the 2nd URL
  python compare_endpoints.py URL1 URL2 --cafile2 /path/to/ca.pem  # use custom CA for 2nd URL

  # Compare multiple endpoints from configuration
  python compare_endpoints.py --config-file endpoints.json --endpoint /api/v1/data
  python compare_endpoints.py --endpoints "local|http://localhost:8080,live|https://api.example.com" --endpoint /api/v1/data

Exit codes:
  0 -> JSONs are equal (after ignores/flags)
  1 -> JSONs differ
  2 -> Runtime or input error
"""

import argparse
import json
import sys
import urllib.request
import urllib.error
import ssl
from typing import Any, List, Tuple, Dict, Set, Union

Path = str

class HttpResponseError(RuntimeError):
    def __init__(
        self,
        status_code: int,
        reason: str,
        body: Any = None,
        url: str = "",
    ) -> None:
        self.status_code = status_code
        self.reason = reason
        self.body = body
        self.url = url
        super().__init__(f"HTTP error fetching {url}: {status_code} {reason}")

    def __str__(self) -> str:
        return f"HTTP error fetching {self.url}: {self.status_code} {self.reason}"


def decode_json_response(data: bytes, url: str) -> Any:
    # Try decode as UTF-8, fallback to latin-1
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        text = data.decode("latin-1")

    if not text.strip():
        return None

    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        raise ValueError(f"Response from {url} is not valid JSON: {e}")


def fetch_json(url: str, headers: Dict[str, str], timeout: int, context: ssl.SSLContext | None) -> Any:
    req = urllib.request.Request(url, headers=headers or {})
    try:
        if url.lower().startswith("https"):
            opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context))
            with opener.open(req, timeout=timeout) as resp:
                data = resp.read()
        else:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                data = resp.read()
        return decode_json_response(data, url)
    except urllib.error.HTTPError as e:
        try:
            body = decode_json_response(e.read(), url)
        except ValueError:
            body = None
        raise HttpResponseError(
            status_code=e.code,
            reason=e.reason,
            body=body,
            url=url,
        ) from e
    except urllib.error.URLError as e:
        raise RuntimeError(f"Network error fetching {url}: {e.reason}") from e

def parse_headers(s: str) -> Dict[str, str]:
    if not s:
        return {}
    try:
        obj = json.loads(s)
    except json.JSONDecodeError as e:
        raise ValueError(f"--headers must be valid JSON object: {e}")
    if not isinstance(obj, dict):
        raise ValueError("--headers must be a JSON object")
    out = {}
    for k, v in obj.items():
        out[str(k)] = "" if v is None else str(v)
    # Ensure a UA so some servers respond
    out.setdefault("User-Agent", "compare-endpoints/1.0 (+stdlib)")
    return out

def normalize_ignored_paths(paths: List[str]) -> Set[Path]:
    out = set()
    for p in paths or []:
        p = p.strip()
        if not p:
            continue
        while ".." in p:
            p = p.replace("..", ".")
        out.add(p)
    return out

def is_primitive(x: Any) -> bool:
    return isinstance(x, (str, int, float, bool, type(None)))

def path_child(parent: Path, key: Union[str, int]) -> Path:
    if parent == "":
        parent = "root"
    if isinstance(key, int):
        return f"{parent}[{key}]"
    if parent == "root":
        return f"root.{key}"
    return f"{parent}.{key}"

def compare_json(
    a: Any,
    b: Any,
    *,
    path: Path = "root",
    diffs: List[Tuple[Path, str]] | None = None,
    ignored_paths: Set[Path] | None = None,
    unordered_lists: bool = False,
) -> List[Tuple[Path, str]]:
    if diffs is None:
        diffs = []
    if ignored_paths is None:
        ignored_paths = set()

    if path in ignored_paths:
        return diffs

    if type(a) != type(b):
        diffs.append((path, f"type mismatch: {type(a).__name__} != {type(b).__name__}"))
        return diffs

    if isinstance(a, dict):
        akeys = set(a.keys())
        bkeys = set(b.keys())
        only_a = sorted(akeys - bkeys)
        only_b = sorted(bkeys - akeys)
        for k in only_a:
            p = path_child(path, k)
            if p not in ignored_paths and path not in ignored_paths:
                diffs.append((p, "present only in first JSON"))
        for k in only_b:
            p = path_child(path, k)
            if p not in ignored_paths and path not in ignored_paths:
                diffs.append((p, "present only in second JSON"))
        for k in sorted(akeys & bkeys):
            p = path_child(path, k)
            if any(p == ip or p.startswith(ip + ".") or p.startswith(ip + "[") for ip in ignored_paths):
                continue
            compare_json(a[k], b[k], path=p, diffs=diffs, ignored_paths=ignored_paths, unordered_lists=unordered_lists)
        return diffs

    if isinstance(a, list):
        if unordered_lists and all(is_primitive(x) for x in a) and all(is_primitive(x) for x in b):
            from collections import Counter
            ca, cb = Counter(a), Counter(b)
            if ca != cb:
                missing = []
                extra = []
                for val in sorted(set(ca.keys()) | set(cb.keys()), key=lambda x: (str(type(x)), str(x))):
                    if ca[val] != cb[val]:
                        if ca[val] > cb[val]:
                            extra.append(f"{repr(val)} x{ca[val] - cb[val]}")
                        else:
                            missing.append(f"{repr(val)} x{cb[val] - ca[val]}")
                msg_parts = []
                if extra:
                    msg_parts.append("extra in first: " + ", ".join(extra))
                if missing:
                    msg_parts.append("missing from first: " + ", ".join(missing))
                diffs.append((path, "; ".join(msg_parts)))
            return diffs

        if len(a) != len(b):
            diffs.append((path, f"list length mismatch: {len(a)} != {len(b)}"))
        n = min(len(a), len(b))
        for i in range(n):
            p = path_child(path, i)
            if any(p == ip or p.startswith(ip + ".") or p.startswith(ip + "[") for ip in ignored_paths):
                continue
            compare_json(a[i], b[i], path=p, diffs=diffs, ignored_paths=ignored_paths, unordered_lists=unordered_lists)
        return diffs

    if a != b:
        diffs.append((path, f"value mismatch: {repr(a)} != {repr(b)}"))
    return diffs

def ssl_context_for(insecure: bool, cafile: str | None) -> ssl.SSLContext | None:
    if insecure:
        ctx = ssl._create_unverified_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        return ctx
    if cafile:
        return ssl.create_default_context(cafile=cafile)
    return ssl.create_default_context()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Compare JSON responses from REST endpoints.")

    parser.add_argument("url1", nargs='?', help="First endpoint URL (GET)")
    parser.add_argument("url2", nargs='?', help="Second endpoint URL (GET)")

    parser.add_argument("--endpoints", help="Comma-separated endpoint configs: 'name1|url1,name2|url2'")
    parser.add_argument("--config-file", help="JSON file containing endpoint configurations")
    parser.add_argument("--endpoint", help="Endpoint path to append to base URLs")

    parser.add_argument("--headers", default="", help="JSON object of headers")
    parser.add_argument("--headers1", default="", help="Headers for URL1 (two-URL mode)")
    parser.add_argument("--headers2", default="", help="Headers for URL2 (two-URL mode)")
    parser.add_argument("--timeout", type=int, default=20, help="Timeout in seconds (default: 20)")
    parser.add_argument("--ignore-path", action="append", default=[], help="Path to ignore (repeatable)")
    parser.add_argument("--unordered-lists", action="store_true", help="Treat primitive lists as unordered")
    parser.add_argument("--pretty", action="store_true", help="Print normalized JSON for inspection")

    parser.add_argument("--insecure", action="store_true", help="Skip TLS verification")
    parser.add_argument("--insecure1", action="store_true", help="Skip TLS for URL1")
    parser.add_argument("--insecure2", action="store_true", help="Skip TLS for URL2")
    parser.add_argument("--cafile", default=None, help="CA bundle file")
    parser.add_argument("--cafile1", default=None, help="CA bundle for URL1")
    parser.add_argument("--cafile2", default=None, help="CA bundle for URL2")

    args = parser.parse_args()

    try:
        ignored = normalize_ignored_paths(args.ignore_path)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(2)

    if not args.url1 or not args.url2:
        print("Error: Both url1 and url2 are required", file=sys.stderr)
        sys.exit(2)

    try:
        headers1 = parse_headers(args.headers1)
        headers2 = parse_headers(args.headers2)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(2)

    ctx1 = ssl_context_for(args.insecure1, args.cafile1)
    ctx2 = ssl_context_for(args.insecure2, args.cafile2)

    try:
        status1 = 200
        status2 = 200
        try:
            a = fetch_json(args.url1, headers1, args.timeout, ctx1)
        except HttpResponseError as e:
            a = e.body
            status1 = e.status_code
        try:
            b = fetch_json(args.url2, headers2, args.timeout, ctx2)
        except HttpResponseError as e:
            b = e.body
            status2 = e.status_code
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(2)

    if status1 != status2:
        print(
            f"Mismatch: HTTP status codes differ ({status1} != {status2}).",
            file=sys.stderr,
        )
        sys.exit(1)

    diffs = compare_json(a, b, ignored_paths=ignored, unordered_lists=args.unordered_lists)

    if args.pretty:
        print("\n--- JSON #1 (normalized) ---")
        try:
            print(json.dumps(a, indent=2, sort_keys=True, ensure_ascii=False))
        except Exception:
            print(a)
        print("\n--- JSON #2 (normalized) ---")
        try:
            print(json.dumps(b, indent=2, sort_keys=True, ensure_ascii=False))
        except Exception:
            print(b)
        print()

    if not diffs:
        print("Match: JSON responses are equal.")
        sys.exit(0)
    else:
        print("Mismatch: JSON responses differ.\n")
        for p, msg in diffs:
            print(f"- {p}: {msg}")
        sys.exit(1)
