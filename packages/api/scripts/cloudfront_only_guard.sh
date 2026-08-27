#!/usr/bin/env bash
# The API ALBs only admit the CloudFront origin-facing prefix list, so the
# *.prod.veworld.vechain.org hostnames no longer resolve to a reachable target
# from a GitHub runner. Delete this guard once the suites can reach the dead
# colour again.

ORIGIN_HOST_SUFFIX=".prod.veworld.vechain.org"

reject_direct_origin() {
  local label="$1" url="$2" host
  host="${url#*://}"
  host="${host%%/*}"
  host="${host%%:*}"

  [[ "$host" == *"$ORIGIN_HOST_SUFFIX" ]] || return 0

  cat >&2 <<EOF
$label points at $host, an ALB hostname that only accepts CloudFront traffic.

Direct access to the API load balancers was removed, so this suite cannot
reach a colour that CloudFront does not front. The dead colour has no
CloudFront distribution yet, which leaves no supported target for a
pre-cutover run.

Options: target a CloudFront hostname (https://indexer.mainnet.vechain.org)
to test the live colour, or pass an explicit reachable --base-url.
EOF
  return 1
}
