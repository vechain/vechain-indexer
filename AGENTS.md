# Repository Guidelines

## Project Structure & Module Organization
Keep application code inside `packages/`: `indexer` holds the Spring Boot indexers, `api` exposes REST endpoints, `common` shares Kotlin utilities, `e2e` houses Gatling-style system tests, and `build` stores shared Gradle and Spotless configs. Infrastructure lives under `database/` (MongoDB compose files, backups, and the `restore/` collection-copy tooling) and `terraform/` for deployment templates. Docker assets and helper scripts reside in `images/` and `git-scripts/` respectively.

## Build, Test, and Development Commands
Run `make build` for a Spotless format pass plus Gradle builds of API and indexer jars. Use `make start` to stand up MongoDB and both services via Docker Compose, or `make db-all` when you only need the database locally. Primary tests run with `make test`; targeted suites use `make test-api`, `make test-indexer`, `make test-common`, or `make test-e2e`. To explore available shortcuts, execute `make help`.
Always run `make build` after making a code change to check whether it builds and the format the code.
When API endpoints, OpenAPI annotations, or the canonical API profile source in `packages/api/.env.example` change, run `make dd-refresh-generated` and commit the resulting JSON updates in `metrics/datadog/`.

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
  - Add env vars in `terraform/api/api.tf` and `terraform/devnet/api.tf`.
  - Add the new keys and Spring profiles in `terraform/api/environments/*.yml` and `terraform/devnet/environments/devnet.yml`.

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

### Conformance Testing
When making API changes or changes that could affect performance, run the API conformance pipeline (`.github/workflows/api-conformance-tests.yml`). This is a manually triggered workflow. Run it first against the **dead** environment (the inactive side of our blue/green deployment), validate the results, and only then proceed with the DNS switch to make it live. This is strongly encouraged for all API and performance-related changes.

## Commit & Pull Request Guidelines
Follow the existing history: concise, imperative titles with optional type prefixes (e.g., `refactor: migrate to new indexer-core interface`) and reference the PR number in parentheses when applicable. Describe problem, solution, and verification in the PR body, link tracking issues, and attach screenshots or logs when they clarify API or UI changes. Ensure formatters and tests pass locally before requesting review.

## Environment & Operations Tips
Copy `.env.example` files inside each package when running outside IntelliJ; the defaults target Dockerized services on localhost. Use `make db-backup` and `make db-restore` to manage whole-database Mongo snapshots stored in `database/backups/`. For targeted collection-level copy between two live clusters, run `make db-copy-collections` (see `database/restore/README.md`).
