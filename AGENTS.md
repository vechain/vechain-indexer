# Repository Guidelines

## Project Structure & Module Organization
Keep application code inside `packages/`: `indexer` holds the Spring Boot indexers, `api` exposes REST endpoints, `common` shares Kotlin utilities, `e2e` houses Gatling-style system tests, and `build` stores shared Gradle and Spotless configs. Infrastructure lives under `database/` (MongoDB compose files and backups), `load-testing/` (k6 stack), and `terraform/` for deployment templates. Docker assets and helper scripts reside in `images/` and `git-scripts/` respectively.

## Build, Test, and Development Commands
Run `make build` for a Spotless format pass plus Gradle builds of API and indexer jars. Use `make start` to stand up MongoDB and both services via Docker Compose, or `make db-all` when you only need the database locally. Primary tests run with `make test`; targeted suites use `make test-api`, `make test-indexer`, `make test-common`, or `make test-e2e`. To explore available shortcuts, execute `make help`.
Always run `make build` after making a code change to check whether it builds and the format the code.
When API endpoints, OpenAPI annotations, or the canonical API profile source in `packages/api/.env.example` change, run `make dd-refresh-generated` and commit the resulting JSON updates in `metrics/datadog/`.

## Coding Style & Naming Conventions
Kotlin sources must stay formatted by ktfmt Google style with 4-space block and continuation indents; ensure `./gradlew spotlessApply` runs cleanly before committing. Keep package names lowercase, classes in `PascalCase`, functions in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Prefer constructor injection and Spring annotations already used across the repo.

## Testing Guidelines
All Gradle test tasks run on JUnit Platform and automatically wire Jacoco reports; keep coverage meaningful enough for the aggregated badges to remain green. Name Kotlin test files with the `SomethingTest.kt` suffix and align fixtures under `src/test/resources`. End-to-end runs (`make test-e2e`) spin up Docker infrastructure, so clean up with `make load-test-clean` if runs abort.

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

### Validation (local)
- Compile/build: `make build`
- Targeted tests: `make test-indexer`, `make test-api`
- Schema tests (deployed env): `scripts/run_api_schema_tests.sh` (Schemathesis runner; see `README.md`)

More detailed templates and copy/paste snippets live in `notes/indexer-api-playbook.md`.

## Commit & Pull Request Guidelines
Follow the existing history: concise, imperative titles with optional type prefixes (e.g., `refactor: migrate to new indexer-core interface`) and reference the PR number in parentheses when applicable. Describe problem, solution, and verification in the PR body, link tracking issues, and attach screenshots or logs when they clarify API or UI changes. Ensure formatters and tests pass locally before requesting review.

## Environment & Operations Tips
Copy `.env.example` files inside each package when running outside IntelliJ; the defaults target Dockerized services on localhost. Use `make db-backup` and `make db-restore` to manage Mongo snapshots stored in `database/backups/`. For load testing, adjust `load-testing/docker-compose.yml` environment variables—particularly `BASE_URL`—before invoking `make load-test`.
