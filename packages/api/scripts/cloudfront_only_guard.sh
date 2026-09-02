#!/usr/bin/env bash
# The API ALBs admit only the CloudFront origin-facing prefix list, so the
# live.*/dead.* origin hostnames are unreachable from anywhere but a
# distribution. Every colour is fronted by one; target its alias instead.

ORIGIN_HOST_PATTERN='^(mainnet|testnet)\.(live|dead)\.prod\.veworld\.vechain\.org$'

reject_direct_origin() {
  local label="$1" url="$2" host
  host="${url#*://}"
  host="${host%%/*}"
  host="${host%%:*}"

  [[ "$host" =~ $ORIGIN_HOST_PATTERN ]] || return 0

  cat >&2 <<EOF
$label points at $host, an ALB hostname that only accepts CloudFront traffic.

Use the distribution in front of that colour instead:
  live: https://indexer.mainnet.vechain.org
        https://indexer.testnet.vechain.org
  dead: https://mainnet.dead-cdn.prod.veworld.vechain.org
        https://testnet.dead-cdn.prod.veworld.vechain.org
EOF
  return 1
}
