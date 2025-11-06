# Repository Guidelines

## Project Structure & Module Organization
Keep application code inside `packages/`: `indexer` holds the Spring Boot indexers, `api` exposes REST endpoints, `common` shares Kotlin utilities, `e2e` houses Gatling-style system tests, and `build` stores shared Gradle and Spotless configs. Infrastructure lives under `database/` (MongoDB compose files and backups), `load-testing/` (k6 stack), and `terraform/` for deployment templates. Docker assets and helper scripts reside in `images/` and `git-scripts/` respectively.

## Build, Test, and Development Commands
Run `make build` for a Spotless format pass plus Gradle builds of API and indexer jars. Use `make start` to stand up MongoDB and both services via Docker Compose, or `make db-all` when you only need the database locally. Primary tests run with `make test`; targeted suites use `make test-api`, `make test-indexer`, `make test-common`, or `make test-e2e`. To explore available shortcuts, execute `make help`.

## Coding Style & Naming Conventions
Kotlin sources must stay formatted by ktfmt Google style with 4-space block and continuation indents; ensure `./gradlew spotlessApply` runs cleanly before committing. Keep package names lowercase, classes in `PascalCase`, functions in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Prefer constructor injection and Spring annotations already used across the repo.

## Testing Guidelines
All Gradle test tasks run on JUnit Platform and automatically wire Jacoco reports; keep coverage meaningful enough for the aggregated badges to remain green. Name Kotlin test files with the `SomethingTest.kt` suffix and align fixtures under `src/test/resources`. End-to-end runs (`make test-e2e`) spin up Docker infrastructure, so clean up with `make load-test-clean` if runs abort.

## Commit & Pull Request Guidelines
Follow the existing history: concise, imperative titles with optional type prefixes (e.g., `refactor: migrate to new indexer-core interface`) and reference the PR number in parentheses when applicable. Describe problem, solution, and verification in the PR body, link tracking issues, and attach screenshots or logs when they clarify API or UI changes. Ensure formatters and tests pass locally before requesting review.

## Environment & Operations Tips
Copy `.env.example` files inside each package when running outside IntelliJ; the defaults target Dockerized services on localhost. Use `make db-backup` and `make db-restore` to manage Mongo snapshots stored in `database/backups/`. For load testing, adjust `load-testing/docker-compose.yml` environment variables—particularly `BASE_URL`—before invoking `make load-test`.
