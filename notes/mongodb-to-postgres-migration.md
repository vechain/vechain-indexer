# MongoDB to PostgreSQL Indexer Migration Guide

This guide documents the patterns for migrating indexers from MongoDB to PostgreSQL, based on the successful B3TR Actions migration.

## Architecture Overview

The migration moves from MongoDB's document-per-version model (separate collections for current + archives) to PostgreSQL's **versioned rows pattern** (single table with `is_current` flag).

```
MongoDB Pattern:                    PostgreSQL Pattern:
┌─────────────────────┐            ┌─────────────────────────────┐
│ Current Collection  │            │     Single Table            │
│   (documents)       │            │   (entity_id, version)      │
└─────────┬───────────┘            │                             │
          │                        │   is_current = true  (1 row)│
          ▼                        │   is_current = false (N rows)│
┌─────────────────────┐            └─────────────────────────────┘
│ Archive Collection  │
│   (old versions)    │
└─────────────────────┘
```

## Key Components to Migrate

For each indexer, you need to update these components:

| Layer | MongoDB | PostgreSQL |
|-------|---------|------------|
| Model | `@Document` annotation, `Archive` class | Plain data class, no `Archive` |
| Repository Interface | Extends `BaseIndexedRepository` | Extends `PostgresIndexedRepository` |
| Repository Impl | Spring Data MongoDB (auto-generated) | `PostgresVersionedRepository` base class |
| Processor | `BaseStatefulProcessor` | `BasePostgresProcessor` |
| Service | Uses `ArchiveService`, `saveVersionedDocuments()` | Uses `repository.saveAllVersioned()`, `PostgresPruner` |
| Config | Creates `ArchiveService`, `TargetedPruner` | Creates `PostgresPruner` |
| Schema | MongoDB `CollectionConfig` (indexes) | SQL schema file |

---

## Step-by-Step Migration

### Step 1: Create SQL Schema

Create a new SQL file in `packages/common/src/main/resources/db/`.

**Required columns:**
- `entity_id TEXT NOT NULL` - unique identifier for the entity
- `version INT NOT NULL` - version number (starts at 1)
- `is_current BOOLEAN NOT NULL DEFAULT true` - marks the active version
- `block_id TEXT NOT NULL` - block hash
- `block_number BIGINT NOT NULL` - block number for rollback/prune
- `block_timestamp BIGINT NOT NULL` - block timestamp
- `PRIMARY KEY (entity_id, version)` - composite key

**Required indexes:**
- Partial indexes for queries: `CREATE INDEX ... WHERE is_current = true`
- Block number index for rollback/prune: `CREATE INDEX ... ON table(block_number)`

**Example structure:**

```sql
CREATE TABLE IF NOT EXISTS my_entity_table (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    -- Domain-specific columns
    my_field TEXT NOT NULL,
    complex_data JSONB NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial index for current records
CREATE INDEX IF NOT EXISTS idx_my_entity_field_current
    ON my_entity_table (my_field) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_my_entity_block_number
    ON my_entity_table (block_number);
```

**Note:** The schema file will be automatically picked up by Spring's SQL initialization. The `application.yaml` uses a wildcard pattern (`classpath:db/*.sql`) to load all SQL files from the `db/` directory.

---

### Step 2: Update Model (`packages/common`)

**Remove:**
- `@Document(collection = "...")` annotation
- `@Id` annotation
- The separate `*Archive` data class

**Keep/Update:**
- Implement `VersionedDocument` interface
- `@JsonIgnore` on internal fields (`id`, `version`, `blockId`, `blockNumber`, `blockTimestamp`)
- `getDocumentId()` method

**Before (MongoDB):**

```kotlin
@Document(collection = "contracts")
data class Contract(
    @Id val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore override val version: Int,
    // Domain fields...
) : VersionedDocument {
    override fun getDocumentId(): String = address
}

@Document("contract_archives")
data class ContractArchive(@Id override val id: String, override val data: Contract) :
    Archive<Contract>
```

**After (PostgreSQL):**

```kotlin
data class Contract(
    @JsonIgnore val id: String,  // was @Id, renamed if needed
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    // Domain fields...
) : VersionedDocument {
    override fun getDocumentId(): String = id
}
// Delete the Archive class entirely
```

---

### Step 3: Update Repository Interface (`packages/common`)

**Before (MongoDB):**

```kotlin
interface MyRepository : BaseIndexedRepository<MyEntity, String> {
    fun findByField(field: String): MyEntity?
}
```

