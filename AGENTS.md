# Repository Guidelines

## Project Structure & Module Organization
Keep application code inside `packages/`: `indexer` holds the Spring Boot indexers, `api` exposes REST endpoints, `common` shares Kotlin utilities, `e2e` houses Gatling-style system tests, and `build` stores shared Gradle and Spotless configs. Infrastructure lives under `database/` (MongoDB compose files, backups, and the `restore/` collection-copy tooling) and `terraform/` for deployment templates. Docker assets and helper scripts reside in `images/` and `git-scripts/` respectively.

## Build, Test, and Development Commands
Run `make build` for a Spotless format pass plus Gradle builds of API and indexer jars. Use `make start` to stand up MongoDB and both services via Docker Compose, or `make db-all` when you only need the database locally. Primary tests run with `make test`; targeted suites use `make test-api`, `make test-indexer`, `make test-common`, or `make test-e2e`. To explore available shortcuts, execute `make help`.
Always run `make build` after making a code change to check whether it builds and the format the code.

## Coding Style & Naming Conventions
Kotlin sources must stay formatted by ktfmt Google style with 4-space block and continuation indents; ensure `./gradlew spotlessApply` runs cleanly before committing. Keep package names lowercase, classes in `PascalCase`, functions in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Prefer constructor injection and Spring annotations already used across the repo.

## Testing Guidelines
All Gradle test tasks run on JUnit Platform and automatically wire Jacoco reports; keep coverage meaningful enough for the aggregated badges to remain green. Name Kotlin test files with the `SomethingTest.kt` suffix and align fixtures under `src/test/resources`. End-to-end runs (`make test-e2e`) spin up Docker infrastructure, so clean up with `make clean` if runs abort.

## New Indexer + API Playbook (Default: Versioned)
When adding a new feature indexer and endpoint, prefer copying an existing implementation and editing it in place (e.g. `accounts/AccountOverview*` or `contracts/Contract*`). Keep the flow consistent across `common` → `indexer` → `api` → config wiring.

### Common (`packages/common`)
- Model: `@Document`, `@JsonView(Views.Public::class)`, `@JsonInclude(JsonInclude.Include.NON_NULL)`.
- Versioned default: implement `VersionedDocument` and add a matching `*Archive : Archive<T>`.
- Repository: add `*Repository : BaseIndexedRepository<Model, String>` and put it in `.../repository/`.

### Indexer (`packages/indexer`)
- `IndexerNames` (in `common` package): add a nested object with `NAME` and `COLLECTION` constants for the new indexer.
- `*Service`: constructor-inject `Repository`, `ArchiveService`, `TargetedPruner`; expose `processBlock/processEvents` and `save(...)` via `saveVersionedDocuments`. Keep business logic isolated here.
- `*Processor`: extend `BaseStatefulProcessor` for versioned storage (rollback + archive/pruner support). Call `service.process*` then `service.save` when lists are non-empty.
- `*Config`: wire `ArchiveService`, `TargetedPruner`, and `IndexerFactory().build()` settings (start block, batch size, included data).
- `mongo/*CollectionConfig`: implement `CollectionConfig` version check + indexes. Add compound indexes that match API query patterns.

### API (`packages/api`)
- `*Service`: query repositories only; keep business logic minimal.
- `*Controller`: copy offset pagination patterns from existing controllers using `PaginationUtils.toPageable(...)` and return `PaginatedResponse` via `paginatedResponse(...)`.
- Time ranges: validate with `TimeValidationUtils.validateTimestamps(...)`.

### Config + Terraform wiring
- `packages/indexer/src/main/resources/application.yaml`:
  - Add `indexer.start-block.<key>`, `indexer.sync-block-batch-size.<key>`, `indexer.version.<key>`.
- Terraform:
  - Add env vars in `terraform/api/api.tf`.
  - Add the new keys and Spring profiles in `terraform/api/environments/*.yml`.

### Triggering an Indexer Resync
To force an indexer to drop its collection and re-index from the start block, increment its deployed version number only in:
- **Deployed (prod)**: `terraform/api/environments/prod-blue.yml` and `terraform/api/environments/prod-green.yml` under `indexer.version.<key>` for both `main` and `test` net sections.

Keep local defaults at `1`: do not bump `indexer.version.<key>` fallback values in `packages/indexer/src/main/resources/application.yaml`, and do not bump `VERSION_*` values in `packages/indexer/.env.example`. Each prod environment file has separate version entries for mainnet and testnet — bump both. The version value must be higher than the currently deployed value; the indexer compares its stored version against the configured one and resyncs when they differ.

