# Agent Instructions

## Testing

- **Never run e2e tests without explicit user permission.** The e2e tests (`make test-e2e` / `./gradlew :packages:e2e:test`) require external infrastructure and can be slow/destructive. Always ask before running them.
- Use `make test` to run unit/integration tests (excludes e2e).
- Individual package tests: `make test-api`, `make test-indexer`, `make test-common`.

## Dependencies

- The `indexer-core` library (`org.vechain:indexer-core`) source code is at https://github.com/vechain/indexer-core. Refer to it for interfaces like `Indexer`, `BlockIndexer`, the `Status` enum, and other core types.

## Project Guidelines

See `AGENTS.md` for detailed project structure, build commands, coding conventions, the new indexer/API playbook, and commit/PR guidelines. Read it before starting non-trivial work.
