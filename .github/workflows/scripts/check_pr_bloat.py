#!/usr/bin/env python3
"""Fail a PR on bloated reviewer-facing prose.

See AGENTS.md "Commit & Pull Request Guidelines". Env: BASE_REF, HEAD_REF
("WORKTREE" for local), PR_BODY / PR_BODY_FILE, BLOAT_BYPASS=1 to report
findings without failing.
"""

import os
import re
import subprocess
import sys

BASE_REF = os.environ.get("BASE_REF") or "origin/main"
HEAD_REF = os.environ.get("HEAD_REF") or "HEAD"
BYPASS = os.environ.get("BLOAT_BYPASS") == "1"


def num(name, fallback):
    # A bad value exits rather than comparing against NaN, which would silently
    # pass everything.
    raw = os.environ.get(name)
    try:
        value = float(raw) if raw not in (None, "") else float(fallback)
    except ValueError:
        value = float("nan")
    if value != value or value < 0:
        print(f'{name}="{raw}" is not a non-negative number; refusing to run.', file=sys.stderr)
        sys.exit(2)
    return value


# Comment volume must be proportionate to the code it documents: 4 lines above a
# new field is bloat, the same 4 above a 40-line function is not. RATIO is comment
# lines per attached added-code line; BLOCK is the absolute ceiling regardless.
MAX_COMMENT_RATIO = num("BLOAT_MAX_COMMENT_RATIO", 1)
MAX_COMMENT_BLOCK = num("BLOAT_MAX_COMMENT_BLOCK", 10)
RATIO_MIN_BLOCK = 2
# Flat, deliberately: description length does not scale with diff size. A big
# change that genuinely needs more gets the `verbose-ok` label.
DESCRIPTION_BUDGET = num("BLOAT_DESCRIPTION_BUDGET", 240)
MIN_DESCRIPTION_WORDS = 10
DENSITY_MIN_ADDED = 20
DENSITY_ADVISORY = 0.25
MD_PARAGRAPH_ADVISORY = 120

# Tool-attribution trailers: adverts, not information. Blocked outright.
AD_TRAILERS = [
    re.compile(r"🤖\s*Generated with", re.I),
    re.compile(r"Generated with \[?Claude Code", re.I),
    re.compile(r"Co-authored-by:\s*(?:Claude|Cursor|Devin|Codex|GitHub Copilot)", re.I),
    re.compile(r"noreply@anthropic\.com", re.I),
    re.compile(r"Co-authored-by:.*\[bot\]", re.I),
]

# Never scanned: generated, vendored, or machine-owned.
IGNORED = [
    re.compile(r"(^|/)(build|out|bin|node_modules|\.gradle|\.terraform)/"),
    re.compile(r"(^|/)gradle\.lockfile$"),
    re.compile(r"^gradle/wrapper/"),
    re.compile(r"^third_party/"),
    re.compile(r"^packages/api/src/main/resources/token-registry/"),
    re.compile(r"\.(jar|png|jpg|jpeg|gif|ico|pdf|zip|gz)$", re.I),
]

# Scanned but never blocking: prose is the point.
ADVISORY_ONLY = [re.compile(r"\.mdx?$", re.I)]

SLASH = {"kt", "kts", "java", "go", "ts", "tsx", "js", "jsx", "mjs", "sol", "css", "scss"}
HASH = {"tf", "tfvars", "yml", "yaml", "sh", "bash", "zsh", "py", "toml", "properties", "conf"}
DASH = {"sql"}
# Extensionless files whose comment style is still unambiguous.
BY_NAME = {"makefile": "hash", "dockerfile": "hash"}

# Machine-directed comments carry no prose; they must never count toward a block.
DIRECTIVE = re.compile(
    r"^(?://|#|--|\*)\s*(?:ktlint-|noinspection|spotless:|region\b|endregion\b|@Suppress|"
    r"SPDX-License-Identifier|checkov:|tflint-ignore|trivy:ignore|ts:skip|yamllint|"
    r"shellcheck|noqa|type:\s*ignore|pylint:|pragma:|mypy:|ruff:|nosec|codeql\[)",
    re.I,
)


