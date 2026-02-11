# Indexer + API Playbook (Default: Versioned)

This repo adds most features as a versioned Mongo model (current state) with an archive collection (history) and a pruner to cap archive growth. The fastest path is always: copy an existing feature and edit.

Recommended references:
- Versioned pattern: `packages/common/src/main/kotlin/org/vechain/indexer/accounts/AccountOverview.kt`
- Indexer wiring: `packages/indexer/src/main/kotlin/org/vechain/indexer/accounts/AccountOverviewConfig.kt`
- Collection + indexes: `packages/indexer/src/main/kotlin/org/vechain/indexer/accounts/mongo/AccountOverviewCollectionConfig.kt`
- API patterns: `packages/api/src/main/kotlin/org/vechain/indexer/accounts/AccountsController.kt`
- Pagination helper: `packages/api/src/main/kotlin/org/vechain/indexer/utils/PaginationUtils.kt`
- Schema tests (Schemathesis): `scripts/run_api_schema_tests.sh`

## 1) Common module (`packages/common`)

### 1.1 Model (versioned default)
- Keep the Mongo `_id` as the logical entity ID (usually an address, composite hash, etc.).
- Implement `VersionedDocument` and include `blockId`, `blockNumber`, `blockTimestamp`, `version`.
- Add a matching archive model: `data class XArchive(@Id override val id: String, override val data: X) : Archive<X>`

Skeleton:
```kotlin
@Document(collection = "<collection_name>")
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class <Model>(
  @JsonIgnore @Id val id: String,
  @JsonIgnore override val blockId: String,
  @JsonIgnore override val blockNumber: Long,
  @JsonIgnore override val blockTimestamp: Long,
  @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
  // domain fields...
) : VersionedDocument {
  @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "<archive_collection_name>")
data class <Model>Archive(@Id override val id: String, override val data: <Model>) : Archive<<Model>>
```

### 1.2 Repository
Put repositories in `.../repository/` and extend `BaseIndexedRepository`.

Skeleton:
```kotlin
@Profile("<feature>", "<subfeature>")
interface <Model>Repository : BaseIndexedRepository<<Model>, String> {
  // Spring Data derived queries as needed
}
```

## 2) Indexer module (`packages/indexer`)

### 2.1 Names
Add a nested object with `NAME` and `COLLECTION` constants to `packages/common/src/main/kotlin/org/vechain/indexer/IndexerNames.kt`.

### 2.2 Service (business logic lives here)
- Keep `processBlock/processEvents` returning `(updated, archivedExisting)` lists.
- Use `saveVersionedDocuments(...)` to atomically persist + archive + prune.

Skeleton:
```kotlin
@Profile("<feature>", "<subfeature>")
@Service
open class <Model>Service(
  private val repository: <Model>Repository,
  private val archiveService: ArchiveService<<Model>, <Model>Archive>,
  private val pruner: TargetedPruner<<Model>, <Model>Archive>,
) {
  open fun processBlock(block: Block, events: List<IndexedEvent>): Pair<List<<Model>>, List<<Model>>> =
    Pair(emptyList(), emptyList())

  @Transactional
  open fun save(updated: List<<Model>>, existing: List<<Model>>) {
    saveVersionedDocuments(updated, existing, repository, archiveService, pruner)
  }
}
```

### 2.3 Processor
- Versioned indexers should use `BaseStatefulProcessor`.
- Only call `save` when either list is non-empty.

### 2.4 Config + CollectionConfig
- Config wires: `ArchiveService`, `TargetedPruner`, `IndexerFactory()` settings.
- CollectionConfig wires: version gate (`indexer.version.<key>`) and `ensureIndexes(...)`.
- Add compound indexes to match API query filters + sort, not just single-field indexes.

## 3) API module (`packages/api`)

### 3.1 Controller
Offset pagination:
```kotlin
val pageable = PaginationUtils.toPageable(page, size, direction, <Model>::blockTimestamp.name)
return paginatedResponse(service.query(..., pageable))
```

Time range inputs:
- Validate with `TimeValidationUtils.validateTimestamps(after, before)`

### 3.2 Service
Keep to repository queries only. Avoid re-encoding “indexer logic” here.

## 4) Wiring (required)

### 4.1 application.yaml
Add:
- `indexer.start-block.<key>` with env var `INDEXER_START_BLOCK_<KEY>`
- `indexer.sync-block-batch-size.<key>` with env var `INDEXER_SYNC_BLOCK_BATCH_SIZE_<KEY>`
- `indexer.version.<key>` with env var `VERSION_<KEY>`

### 4.2 Terraform
Update all of:
- `terraform/api/api.tf` and `terraform/devnet/api.tf` env var lists
- `terraform/api/environments/prod-*.yml` and `terraform/devnet/environments/devnet.yml` for:
  - `spring_profile` (add `<subfeature>` if needed)
  - `start-block.<key>`, `sync-block-batch-size.<key>`, `version.<key>`

## 5) Verification
- Local compile: `./gradlew :packages:common:compileKotlin :packages:indexer:compileKotlin :packages:api:compileKotlin`
- Targeted tests: `make test-indexer`, `make test-api`
- Deployed API schema checks: `scripts/run_api_schema_tests.sh` (Schemathesis)

