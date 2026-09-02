#!/usr/bin/env bash
# Content hash of one service's Docker build context: the exact input set
# Dockerfile COPYs in, after .dockerignore. Equal hashes mean an identical
# image, so a build can be skipped and the existing registry tag reused.
#
# The base image tag lives in Dockerfile and so is part of the hash; a Renovate
# base bump is what forces a rebuild for fresh OS packages.
set -euo pipefail

usage() {
    echo "usage: $(basename "$0") <api|indexer> [git-ref]" >&2
    exit 2
}

PACKAGE="${1:-}"
REF="${2:-}"

case "$PACKAGE" in
    api | indexer) ;;
    *) usage ;;
esac

# "<mode> <object> <path>", from a ref's tree or from the working tree.
if [ -n "$REF" ]; then
    git rev-parse --verify --quiet "${REF}^{tree}" >/dev/null ||
        { echo "unknown git ref: $REF" >&2; exit 3; }
    entries=$(git ls-tree -r "$REF" | awk -F'\t' '{split($1, f, " "); print f[1], f[3], $2}')
else
    # A scratch index, so an uncommitted edit counts and the real one is untouched.
    scratch=$(mktemp)
    trap 'rm -f "$scratch"' EXIT
    GIT_INDEX_FILE="$scratch" git read-tree HEAD
    GIT_INDEX_FILE="$scratch" git add -A -- "$(git rev-parse --show-toplevel)"
    entries=$(GIT_INDEX_FILE="$scratch" git ls-files -s |
        awk -F'\t' '{split($1, f, " "); print f[1], f[2], $2}')
fi

include="^[0-9]+ [0-9a-f]+ (Dockerfile|\.dockerignore|gradlew|build\.gradle\.kts|settings\.gradle\.kts|system\.properties|gradle/.*|third_party/.*|packages/(common|${PACKAGE})/.*)$"
exclude="^[0-9]+ [0-9a-f]+ (gradle/docker\.gradle\.properties|packages/[^/]+/(src/test/.*|scripts/.*|\.env\.example.*))$"

matched=$(printf '%s\n' "$entries" | grep -E "$include" | grep -Ev "$exclude" | LC_ALL=C sort)

if [ -z "$matched" ]; then
    echo "no build-context files matched for $PACKAGE at ${REF:-<index>}" >&2
    exit 4
fi

printf '%s\n' "$matched" | sha256sum | cut -c1-12