def git(args):
    try:
        return subprocess.run(
            ["git", *args], capture_output=True, check=True, text=True
        ).stdout
    except subprocess.CalledProcessError as err:
        # Fail closed: an unresolvable diff must not silently pass the check.
        raise RuntimeError(f"git {' '.join(args)} failed: {err.stderr.strip()}") from err


def ext(f):
    return f.rsplit(".", 1)[-1].lower() if "." in os.path.basename(f) else ""


def ignored(f):
    return any(r.search(f) for r in IGNORED)


def advisory_only(f):
    return any(r.search(f) for r in ADVISORY_ONLY)


def is_markdown(f):
    return bool(re.search(r"\.mdx?$", f, re.I))


def comment_syntax(f):
    e = ext(f)
    if e in SLASH:
        return "slash"
    if e in HASH:
        return "hash"
    if e in DASH:
        return "dash"
    return BY_NAME.get(os.path.basename(f).lower())


def classify(line, syntax):
    body = line.strip()
    if not body:
        return "blank"
    if body.startswith("#!"):
        return "code"
    if DIRECTIVE.match(body):
        return "code"
    if syntax == "slash":
        # A `*` continuation only counts inside a block we are already tracking;
        # treating it as a comment unconditionally is right for added KDoc runs.
        return "comment" if re.match(r"^(//|/\*|\*/|\*(\s|$))", body) else "code"
    if syntax == "hash":
        return "comment" if body.startswith("#") else "code"
    if syntax == "dash":
        return "comment" if body.startswith("--") else "code"
    return "code"


# --- description ------------------------------------------------------------


def prose(body):
    text = re.sub(r"```[\s\S]*?```", " ", body)
    text = re.sub(r"~~~[\s\S]*?~~~", " ", text)
    text = re.sub(r"<!--[\s\S]*?-->", " ", text)
    kept = [
        line
        for line in text.split("\n")
        if not re.match(r"^\s*(?:🤖\s*Generated with|Co-authored-by:|Generated with)", line, re.I)
        and not re.match(r"^\s*!\[", line)
    ]
    text = "\n".join(kept)
    text = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"^\s*[-*+]\s+", "", text, flags=re.M)
    text = re.sub(r"^\s*#{1,6}\s+", "", text, flags=re.M)
    return re.sub(r"[`*_>|]", " ", text)


def word_count(text):
    return sum(1 for w in text.split() if re.search(r"[A-Za-z0-9]", w))


def resolve_body():
    path = os.environ.get("PR_BODY_FILE")
    if path and os.path.exists(path):
        with open(path, encoding="utf-8") as fh:
            return fh.read()
    if "PR_BODY" in os.environ:
        return os.environ["PR_BODY"]
    try:
        return subprocess.run(
            ["gh", "pr", "view", "--json", "body", "-q", ".body"],
            capture_output=True,
            check=True,
            text=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError):
        return None


# --- diff -------------------------------------------------------------------