### Validation (local)
- Compile/build: `make build`
- Targeted tests: `make test-indexer`, `make test-api`
- Schema tests (deployed env): `packages/api/scripts/run_api_schema_tests.sh` (Schemathesis runner; see `README.md`)

More detailed templates and copy/paste snippets live in `notes/indexer-api-playbook.md`.

## Indexer Performance Guidelines

### CRITICAL: 1 Indexer = 1 Collection
Each indexer MUST map to exactly one MongoDB collection. Never create multiple collections for a single indexer. The backup, restore, and rollback mechanisms all operate at the collection level and assume a 1:1 relationship between an indexer and its collection. Creating multiple collections for one indexer breaks rollback consistency (partial rollbacks), backup integrity (collections can drift out of sync), and restore correctness. If your data model seems to require multiple collections, split it into separate indexers instead. This is a hard rule with no exceptions.
Indexer code must only access its own collection. Do not inject, call, or query another indexer's repository, collection, Mongo template query, or service from inside an indexer. Cross-indexer dependencies are a huge no-no and should be treated as an architectural violation, not a trade-off to make casually.
If data seems to require reading another indexer's collection, stop and redesign the flow. Prefer deriving it from on-chain events, reshaping the owning indexer's document, or introducing a separate dedicated indexer with its own collection. Do not solve it by wiring one indexer to another indexer's repository.
The only allowed exception is a narrow downstream-derived pattern with an explicit `.dependsOn(...)` relationship. In that case, the downstream indexer may read the upstream collection only when the dependency is one-way, the downstream document is clearly derived from upstream data, and rollout/versioning/resync are coordinated across both indexers.
This exception still carries coupling and rollback risk. It is not a normal implementation option, not a shortcut for convenience, and not permission to build chains of indexers reading each other freely.
This applies even to read-only aggregations, helper lookups, "just one query", or cases where the dependency feels obvious. Those shortcuts create coupling, ordering constraints, rollout risk, and rollback inconsistency between indexers unless they follow the explicit downstream exception above.
Processor classes should separate processing and persisting. This is generally in the form of a `processEvents` or `processBlock` method that returns a list of documents to be saved, and a `save` method that handles the actual persistence. This keeps the processing logic decoupled from the database and allows for better testing and flexibility.
Save functions should be covered by a Transactional annotation to ensure that all writes succeed or fail together.

### On-Chain Events Are the Only Preferred Data Source
Indexers should consume on-chain events (logs) as their data source. The following alternative data sources carry significant performance implications and should only be used as a last resort:
- **External API calls** — introduce latency, rate limits, and external failure modes into the indexing pipeline.
- **Smart contract calls** — require RPC calls for each block, adding load and slowing sync.
- **Dependent indexers** — one indexer reading from another indexer's collection, repository, or service creates coupling and ordering dependencies and is strongly discouraged. Only explicit downstream `.dependsOn(...)` exceptions should be considered, and they must be justified and coordinated operationally.
- **`callDataClauses`** — add complexity and performance overhead to block processing.

There are existing examples of these patterns in this repository and they are not always incorrect — some are justified for specific use cases. However, do not treat them as templates to copy freely. If any of these patterns seem necessary, the contributor should be challenged to confirm there is no viable on-chain events alternative and should understand the performance trade-offs before proceeding.

## API Performance Guidelines

### Data Shape Must Match Query Shape
Indexer collections should be pre-shaped to match the API queries they serve. Ideally each API call results in a single, simple database lookup — not aggregation pipelines or multi-step transformations. If a contributor proposes an endpoint that requires complex aggregations or joining data across collections, challenge whether a dedicated indexer/collection that pre-computes the needed shape would be more appropriate. This is not an absolute rule, but deviations should be consciously justified with an understanding of the performance implications.

### One API Call = One Database Query
An API endpoint should not make multiple sequential repository calls (e.g., fetch a document, then use a value from it to query a second collection). If an endpoint needs data from multiple collections, that is a strong signal the data model should be restructured — either by reshaping an existing indexer's output or by creating a new indexer that pre-joins the data.

### Focused Endpoints Over Flexible Ones
Avoid endpoints with many optional filter parameters. An endpoint that accepts 8 optional query params to cover every possible filtering combination is hard to optimise and hard to index. Challenge contributors: does the consumer actually need all these filters? Prefer splitting into multiple focused endpoints that each do one thing well over a single endpoint that does many things poorly.