**After (PostgreSQL):**

```kotlin
interface MyRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<MyEntity>, existing: List<MyEntity>)
    fun findByField(field: String): MyEntity?
    // Add other query methods as needed
}
```

---

### Step 4: Create PostgreSQL Repository Implementation (`packages/common`)

Create a new file: `Postgres*Repository.kt`

Extend `PostgresVersionedRepository<T>` and implement your repository interface.

**Template:**

```kotlin
@Profile("your-profile")
@Repository
open class PostgresMyRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) : PostgresVersionedRepository<MyEntity>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    MyRepository {

    override fun tableName(): String = "my_entity_table"
    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String = """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        my_field, complex_data
    """.trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?::jsonb"

    override fun insertParams(doc: MyEntity): Array<Any?> = arrayOf(
        doc.id,
        doc.version,
        true, // is_current
        doc.blockId,
        doc.blockNumber,
        doc.blockTimestamp,
        doc.myField,
        doc.complexData?.let { objectMapper.writeValueAsString(it) },
    )

    override fun mapRow(rs: ResultSet): MyEntity {
        val complexData = rs.getString("complex_data")?.let {
            objectMapper.readValue(it, ComplexData::class.java)
        }
        return MyEntity(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            myField = rs.getString("my_field"),
            complexData = complexData,
        )
    }

    override fun saveAllVersioned(
        updated: List<MyEntity>,
        existing: List<MyEntity>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findByField(field: String): MyEntity? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE my_field = ? AND is_current = true
                """.trimIndent(),
                { rs, _ -> mapRow(rs) },
                field,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }
}
```

**Key patterns for query methods:**
- Always include `WHERE is_current = true` for current record queries
- Use `LIMIT ? OFFSET ?` for pagination
- Use `SliceImpl(content, pageable, hasNext)` for paginated results
- Use `?::jsonb` placeholder for JSONB columns
- Wrap single-result queries in try-catch for `EmptyResultDataAccessException`

**Pagination example:**

```kotlin
override fun findAllByField(field: String, pageable: Pageable): Slice<MyEntity> {
    val limit = pageable.pageSize + 1  // Fetch one extra to detect hasNext
    val offset = pageable.offset

    val results = jdbcTemplate.query(
        """
        SELECT * FROM ${tableName()}
        WHERE my_field = ? AND is_current = true
        ORDER BY some_column DESC
        LIMIT ? OFFSET ?
        """.trimIndent(),
        { rs, _ -> mapRow(rs) },
        field,
        limit,
        offset,
    )

    val hasNext = results.size > pageable.pageSize
    val content = if (hasNext) results.dropLast(1) else results

    return SliceImpl(content, pageable, hasNext)
}
```

---

### Step 5: Update Service (`packages/indexer`)

**Remove:**
- `ArchiveService` dependency
- `saveVersionedDocuments()` calls

**Add:**
- `PostgresPruner` dependency
- Direct `repository.saveAllVersioned(updated, existing)` calls

**Before (MongoDB):**

```kotlin
@Service
open class MyService(
    private val repository: MyRepository,
    private val archiveService: ArchiveService<MyEntity, MyArchive>,
    private val targetedPruner: TargetedPruner<MyEntity, MyArchive>,
) {
    open fun save(updated: List<MyEntity>) {
        saveVersionedDocuments(repository, archiveService, targetedPruner, updated)
    }
}
```

**After (PostgreSQL):**

```kotlin
@Service
open class MyService(
    private val repository: MyRepository,
    private val pruner: PostgresPruner,
) {
    open fun processEvents(events: List<IndexedEvent>): Pair<List<MyEntity>, List<MyEntity>> {
        val updated = mutableListOf<MyEntity>()
        val existing = mutableListOf<MyEntity>()
        
        // Process events, populating updated and existing lists
        // For each entity being updated:
        //   1. Look up existing record (from cache or repository)
        //   2. Create new version with incremented version number
        //   3. Add existing record to 'existing' list
        //   4. Add new record to 'updated' list
        
        return updated to existing
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<MyEntity>, existing: List<MyEntity>) {
        repository.saveAllVersioned(updated, existing)

        // Trigger targeted pruning for entities with prior versions
        if (updated.isNotEmpty()) {
            val latestBlock = updated.maxOf { it.blockNumber }
            val entityIds = existing.filter { it.version > 1 }.map { it.id }
            if (entityIds.isNotEmpty()) {
                pruner.run(latestBlock, entityIds)
            }
        }
    }
}
```

