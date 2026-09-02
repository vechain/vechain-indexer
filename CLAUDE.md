# Agent Instructions

## Testing

- **Never run e2e tests without explicit user permission.** The e2e tests (`make test-e2e` / `./gradlew :packages:e2e:test`) require external infrastructure and can be slow/destructive. Always ask before running them.
- Use `make test` to run unit/integration tests (excludes e2e).
- Individual package tests: `make test-api`, `make test-indexer`, `make test-common`.

## Required Pre-Commit Scripts

Always run this script before committing or pushing any change, and commit any resulting file updates. CI enforces it and will fail if the generated files drift.

- `bash packages/api/scripts/refresh_token_registry.sh` — refreshes `packages/api/src/main/resources/token-registry/main.json` and `test.json` from the upstream registry.

If the script updates tracked files, include those updates in the same commit (or a follow-up `chore:` commit on the same branch) before pushing.

## Prose Bloat Is a Merge Blocker

`.github/workflows/pr-bloat.yml` fails a PR on a description over 240 words (or under 10), a tool-attribution trailer, or a comment block that outweighs the code it documents (>1:1 against attached added-code lines; 10 lines absolute; a top-of-file header measures against the whole file). Comment density and long markdown paragraphs are advisory; `**/*.md` never blocks. Run `make check-pr-bloat` locally. Bypass is the `verbose-ok` label, which anyone including the author may apply. Thresholds are env-overridable in `.github/workflows/scripts/check_pr_bloat.py`.

Write reviewer-facing prose at final length — don't draft long and trim. The budget is the target, not a limit to approach. See `AGENTS.md` "Commit & Pull Request Guidelines" for the full rules.

## Shared Infra Needs a Manual Apply

`terraform/vpc` (VPC, Route53, ECR, CloudFront WAFs) and `terraform/cloudfront` (the CloudFront distributions) are the colour-agnostic stacks. Merging a change to either applies nothing. `.github/workflows/shared-infra.yml` blocks any PR touching `terraform/vpc/**` or `terraform/cloudfront/**` until the `shared-infra-ack` label is applied, and plans the affected stack for review. After merge, run `.github/workflows/deploy-shared-infra.yml` — the only path that applies `terraform/vpc` in full (a DNS cutover applies Route53 records only), and it then applies `terraform/cloudfront`'s `shared`, `staging`, `prod` and `dead` workspaces in that order — see `terraform/cloudfront/README.md`. See `AGENTS.md` "Shared Infra Needs a Manual Apply".

## Dependencies

- The `indexer-core` library (`org.vechain:indexer-core`) source code is at https://github.com/vechain/indexer-core. Refer to it for interfaces like `Indexer`, `BlockIndexer`, the `Status` enum, and other core types.

## Swagger / OpenAPI Filters

Whenever a controller exposes a `@RequestParam` filter backed by an enum or a curated string list, the corresponding Swagger annotation under `packages/api/src/main/kotlin/org/vechain/indexer/docs/` must be kept in sync. The runtime validators (e.g. `ValidEventName`) often derive allowed values dynamically from the enum, but the Swagger `@Parameter(allowableValues = [...])` lists are hard-coded duplicates that drift silently.

- Source of truth for history filters: `HistoryEventName` (`packages/common/src/main/kotlin/org/vechain/indexer/history/HistoryEventName.kt`).
- When entries are added, removed, or renamed in such an enum, audit every annotation in `packages/api/src/main/kotlin/org/vechain/indexer/docs/` that lists `allowableValues` (e.g. `EventNameParameter`, `TokenEventNameParameter`, `NftHistoryEventNameParameter`, `StargateTokenHistoryEventNameParameter`) and update them to match.
- Curated-subset annotations (Token / NFT / Stargate) only enumerate values relevant to that endpoint; only add a new value if it belongs to that subset.

## Project Guidelines

See `AGENTS.md` for detailed project structure, build commands, coding conventions, the new indexer/API playbook, and commit/PR guidelines. Read it before starting non-trivial work.