### Index Coverage Without Bloat
All queries must have some level of index coverage — no query should trigger a full collection scan. However, do not create a dedicated compound index for every query permutation. Strike a pragmatic balance: cover the common patterns, look for redundant or overlapping indexes, and keep index count reasonable. The MongoDB Atlas Performance Advisor can be useful but take its recommendations with a large grain of salt — it tends to suggest too many indexes. All indexes are defined in `*CollectionConfig` files in the codebase; that is the single source of truth.

### Avoid Count Operations
`countDocuments()` is expensive on large collections and should be strongly discouraged. Prefer `estimatedDocumentCount()` where an exact count is not required, but remember it cannot accept a query filter and the result must be adjusted for non-data records (e.g., `__checkpoint__` documents). When a count operation is truly unavoidable, it is a strong candidate for caching using the existing Caffeine / `@Cacheable` pattern — register the cache in `CacheConfig.CACHE_NAMES` and add configuration in `application.yaml`.

### Pagination Is Required
Never return unbounded result sets. Use the existing pagination utilities:
- **Offset pagination** for filtered queries that operate on a bounded subset of data.
- **Cursor-based pagination** for queries that operate on an entire collection with millions of records (e.g., richlist rankings). The codebase already has a cursor-based pagination implementation — use it rather than building a new one.

### Endpoints Own Their Cache TTL
Every endpoint declares how long CloudFront and clients may reuse its response, next to its
`@GetMapping`:

```kotlin
@GetMapping("/level-overview")
@CacheFor(CachePolicy.HOURLY)
open fun getLevelOverviews(...): List<GMLevelOverview> = ...
```

`CacheControlAdvice` writes the `Cache-Control`, so a handler returns its body as normal —
no `ResponseEntity` wrapping. The default CloudFront behaviour uses the `origin-controlled`
cache policy, which is the one whose `min_ttl < default_ttl < max_ttl` and therefore the one
that obeys the origin. Nothing in Terraform pins an API TTL any more.

Pick the coarsest window the data tolerates, from the tiers in `CachePolicy`: `VOLATILE`
(moves with the head — clients revalidate, shared caches hold it for a block), `MINUTE`,
`TEN_MINUTES`, `HOURLY`, `DAILY`. Prefer an existing tier over inventing a number.

Two escapes, for a TTL only the response knows. Both need `@CacheFor` anyway — it is the
floor that applies if the call is missed:

- `cachedByAge(settledAt, body)` — grants the age of the newest thing the response can cover,
  capped at a year, so nothing outlives the span it has already been stable and a reorg can only
  poison an entry for as long as the block was on chain. Pass the block a page settled at
  (`BlockController`, `GET /transactions/{txId}`) or the instant a `startTimestamp`/`endTimestamp`
  window closed. A window whose end is now — or in the future, which the validators allow — grades
  to nothing and stays volatile; without that, rows keep landing inside a window whose cache key
  never changes, which is the one request shape that reliably serves stale data.
- `cachedFor(policy, body)` — grants a policy chosen per request. The `historic/{range}`
  endpoints take theirs from `TimeRangePreset`, since a year-wide series tolerates far more
  staleness than an hour-wide one.

Errors never inherit an endpoint's window: an exception handler's return type carries no
`@CacheFor`, and the advice forces `VOLATILE` on any non-2xx it does see.

`CacheControlCoverageTest` fails the build on a `@GetMapping` without a `@CacheFor`. It reads
bytecode rather than a Spring context, so an endpoint cannot hide behind its `@Profile`.

### Conformance Testing
When making API changes or changes that could affect performance, run the API conformance pipeline (`.github/workflows/api-conformance-tests.yml`). This is a manually triggered workflow. Run it first against the **dead** environment (the inactive side of our blue/green deployment), validate the results, and only then proceed with the DNS switch to make it live. This is strongly encouraged for all API and performance-related changes.

The dead colour is reachable only through its own CloudFront distributions, at `mainnet.dead.veworld.vechain.org` and `testnet.dead.veworld.vechain.org`. The `*.dead.prod.veworld.vechain.org` records behind them are ALB origins that admit CloudFront traffic and nothing else, so pointing a suite at one times out. The `dead` workspace in `terraform/cloudfront` owns that front door.

## Commit & Pull Request Guidelines
Follow the existing history: concise, imperative titles with optional type prefixes (e.g., `refactor: migrate to new indexer-core interface`) and reference the PR number in parentheses when applicable. Describe problem, solution, and verification in the PR body, link tracking issues, and attach screenshots or logs when they clarify API or UI changes. Ensure formatters and tests pass locally before requesting review.