**Important:** The service now returns a `Pair<List<T>, List<T>>` from `processEvents()`:
- First list (`updated`): New versions to insert with `is_current = true`
- Second list (`existing`): Previous versions to mark as `is_current = false`

---

### Step 6: Update Processor (`packages/indexer`)

**Change:**
- Extend `BasePostgresProcessor` instead of `BaseStatefulProcessor`
- Remove `ArchiveService` from constructor

**Before (MongoDB):**

```kotlin
@Component
open class MyProcessor(
    repository: MyRepository,
    archiveService: ArchiveService<MyEntity, MyArchive>,
    private val service: MyService,
    indexerVersionService: IndexerVersionService,
) : BaseStatefulProcessor(repository, archiveService, indexerVersionService, IndexerNames.MY_INDEXER)
```

**After (PostgreSQL):**

```kotlin
@Component
open class MyProcessor(
    repository: MyRepository,
    private val service: MyService,
    indexerVersionService: IndexerVersionService,
) : BasePostgresProcessor(repository, indexerVersionService, IndexerNames.MY_INDEXER) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        
        val (updated, existing) = service.processEvents(entry.events())
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
```

---

### Step 7: Update Config (`packages/indexer`)

**Remove:**
- `ArchiveService` bean
- MongoDB `TargetedPruner` bean

**Add:**
- `PostgresPruner` bean

**Before (MongoDB):**

```kotlin
@Configuration
open class MyConfig {
    @Bean
    open fun myArchiveService(mongoTemplate: MongoTemplate) =
        ArchiveService(MyArchive::class.java, mongoTemplate)

    @Bean
    open fun myTargetedPruner(
        archiveService: ArchiveService<MyEntity, MyArchive>,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ) = TargetedPruner(archiveService, pruneBlockDepth)
    
    @Bean
    open fun myIndexer(
        // ...
        myTargetedPruner: TargetedPruner<MyEntity, MyArchive>,
    ): Indexer = IndexerFactory()
        // ...
        .pruner(myTargetedPruner)
        .build()
}
```

**After (PostgreSQL):**

```kotlin
@Configuration
open class MyConfig {
    @Bean
    open fun myPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner = PostgresPruner(
        jdbcTemplate,
        namedJdbcTemplate,
        pruneBlockDepth,
        "my_entity_table",  // Must match tableName() in repository
    )
    
    @Bean
    open fun myIndexer(
        // ...
        myPruner: PostgresPruner,
    ): Indexer = IndexerFactory()
        // ...
        .pruner(myPruner)
        .build()
}
```

---

### Step 8: Delete MongoDB Collection Config

Delete the `mongo/*CollectionConfig.kt` file for this indexer. The SQL schema replaces it.

These files typically:
- Extended `CollectionConfig`
- Used `MongoTemplate` for collection/index management
- Had `@PostConstruct` methods for initialization

All of this is now handled by the SQL schema file.

---

### Step 9: Update Tests

Update test files to mock the new patterns:

**Service tests:**
- Remove `ArchiveService` mocks
- Mock `PostgresPruner` instead of `TargetedPruner`
- Verify `repository.saveAllVersioned()` is called with correct arguments
- Update assertions to check both `updated` and `existing` lists

**Processor tests:**
- Remove `ArchiveService` from constructor calls
- Update mock setup for `BasePostgresProcessor` pattern

**Example test update:**

```kotlin
// Before
private val archiveService: ArchiveService<MyEntity, MyArchive> = mockk(relaxed = true)
private val targetedPruner: TargetedPruner<MyEntity, MyArchive> = mockk(relaxed = true)

// After
private val pruner: PostgresPruner = mockk(relaxed = true)

// Before - verify save
verify { archiveService.archive(any()) }

// After - verify save
verify { repository.saveAllVersioned(any(), any()) }
```

---

## Reference Files

**Base classes to use:**
- `packages/common/src/main/kotlin/org/vechain/indexer/postgres/PostgresIndexedRepository.kt` - Interface for processors
- `packages/common/src/main/kotlin/org/vechain/indexer/postgres/PostgresVersionedRepository.kt` - Base class for repositories
- `packages/indexer/src/main/kotlin/org/vechain/indexer/BasePostgresProcessor.kt` - Base class for processors
- `packages/indexer/src/main/kotlin/org/vechain/indexer/pruner/PostgresPruner.kt` - Reusable pruner

