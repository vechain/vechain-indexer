# Time-Series Endpoint + Indexer Playbook

This playbook captures the cumulative time-series pattern used by `BlockUsage` and validator historic rewards.

Use this when adding:
- a global cumulative series, such as chain-wide metrics
- a per-entity cumulative series, such as validator, account, or contract charts

## Core Pattern

Store cumulative values in the indexer, then let the API choose a sampling resolution based on the requested time range.

This gives:
- one source of truth for all chart windows
- cheap delta/rate calculation on the client
- reusable read logic across endpoints

Current shared read-side primitives:
- `packages/api/src/main/kotlin/org/vechain/indexer/utils/TimeSeriesUtils.kt`
- `packages/common/src/main/kotlin/org/vechain/indexer/timeseries/TimeSeriesResolution.kt`

Current reference implementations:
- Global: `packages/indexer/src/main/kotlin/org/vechain/indexer/explorer/BlockUsageService.kt`
- Global API: `packages/api/src/main/kotlin/org/vechain/indexer/explorer/BlockUsageService.kt`
- Per-entity API: `packages/api/src/main/kotlin/org/vechain/indexer/validators/ValidatorService.kt`

## Data Model

For a cumulative time-series document:
- store `blockId`, `blockNumber`, `blockTimestamp`
- store cumulative counters, not per-bucket deltas
- store sampling markers for fixed resolutions:
  - `isHourly`
  - `isDaily`
  - `isWeekly`
  - `isMonthly`

This marker shape is acceptable for now because:
- the supported resolutions are fixed
- the read path is index-friendly
- it avoids server-side aggregation at request time

## Write-Side Rules

### Global series

For a single global stream:
- compute the next cumulative record from the previous record
- mark a record when its timestamp crosses an hourly/daily/weekly/monthly boundary

Reference:
- `packages/indexer/src/main/kotlin/org/vechain/indexer/explorer/BlockUsageService.kt`

### Per-entity series

For entity-scoped series:
- maintain sampling state per entity key, not globally
- determine `isHourly` / `isDaily` / `isWeekly` / `isMonthly` against the last emitted timestamp for that entity and resolution
- preload the latest sampled timestamps on startup so restarts do not break marker generation

Reference:
- `packages/indexer/src/main/kotlin/org/vechain/indexer/validator/ValidatorBlockService.kt`
- `packages/common/src/main/kotlin/org/vechain/indexer/validator/ValidatorBlockRepository.kt`

Important:
- do not let one entity’s timestamps affect another entity’s sampling markers
- if the series is partitioned, every cache and every lookup must also be partitioned

## Read-Side Rules

Never rely on exact timestamp equality for requested range boundaries.

Wrong:
- querying sampled points plus `{ blockTimestamp: startTimestamp }` / `{ blockTimestamp: endTimestamp }`

Correct:
1. choose a resolution from the requested time range
2. fetch sampled records inside `[startTimestamp, endTimestamp]`
3. fetch the latest record at or before `startTimestamp`
4. fetch the latest record at or before `endTimestamp`
5. merge, dedupe, and sort

Use:
- `TimeSeriesUtils.selectResolution(...)`
- `TimeSeriesUtils.getBookendedRecords(...)`

This ensures cumulative charts remain continuous even when sampled points are sparse.

## Shared Resolution Policy

Current shared policy in `TimeSeriesUtils.selectResolution(...)`:
- `<= 4_000s` -> `RAW`
- `<= 700_000s` -> `HOURLY`
- `<= 6_000_000s` -> `DAILY`
- `<= 35_000_000s` -> `WEEKLY`
- `> 35_000_000s` -> `MONTHLY`

If a new endpoint needs different thresholds, change the shared selector intentionally rather than forking the logic in one service.

## Repository Pattern

Each time-series repository should expose:
- raw range query
- sampled range queries per resolution
- latest-before-or-at timestamp lookup

### Global series example

