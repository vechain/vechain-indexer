# Agent Instructions

## Testing

- **Never run e2e tests without explicit user permission.** The e2e tests (`make test-e2e` / `./gradlew :packages:e2e:test`) require external infrastructure and can be slow/destructive. Always ask before running them.
- Use `make test` to run unit/integration tests (excludes e2e).
- Individual package tests: `make test-api`, `make test-indexer`, `make test-common`.