**Example migrated indexer (use as template):**
- Model: `packages/common/src/main/kotlin/org/vechain/indexer/b3tr/action/AppAllTimeActionSummary.kt`
- Repository Interface: `packages/common/src/main/kotlin/org/vechain/indexer/b3tr/action/repository/AppAllTimeActionSummaryRepository.kt`
- Repository Impl: `packages/common/src/main/kotlin/org/vechain/indexer/b3tr/action/repository/PostgresAppAllTimeActionSummaryRepository.kt`
- Service: `packages/indexer/src/main/kotlin/org/vechain/indexer/b3tr/action/AppAllTimeActionSummaryService.kt`
- Processor: `packages/indexer/src/main/kotlin/org/vechain/indexer/b3tr/action/AppAllTimeActionSummaryProcessor.kt`
- Config: `packages/indexer/src/main/kotlin/org/vechain/indexer/b3tr/action/AppAllTimeActionSummaryConfig.kt`
- SQL Schema: `packages/common/src/main/resources/db/action-summaries-schema.sql`

---

## Checklist

For each indexer migration:

- [ ] Create SQL schema file with versioned table structure (auto-loaded via `classpath:db/*.sql`)
- [ ] Update model: remove `@Document`, `@Id`, delete `Archive` class
- [ ] Update repository interface: extend `PostgresIndexedRepository`
- [ ] Create `Postgres*Repository` implementation
- [ ] Update service: replace `ArchiveService` with `PostgresPruner`
- [ ] Update processor: extend `BasePostgresProcessor`
- [ ] Update config: replace `ArchiveService`/`TargetedPruner` beans with `PostgresPruner`
- [ ] Delete MongoDB `CollectionConfig`
- [ ] Update tests
- [ ] Run `make build` and `make test-indexer`/`make test-common`

---

## Indexers Remaining to Migrate

The following indexers still use MongoDB and will need migration:

- `Contract` - `packages/common/src/main/kotlin/org/vechain/indexer/contracts/`
- `AccountOverview` - `packages/common/src/main/kotlin/org/vechain/indexer/accounts/`
- `Validator` - `packages/common/src/main/kotlin/org/vechain/indexer/validator/`
- `VetBalance` - VET balance tracking
- `TotalAccounts` - Total accounts statistics
- `Delegation` - Delegation records
- `IndexedTransaction` - Transaction indexing
- `IndexedNft` - NFT indexing
- `IndexedHistoryEvent` - History events
- `IndexedTransferEvent` - Transfer events
- `FungibleTokenInteraction` - Fungible token interactions
- `StargateToken` - Stargate tokens
- `TokenReward` - Token rewards
- `NftOwnerBalance` - NFT owner balances
- `ProposalResult` - B3TR proposal results
- `VeVoteProposalResult` - VeVote proposal results

Each follows the same migration pattern documented above.

## Completed Migrations

- `GmNft` - GM NFTs (migrated to `b3tr_gm_nfts` table)
- `AppAllTimeActionSummary` - B3TR app all-time action summaries
- `AppDailyActionSummary` - B3TR app daily action summaries
- `AppRoundActionSummary` - B3TR app round action summaries
- `UserAllTimeActionSummary` - B3TR user all-time action summaries
- `UserDailyActionSummary` - B3TR user daily action summaries
- `UserRoundActionSummary` - B3TR user round action summaries

## Technical Notes

### Versioned Document Persistence Fix (2026-01-27)

The `PostgresVersionedRepository.saveAllVersioned()` method was updated to fix critical issues with intermediate version persistence and duplicate key handling.

**Problem:** When batch processing events across multiple blocks, intermediate versions were being lost:
- Block 100: entity v1 → v2
- Block 101: entity v2 → v3
- Block 102: entity v3 → v4

Only v4 was persisted; v2 and v3 were never saved, breaking rollback functionality.

**Root Cause:** The original implementation tried to UPDATE existing records (which don't exist in DB for cache-sourced versions) and had no ON CONFLICT handling for reprocessed blocks.

**Solution:** 
1. Changed from UPDATE to INSERT for `existing` records with `ON CONFLICT (entity_id, version) DO UPDATE SET is_current = false`
2. Added `insertParamsForExisting()` helper method that returns params with `is_current = false`
3. Both `existing` (intermediate) and `updated` (final) records are now explicitly INSERTed with appropriate `is_current` flags

**Impact:** All versioned PostgreSQL repositories now correctly persist intermediate versions, enabling proper rollback across multi-block batches.