Required methods:
- `findAllInTimestampRange(startTimestamp, endTimestamp)`
- `findHourlyInTimestampRange(startTimestamp, endTimestamp)`
- `findDailyInTimestampRange(startTimestamp, endTimestamp)`
- `findWeeklyInTimestampRange(startTimestamp, endTimestamp)`
- `findMonthlyInTimestampRange(startTimestamp, endTimestamp)`
- `findFirstByBlockTimestampLessThanEqualOrderByBlockTimestampDesc(blockTimestamp)`

Reference:
- `packages/common/src/main/kotlin/org/vechain/indexer/explorer/repository/BlockUsageRepository.kt`

### Per-entity series example

Required methods:
- same raw/sampled range queries, but filtered by entity key
- latest-before-or-at lookup filtered by entity key and any fixed status constraint

Reference:
- `packages/common/src/main/kotlin/org/vechain/indexer/validator/ValidatorBlockRepository.kt`

## Indexing Pattern

### Global series

Create:
- `blockTimestamp`
- `blockNumber` for rollback / latest-record access
- one compound index per sampling marker:
  - `isHourly + blockTimestamp`
  - `isDaily + blockTimestamp`
  - `isWeekly + blockTimestamp`
  - `isMonthly + blockTimestamp`

Reference:
- `packages/indexer/src/main/kotlin/org/vechain/indexer/explorer/BlockUsageCollectionConfig.kt`

### Per-entity series

Create:
- raw query index on `entityKey + blockTimestamp`
- one compound sampled index per resolution:
  - `isHourly + fixedFilters + entityKey + blockTimestamp`
  - `isDaily + fixedFilters + entityKey + blockTimestamp`
  - `isWeekly + fixedFilters + entityKey + blockTimestamp`
  - `isMonthly + fixedFilters + entityKey + blockTimestamp`

Reference:
- `packages/indexer/src/main/kotlin/org/vechain/indexer/validator/ValidatorBlockCollectionConfig.kt`

Keep the index field order aligned with the query predicate order.

## API Service Pattern

In the API service:
- validate timestamps
- call `TimeSeriesUtils.selectResolution(endTimestamp - startTimestamp)`
- use raw range queries for `RAW`
- use `getBookendedRecords(...)` for sampled resolutions

References:
- `packages/api/src/main/kotlin/org/vechain/indexer/explorer/BlockUsageService.kt`
- `packages/api/src/main/kotlin/org/vechain/indexer/validators/ValidatorService.kt`

## Controller / Docs Guidance

Document that:
- the endpoint returns cumulative values
- the client may need to compute deltas between consecutive points
- sampled responses include nearest records at or before the requested boundaries
- granularity is chosen automatically from the requested range

References:
- `packages/api/src/main/kotlin/org/vechain/indexer/explorer/BlockUsageController.kt`
- `packages/api/src/main/kotlin/org/vechain/indexer/validators/ValidatorController.kt`

## Test Checklist

Add tests for:
- cumulative write-side calculation
- marker generation when a boundary is crossed
- per-entity marker isolation
- resolution selection
- bookend insertion for arbitrary timestamps
- dedupe when a sampled point already lands on a boundary
- monthly path for large ranges

Current examples:
- `packages/api/src/test/kotlin/org/vechain/indexer/utils/TimeSeriesUtilsTest.kt`
- `packages/api/src/test/kotlin/org/vechain/indexer/explorer/BlockUsageServiceTest.kt`
- `packages/api/src/test/kotlin/org/vechain/indexer/validators/ValidatorServiceTest.kt`
- `packages/indexer/src/test/kotlin/org/vechain/indexer/explorer/BlockUsageServiceTest.kt`

## Pitfalls To Avoid

- Do not query start/end bookends by exact timestamp equality.
- Do not duplicate resolution-selection logic in multiple services.
- Do not use global sampling state for per-entity series.
- Do not forget the monthly path in the repository, indexes, docs, and tests.
- Do not preload hourly/weekly/monthly caches from the wrong marker field.
- Do not return sampled series without bookends for cumulative charts.

## Minimal Build / Verification Flow

After adding a new time-series endpoint/indexer:
- `make build`
- targeted API tests
- targeted indexer tests

For this pattern specifically, the most important checks are:
- compile `common` and `api`
- API tests for resolution selection and bookends
- indexer tests for cumulative accumulation and marker generation