def added_by_file(rng, worktree):
    files = {}
    diff = git(["diff", "--unified=0", "--no-color", "-M", "--diff-filter=AMR", *rng])
    file = None
    syntax = None
    line_no = 0
    hunk = 0
    cur = None

    def ensure(f):
        return files.setdefault(f, {"file": f, "added": 0, "comment": 0, "runs": [], "md": []})

    def close_run():
        nonlocal cur
        if cur:
            ensure(cur["file"])["runs"].append(cur)
        cur = None

    def extend(f, kind, line, text):
        nonlocal cur
        if cur and cur["kind"] == kind and cur["hunk"] == hunk:
            cur["length"] += 1
            return
        close_run()
        cur = {
            "file": f,
            "kind": kind,
            "hunk": hunk,
            "start": line,
            "length": 1,
            "preview": text.strip(),
        }

    for raw in diff.split("\n"):
        if raw.startswith("diff --git "):
            close_run()
            file = None
            continue
        if raw.startswith("+++ "):
            close_run()
            p = raw[4:].strip()
            file = None if p == "/dev/null" else re.sub(r"^b/", "", p)
            if file and ignored(file):
                file = None
            syntax = comment_syntax(file) if file else None
            if file:
                ensure(file)
            continue
        if raw.startswith("@@"):
            close_run()
            hunk += 1
            m = re.match(r"^@@ -\d+(?:,\d+)? \+(\d+)", raw)
            line_no = int(m.group(1)) if m else 0
            continue
        # A deletion breaks a run: the added lines either side are not contiguous.
        if raw.startswith("-") and not raw.startswith("---"):
            close_run()
        if not file or not raw.startswith("+") or raw.startswith("+++"):
            continue

        text = raw[1:]
        rec = ensure(file)
        rec["added"] += 1
        kind = "md" if is_markdown(file) else classify(text, syntax)
        if kind == "comment":
            rec["comment"] += 1
        if kind == "blank":
            close_run()
        elif kind != "md":
            extend(file, kind, line_no, text)
        if is_markdown(file):
            rec["md"].append(text)
        line_no += 1
    close_run()

    if worktree:
        for untracked in git(["ls-files", "--others", "--exclude-standard"]).split("\n"):
            f = untracked.strip()
            if not f or ignored(f) or not os.path.exists(f):
                continue
            syn = comment_syntax(f)
            if not syn and not is_markdown(f):
                continue  # nothing to classify (images, binaries)
            try:
                with open(f, encoding="utf-8") as fh:
                    content = fh.read()
            except (OSError, UnicodeDecodeError):
                continue  # unreadable local file is not a bloat finding
            rec = ensure(f)
            run = None
            for i, text in enumerate(content.split("\n")):
                rec["added"] += 1
                kind = "md" if is_markdown(f) else classify(text, syn)
                if kind == "comment":
                    rec["comment"] += 1
                if kind == "blank" or kind == "md":
                    if run:
                        rec["runs"].append(run)
                    run = None
                elif run and run["kind"] == kind:
                    run["length"] += 1
                else:
                    if run:
                        rec["runs"].append(run)
                    run = {
                        "file": f,
                        "kind": kind,
                        "hunk": 0,
                        "start": i + 1,
                        "length": 1,
                        "preview": text.strip(),
                    }
                if is_markdown(f):
                    rec["md"].append(text)
            if run:
                rec["runs"].append(run)
    return list(files.values())


def md_paragraphs(lines):
    out = []
    buf = []
    fenced = False

    def flush():
        nonlocal buf
        if buf:
            words = word_count(prose("\n".join(buf)))
            if words > 0:
                out.append({"words": words, "preview": buf[0].strip()[:90]})
        buf = []

    for line in lines:
        if re.match(r"^\s*(```|~~~)", line):
            fenced = not fenced
            flush()
            continue
        if fenced:
            continue
        if not line.strip() or re.match(r"^\s*\|", line) or re.match(r"^\s*(?:[-*+]|\d+\.)\s", line):
            flush()
        else:
            buf.append(line)
    flush()
    return out


# --- reporting --------------------------------------------------------------

MARKER = "<!-- pr-bloat-guard -->"


def write_comment(lines):
    path = os.environ.get("BLOAT_COMMENT_FILE")
    if not path:
        return
    try:
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("\n".join([MARKER, *lines]) + "\n")
    except OSError:
        pass  # best effort


