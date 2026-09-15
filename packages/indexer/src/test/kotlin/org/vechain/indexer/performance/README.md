# Performance Tests

Performance tests for VeChain indexers. Tests fetch real blocks from mainnet and measure throughput, memory usage, and CPU utilization with detailed profiling.

## Quick Start

```bash
# Start PostgreSQL
make pg-up

# Run all performance tests (takes ~6 minutes)
./gradlew :packages:indexer:test --tests "org.vechain.indexer.performance.*.*PerformanceTest"

# Run single indexer test
./gradlew :packages:indexer:test --tests "org.vechain.indexer.performance.accounts.AccountsProcessorPerformanceTest"
```

## What Gets Tested

All 11 indexers with 1000 mainnet blocks (23430500-23431499):

- ✅ Accounts
- ✅ BlockUsage (starts from block 0)
- ✅ Delegation
- ✅ History
- ✅ StargateToken
- ✅ TokenReward (Stargate Rewards)
- ✅ Transaction
- ✅ Validator
- ✅ ValidatorBlock
- ✅ VthoGeneratedByBlock

## Output

Each test prints detailed metrics:

```
Performance Test Results: AccountsIndexer
================================================================================
Block Range: 23430500 - 23431499 (1000 blocks)
Duration: 164ms
Blocks Per Second: 6097.56
Memory Usage:
  Start: 58 MB
  End: 188 MB
  Peak: 188 MB
  Increase: 130 MB
CPU Usage:
  Average Load: 28.47%
  Peak Load: 53.17%
================================================================================

DETAILED PROFILING RESULTS
Operation                                   Calls     Total (ms)     Avg (ms)
------------------------------------------------------------------------------
Total Indexing Time                             1        161.774      161.774
    AccountsProcessor.process                   1         76.251       76.251
      AccountsService.save                      1         70.141       70.141
      AccountsService.processBlock              1          6.083        6.083
        - getNewAccounts                        1          4.383        4.383
        - updateAccountsInfo                    1          1.679        1.679
```

**Plus CSV export** with detailed timing breakdowns for every operation.

## Prerequisites

**Required:**
- PostgreSQL running on `localhost:5432` with credentials `indexer:password` (`make pg-up`)
- Internet connection to `https://mainnet.vechain.org`

## Test Structure

```
performance/
├── BasePerformanceTest.kt           # Shared infrastructure
├── DetailedProfiler.kt              # Profiling utility
├── PerformanceMetrics.kt            # Metrics data classes
├── accounts/
│   ├── AccountsProcessorPerformanceTest.kt
│   └── ProfiledAccountsService.kt
├── delegation/
│   ├── DelegationProcessorPerformanceTest.kt
│   └── ProfiledDelegationService.kt
└── ... (one folder per indexer)
```

## How It Works

1. **Fetch Blocks** - Downloads 1000 real blocks from mainnet
2. **Clear Database** - Ensures clean test environment
3. **Profile Execution** - Wraps services to measure every operation
4. **Collect Metrics** - Tracks time, memory, CPU
5. **Report Results** - Prints summary + exports CSV

## Key Features

- **Zero Configuration** - Tests use `@ActiveProfiles` (no manual setup needed)
- **Disabled by Default** - Tests marked with `@Disabled` to prevent accidental execution in CI/CD
- **Explicit Execution** - Run with `--tests "*PerformanceTest"` to execute despite `@Disabled`
- **Detailed Profiling** - Millisecond-precision timing for every method
- **Real Data** - Uses actual mainnet blocks for accuracy
- **CSV Export** - Detailed profiling data for analysis

## Profiling Techniques

Different strategies for different code patterns:

**1. Extension (preferred):**
```kotlin
class ProfiledAccountsService(...) : AccountsService(...) {
    override fun processBlock(...) {
        profiler.time("processBlock") { super.processBlock(...) }
    }
}
```

**2. Reflection (for final methods):**
```kotlin
private val processEventsMethod = service.javaClass.getDeclaredMethod("processEvents", ...)
processEventsMethod.invoke(service, ...)
```

**3. Composition (for final classes):**
```kotlin
class ProfiledTransactionService(private val repository: TransactionWriteRepository) {
    fun processBlockTransactions(...) {
        val actualService = TransactionService(repository)
        profiler.time("process") { actualService.processBlockTransactions(...) }
    }
}
```

## Configuration

All test config is in `src/test/resources/application-test.properties`:

```properties
# Thor Node
thor.url=https://mainnet.vechain.org

# Start blocks (per indexer)
indexer.start-block.accounts=1000000
indexer.start-block.history=23430500
# ... etc

# Batch sizes
indexer.sync-block-batch-size.stargate=500
indexer.sync-block-batch-size.b3tr=500
```

## Notes

**@Disabled Annotations:**
All performance tests are marked with `@Disabled` to prevent them from running during normal test execution (`./gradlew test`). To run them, explicitly target them with `--tests "*PerformanceTest"` which overrides the `@Disabled` annotation.

**Block Usage Special Case:**
BlockUsage indexer must start from block 0 (not 23430500) due to cumulative data requirements.

**Memory & CPU:**
Tests track resource usage throughout execution, not just start/end.

## Troubleshooting

**PostgreSQL Connection Error:**
```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Check credentials match application-test.properties
# Default: jdbc:postgresql://localhost:5432/vechain as indexer:password
```

**OutOfMemory:**
```bash
# Increase heap size
./gradlew test --tests "*PerformanceTest" -Xmx4g
```

**Network Timeouts:**
```bash
# Increase timeout in application-test.properties
thor.timeout=30000
```

## See Also

- **[PERFORMANCE_TEST_RESULTS.md](../../../../../../../../PERFORMANCE_TEST_RESULTS.md)** - Full test results with analysis
- **[PR_SUMMARY.md](../../../../../../../../PR_SUMMARY.md)** - Performance summary table
