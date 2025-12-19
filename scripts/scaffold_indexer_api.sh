#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
scaffold_indexer_api.sh

Prints a checklist + suggested file paths for adding a new (default: versioned) indexer + API.
This script does not modify files; it is intended as a copy/paste helper for humans and AI agents.

Usage:
  scripts/scaffold_indexer_api.sh <feature_group> <feature_key> <ModelName>

Examples:
  scripts/scaffold_indexer_api.sh accounts account-overview AccountOverview
  scripts/scaffold_indexer_api.sh contracts contract Contract

Notes:
  - feature_group is the broad profile (e.g. "accounts", "contracts")
  - feature_key is the specific profile / config key (e.g. "account-overview", "vet-balance")
  - ModelName should match the `packages/common` data class
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

FEATURE_GROUP="${1:-}"
FEATURE_KEY="${2:-}"
MODEL="${3:-}"

if [[ -z "${FEATURE_GROUP}" || -z "${FEATURE_KEY}" || -z "${MODEL}" ]]; then
  usage
  exit 2
fi

KEY_ENV="$(echo "${FEATURE_KEY}" | tr '[:lower:]-' '[:upper:]_')"

cat <<EOF
New Indexer + API (default: versioned)

**Profiles**
- Indexer: @Profile("${FEATURE_GROUP}", "${FEATURE_KEY}")
- API: @Profile("${FEATURE_GROUP}")

**Common**
- Model: packages/common/src/main/kotlin/org/vechain/indexer/${FEATURE_GROUP}/${MODEL}.kt
- Repository: packages/common/src/main/kotlin/org/vechain/indexer/${FEATURE_GROUP}/repository/${MODEL}Repository.kt

**Indexer**
- Processor: packages/indexer/src/main/kotlin/org/vechain/indexer/${FEATURE_GROUP}/${MODEL}Processor.kt
- Service: packages/indexer/src/main/kotlin/org/vechain/indexer/${FEATURE_GROUP}/${MODEL}Service.kt
- Config: packages/indexer/src/main/kotlin/org/vechain/indexer/${FEATURE_GROUP}/${MODEL}Config.kt
- Mongo config: packages/indexer/src/main/kotlin/org/vechain/indexer/${FEATURE_GROUP}/mongo/${MODEL}CollectionConfig.kt
- Add name to: packages/indexer/src/main/kotlin/org/vechain/indexer/IndexerNames.kt

**API**
- Controller: packages/api/src/main/kotlin/org/vechain/indexer/${FEATURE_GROUP}/${MODEL}Controller.kt
- Service: packages/api/src/main/kotlin/org/vechain/indexer/${FEATURE_GROUP}/${MODEL}Service.kt

**application.yaml wiring (packages/indexer/src/main/resources/application.yaml)**
- indexer.start-block.${FEATURE_KEY}: \${INDEXER_START_BLOCK_${KEY_ENV}:0}
- indexer.sync-block-batch-size.${FEATURE_KEY}: \${INDEXER_SYNC_BLOCK_BATCH_SIZE_${KEY_ENV}:500}
- indexer.version.${FEATURE_KEY}: \${VERSION_${KEY_ENV}:1}

**Terraform wiring**
- terraform/api/api.tf + terraform/devnet/api.tf: env vars
  - INDEXER_START_BLOCK_${KEY_ENV}
  - INDEXER_SYNC_BLOCK_BATCH_SIZE_${KEY_ENV}
  - VERSION_${KEY_ENV}
- terraform/api/environments/prod-*.yml + terraform/devnet/environments/devnet.yml:
  - spring_profile includes "${FEATURE_KEY}" (and/or "${FEATURE_GROUP}" depending on module)
  - start-block.${FEATURE_KEY}, sync-block-batch-size.${FEATURE_KEY}, version.${FEATURE_KEY}

**Local validation**
- ./gradlew :packages:common:compileKotlin :packages:indexer:compileKotlin :packages:api:compileKotlin
- make test-indexer && make test-api
- (deployed) scripts/run_api_schema_tests.sh (Schemathesis)
EOF