def write_summary(lines):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    try:
        with open(path, "a", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")
    except OSError:
        pass  # best effort


def check_description(blocking):
    body = resolve_body()
    if body is None:
        print("No PR description available (set PR_BODY_FILE / PR_BODY); skipping that check.")
        return None
    if any(r.search(body) for r in AD_TRAILERS):
        blocking.append(
            {
                "kind": "ad-trailer",
                "detail": "description carries a tool-attribution trailer — delete it",
                "fix": "tool attribution is an advert, not information for the reviewer",
            }
        )
    words = word_count(prose(body))
    budget = int(DESCRIPTION_BUDGET)
    if words > budget:
        blocking.append(
            {
                "kind": "description",
                "detail": f"{words} words, budget {budget}",
                "fix": f"cut to {budget} words: state what changed and why, and let the diff show the rest",
            }
        )
    elif words < MIN_DESCRIPTION_WORDS:
        blocking.append(
            {
                "kind": "description",
                "detail": f"{words} word{'' if words == 1 else 's'} is too few to tell a reviewer anything",
                "fix": f"write at least {MIN_DESCRIPTION_WORDS} words saying what changed and why",
            }
        )
    return words


def check_comment_blocks(f, blocking, advisory):
    runs = f["runs"]
    code_added = sum(r["length"] for r in runs if r["kind"] == "code")
    for i, b in enumerate(runs):
        if b["kind"] != "comment":
            continue
        nxt = runs[i + 1] if i + 1 < len(runs) else None
        # A top-of-file comment documents the file, not the next line.
        if b["start"] <= 3:
            documents = code_added
        elif nxt and nxt["kind"] == "code" and nxt["hunk"] == b["hunk"]:
            documents = nxt["length"]
        else:
            documents = 0
        ratio = b["length"] / max(documents, 1)
        over_ratio = b["length"] >= RATIO_MIN_BLOCK and ratio > MAX_COMMENT_RATIO
        over_block = b["length"] > MAX_COMMENT_BLOCK
        if not over_ratio and not over_block:
            continue
        if over_block:
            detail = f"{b['length']}-line comment block (absolute max {int(MAX_COMMENT_BLOCK)})"
        elif documents == 0:
            detail = (
                f"{b['length']} comment lines added above unchanged code"
                " — prose-only additions belong in a README or notes/"
            )
        else:
            detail = (
                f"{b['length']} comment lines documenting {documents} added code"
                f" line{'' if documents == 1 else 's'} — comment should not outweigh"
                f" the code (max {MAX_COMMENT_RATIO:g}:1)"
            )
        finding = {
            "kind": "comment-block",
            "file": f["file"],
            "line": b["start"],
            "detail": detail,
            "preview": b["preview"][:90],
        }
        (advisory if advisory_only(f["file"]) else blocking).append(finding)


def check_advisories(f, advisory):
    if f["added"] >= DENSITY_MIN_ADDED and not is_markdown(f["file"]):
        ratio = f["comment"] / f["added"]
        if ratio > DENSITY_ADVISORY:
            advisory.append(
                {
                    "kind": "density",
                    "file": f["file"],
                    "detail": f"{ratio * 100:.0f}% of added lines are comments ({f['comment']}/{f['added']})",
                }
            )
    for p in md_paragraphs(f["md"]):
        if p["words"] > MD_PARAGRAPH_ADVISORY:
            advisory.append(
                {
                    "kind": "md-paragraph",
                    "file": f["file"],
                    "detail": f"{p['words']}-word paragraph (advisory limit {int(MD_PARAGRAPH_ADVISORY)})",
                    "preview": p["preview"],
                }
            )


def label(f):
    where = ""
    if f.get("file"):
        where = f"{f['file']}{':' + str(f['line']) if f.get('line') else ''} — "
    tail = f"\n      {f['preview']}" if f.get("preview") else ""
    return f"{where}{f['detail']}{tail}"


def failure_comment(blocking, budget):
    rows = [
        f"| {'`' + f['file'] + (':' + str(f['line']) if f.get('line') else '') + '`' if f.get('file') else '_PR description_'} | {f['detail']} |"
        for f in blocking
    ]
    plural = "" if len(blocking) == 1 else "s"
    return [
        "### PR bloat check failed",
        "",
        f"{len(blocking)} thing{plural} to fix before this can merge.",
        "",
        "| Where | Problem |",
        "| --- | --- |",
        *rows,
        "",
        "**How to fix**",
        "",
        f"- **Description:** what changed and why, in two or three sentences ({budget}-word ceiling)."
        " Drop `## Summary` / `## Test plan` scaffolding unless there is genuinely something new to"
        " test, and drop any tool-attribution trailer.",
        "- **Comments:** default to none. Add one only where the WHY is non-obvious (a hidden"
        " constraint, an invariant, a workaround), and keep it to one line. A comment must not"
        " outweigh the code it documents.",
        "- Don't restate what the code does, what a technical term already implies, or what a linked"
        " design doc already says. Don't explain what something does _not_ do.",
        "- Real design rationale belongs in a module README or `notes/` with a link, not stacked"
        " above the code.",
        "",
        'Full guidance: AGENTS.md, "Commit & Pull Request Guidelines". Reproduce locally with'
        " `make check-pr-bloat`.",
        "",
        "**If the length is genuinely warranted**, add the `verbose-ok` label to bypass this check —"
        " that is what it is for. It stays visible on the PR, so the call is on the record.",
        *(
            [
                "",
                "_An authorized `verbose-ok` bypass is active, so this check is not blocking the merge._",
            ]
            if BYPASS
            else []
        ),
    ]


def main():
    worktree = HEAD_REF == "WORKTREE"
    # Merge base, not the tip: a two-dot diff against a moved origin/main reports
    # other people's commits as this change. CI's three-dot range already does this.
    rng = (
        [git(["merge-base", BASE_REF, "HEAD"]).strip()]
        if worktree
        else [f"{BASE_REF}...{HEAD_REF}"]
    )
    files = added_by_file(rng, worktree)
    total_added = sum(f["added"] for f in files)

    blocking = []
    advisory = []
    budget = int(DESCRIPTION_BUDGET)
    words = check_description(blocking)

    for f in files:
        check_comment_blocks(f, blocking, advisory)
        check_advisories(f, advisory)

    if words is not None:
        print(f"Description: {words} words (budget {budget}).")
    print(f"Diff: {total_added} added lines across {len(files)} scanned file(s).")

    if advisory:
        print(f"\n{len(advisory)} advisory finding(s) (not blocking):")
        for f in advisory:
            print(f"  [{f['kind']}] {label(f)}")

    summary = []
    if not blocking:
        print("\nNo blocking bloat findings. OK.")
        summary += ["### PR bloat check", "", "No blocking findings."]
        if words is not None:
            summary += ["", f"Description: {words} / {budget} words."]
    else:
        print(f"\n{len(blocking)} blocking bloat finding(s):", file=sys.stderr)
        for f in blocking:
            fix = f"\n      fix: {f['fix']}" if f.get("fix") else ""
            print(f"  [{f['kind']}] {label(f)}{fix}", file=sys.stderr)
        print(
            "\nAGENTS.md: a PR description is a couple of sentences; a code comment is one short"
            "\nline explaining a non-obvious WHY. Move real design rationale into a README and link"
            "\nit. If this PR genuinely needs the length, add the `verbose-ok` label.",
            file=sys.stderr,
        )
        summary += [
            "### PR bloat check — FAILED",
            "",
            f"{len(blocking)} blocking finding(s). AGENTS.md: descriptions are a couple of sentences;"
            " comments are one short line on a non-obvious WHY.",
            "",
            "| Kind | Where | Problem |",
            "| --- | --- | --- |",
            *[
                f"| {f['kind']} | {'`' + f['file'] + (':' + str(f['line']) if f.get('line') else '') + '`' if f.get('file') else 'PR description'} | {f['detail']} |"
                for f in blocking
            ],
            "",
            "Add the `verbose-ok` label if the length is genuinely warranted.",
        ]
        write_comment(failure_comment(blocking, budget))

    if advisory:
        summary += [
            "",
            "<details><summary>Advisory findings (not blocking)</summary>",
            "",
            *[
                f"- `{f['kind']}` {f.get('file', '')}{':' + str(f['line']) if f.get('line') else ''} — {f['detail']}"
                for f in advisory
            ],
            "",
            "</details>",
        ]
    write_summary(summary)

    if blocking:
        if BYPASS:
            print(
                "\nBLOAT_BYPASS set (authorized `verbose-ok` label); reporting only.", file=sys.stderr
            )
            return
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except Exception as err:  # noqa: BLE001 - any failure must fail closed, not pass
        print(err, file=sys.stderr)
        sys.exit(2)