### Prose Bloat Is a Merge Blocker
[.github/workflows/pr-bloat.yml](.github/workflows/pr-bloat.yml) fails a PR on a description over 240 words (or under 10), a tool-attribution trailer, or a comment block that outweighs the code it documents (>1:1 against attached added-code lines; 10 lines absolute; a top-of-file header measures against the whole file). Comment density and long markdown paragraphs are advisory; `**/*.md` never blocks. `make check-pr-bloat` runs it locally. Bypass is the `verbose-ok` label, which anyone including the author may apply. Thresholds are env-overridable in [check_pr_bloat.py](.github/workflows/scripts/check_pr_bloat.py).

Write reviewer-facing prose at final length — don't draft long and trim. The budget is the target, not a limit to approach.

- **PR descriptions:** what changed and why, in two or three sentences. Skip `## Summary` / `## Test plan` scaffolding unless there is genuinely something new to test. No tool-attribution trailers.
- **Comments:** add one only where the WHY is non-obvious — a hidden constraint, an invariant, a workaround. A comment must not outweigh the code it documents; on a one-line field addition, that means no comment. KDoc counts.
- Don't restate what the code does, what a technical term already implies (a reader who knows `checkpoint` doesn't need "marks progress"), or what a linked design doc already says.
- Don't explain what something does _not_ do, or where data does _not_ flow. State what is; the reader can see the absence.
- **But keep visual/semantic bridges.** Mapping something observable to what it means ("dashed orange line = `EcsTaskCpuHigh` threshold (80%)") is real information, not padding.
- **Design notes:** one to three sentences per phase or status entry. "Why X over Y" rationale belongs in the module README or `notes/`, not stacked above the code.

### One Deploy Applies Every Stack
[deploy.yml](.github/workflows/deploy.yml) is the single apply path for a deployed environment. It takes `environment` (only `prod` exists today), `network` and `colour`, and applies, in dependency order:

1. `terraform/vpc`, scope `full` — VPC, Route53, ECR, Atlas access, the CloudFront WAFs
2. `terraform/observability` then `terraform/observability-grafana` — in parallel with the VPC stack; `terraform/api` reads both stacks' outputs
3. `terraform/cloudfront` — `shared`, `staging`, `prod`, `dead`, which is a dependency chain
4. `terraform/api` for the target colour, image tags resolved per service
5. `terraform/vpc`, scope `dead-records` — after the application, so the dead records name the ALB it just moved

Every stack plans and applies on every run, so one with no change is a no-op. This replaced the separate shared-infra and observability dispatches: no stack is left for an operator to remember.

Live colours still come from DNS rather than operator input, so a cutover cannot be reverted by a stale value typed into a form, and both VPC applies serialize on the `terraform-vpc-apply` concurrency group.

**Merging still applies nothing.** [shared-infra.yml](.github/workflows/shared-infra.yml) enforces that for the colour-agnostic stacks. Touch `terraform/vpc/**` or `terraform/cloudfront/**` and it blocks the PR until someone applies the `shared-infra-ack` label, posts a sticky comment, and runs the `terraform plan` that no other check covers — read it in the job summary before approving, because the next deploy applies it. Each stack plans separately, since only `terraform/vpc` needs the live-colour lookup.

`terraform/cloudfront` holds six CloudFront distributions across four workspaces — two live, two idle continuous-deployment canaries against the same prod origins, and two fronting the dead colour for pre-cutover testing. Each workspace's plan lands in the job summary before it applies. The gate plans every workspace so you can see which a merge leaves dirty, planning one that does not exist yet against empty local state. [Its README](terraform/cloudfront/README.md) records the detail.

The VPC apply is scoped, because DNS records live in it and a cutover *is* a `terraform apply`. [deploy-colour-agnostic-infra.yml](.github/workflows/deploy-colour-agnostic-infra.yml) takes `scope`: `records` for a cutover (all four records — the dead pair must follow a swap), `dead-records` and `full` for the two passes a deploy makes. Leave a cutover untargeted and it applies every pending change in the stack as a side effect of switching DNS.

The review-time plan needs the `AWS_OIDC_ROLE_ARN` variable, pointed at the **read-only** `veworld-indexer-github-actions-prod-plan` role that `terraform/vpc` declares — not the deploy role, because the plan executes PR-controlled Terraform. Unset, the plan step skips rather than falling back to write credentials. The SSH agent for private module sources is still exposed to that code; keep module sources under review.

## Environment & Operations Tips
Copy `.env.example` files inside each package when running outside IntelliJ; the defaults target Dockerized services on localhost. Use `make db-backup` and `make db-restore` to manage whole-database Mongo snapshots stored in `database/backups/`. For targeted collection-level copy between two live clusters, run `make db-copy-collections` (see `database/restore/README.md`).
