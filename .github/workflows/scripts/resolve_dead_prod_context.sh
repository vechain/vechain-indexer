#!/usr/bin/env bash

set -euo pipefail

require_confirmation="${REQUIRE_CONFIRMATION:-false}"
confirm_restore="${CONFIRM_RESTORE:-false}"

mainnet_live="${MAINNET_LIVE_COLOR:?MAINNET_LIVE_COLOR is required}"
mainnet_dead="${MAINNET_DEAD_COLOR:?MAINNET_DEAD_COLOR is required}"
testnet_live="${TESTNET_LIVE_COLOR:?TESTNET_LIVE_COLOR is required}"
testnet_dead="${TESTNET_DEAD_COLOR:?TESTNET_DEAD_COLOR is required}"

if [[ "${require_confirmation}" == "true" && "${confirm_restore}" != "true" ]]; then
  echo "Restore confirmation was not provided."
  exit 1
fi

if [[ "${mainnet_live}" != "${testnet_live}" ]]; then
  echo "Mainnet and testnet live colors differ (${mainnet_live} vs ${testnet_live}); aborting."
  exit 1
fi

if [[ "${mainnet_dead}" != "${testnet_dead}" ]]; then
  echo "Mainnet and testnet dead colors differ (${mainnet_dead} vs ${testnet_dead}); aborting."
  exit 1
fi

case "${mainnet_live}" in
  prod-blue|prod-green)
    ;;
  *)
    echo "Unexpected live color ${mainnet_live}; aborting."
    exit 1
    ;;
esac

case "${mainnet_dead}" in
  prod-blue|prod-green)
    ;;
  *)
    echo "Unexpected dead color ${mainnet_dead}; aborting."
    exit 1
    ;;
esac

source_color="${mainnet_live}"
target_color="${mainnet_dead}"

{
  echo "source_color=${source_color}"
  echo "target_color=${target_color}"
  echo "source_main_cluster=${source_color}-Mainnet"
  echo "source_test_cluster=${source_color}-Testnet"
  echo "target_main_cluster=${target_color}-Mainnet"
  echo "target_test_cluster=${target_color}-Testnet"
  echo "ecs_cluster=${target_color}-veworld-cluster"
} >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"

{
  echo "### Dead Prod Recovery Context"
  echo "- Live prod color: \`${source_color}\`"
  echo "- Dead prod color: \`${target_color}\`"
  echo "- Source mainnet cluster: \`${source_color}-Mainnet\`"
  echo "- Source testnet cluster: \`${source_color}-Testnet\`"
  echo "- Target mainnet cluster: \`${target_color}-Mainnet\`"
  echo "- Target testnet cluster: \`${target_color}-Testnet\`"
} >> "${GITHUB_STEP_SUMMARY:?GITHUB_STEP_SUMMARY is required}"
