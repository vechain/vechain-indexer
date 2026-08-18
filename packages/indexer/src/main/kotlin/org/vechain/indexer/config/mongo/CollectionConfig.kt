package org.vechain.indexer.config.mongo

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexInfo
import org.springframework.data.mongodb.core.index.PartialIndexFilter
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.IndexedDocument

abstract class CollectionConfig(
    private val mongoTemplate: MongoTemplate,
    @Suppress("UNUSED_PARAMETER") _coroutineScope: CoroutineScope,
    val modelObj: Class<*>,
) {
    companion object {
        /**
         * Default partial filter for IndexedDocument collections. Excludes checkpoint documents.
         */
        val INDEXED_DOCUMENT_PARTIAL_FILTER: Document =
            Document("blockNumber", Document("\$exists", true))
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val ensuredIndexNamesByCollection = mutableMapOf<String, MutableSet<String>>()
    private val pendingIndexes = mutableListOf<PendingIndex>()

    private data class PendingIndex(
        val name: String,
        val index: Index,
        val entityClass: Class<*>,
        val partialFilter: Document?,
    )

    abstract fun initCollection()

    protected fun collectionHasDocuments(entityClass: Class<*> = modelObj): Boolean {
        require(IndexedDocument::class.java.isAssignableFrom(entityClass)) {
            "collectionHasDocuments requires an IndexedDocument entity: ${entityClass.name}"
        }

        val start = TimeSource.Monotonic.markNow()
        val query = Query.query(Criteria.where("blockNumber").exists(true))
        val result =
            mongoTemplate.exists(query, entityClass, mongoTemplate.getCollectionName(entityClass))
        val elapsed = start.elapsedNow()

        if (elapsed > 1.seconds) {
            logger.warn(
                "Indexed-document existence probe for {} took {} and found documents={}",
                entityClass.simpleName,
                elapsed,
                result,
            )
        } else {
            logger.debug(
                "Indexed-document existence probe for {} completed in {} and found documents={}",
                entityClass.simpleName,
                elapsed,
                result,
            )
        }

        return result
    }

    fun ensureCollection() {
        if (mongoTemplate.collectionExists(modelObj)) {
            logger.debug("Collection ${modelObj.simpleName} already exists.")
            return
        }
        try {
            logger.info("Creating collection ${modelObj.simpleName}")
            mongoTemplate.createCollection(modelObj)
        } catch (e: Exception) {
            logger.error("Failed to create collection ${modelObj.simpleName}", e)
            throw e
        }
    }

    /**
     * Registers indexes that should exist on [entityClass] after bootstrap. The indexes are
     * buffered (not created yet); [removeStaleIndexes] runs against the registered names first to
     * drop legacy indexes, then [createPendingIndexes] performs the actual `createIndex` calls.
     * This split prevents `IndexOptionsConflict` (MongoDB error 85) when an index is renamed but
     * the legacy index still exists with the same key spec.
     */
    fun ensureIndexes(
        indexes: Collection<Pair<String, Index>>,
        entityClass: Class<*> = modelObj,
        partialFilter: Document? = INDEXED_DOCUMENT_PARTIAL_FILTER,
    ) {
        if (indexes.isEmpty()) {
            logger.debug("No additional indexes configured for ${entityClass.simpleName}.")
            return
        }

        val collectionName = mongoTemplate.getCollectionName(entityClass)
        val names = ensuredIndexNamesByCollection.getOrPut(collectionName) { mutableSetOf() }
        for ((indexName, index) in indexes) {
            names.add(indexName)
            pendingIndexes.add(PendingIndex(indexName, index, entityClass, partialFilter))
        }
    }

    /**
     * Builds a `(name, Index)` pair where the name is derived from the field/direction sequence,
     * e.g. `("blockNumber" to DESC, "txId" to ASC)` -> `"blockNumber_-1_txId_1"`. Use this in
     * [ensureIndexes] to keep index names consistent with their key shape.
     */
    protected fun buildIndex(vararg fields: Pair<String, Sort.Direction>): Pair<String, Index> {
        require(fields.isNotEmpty()) { "Index must have at least one field" }
        val name =
            fields.joinToString("_") { (field, dir) ->
                "${field}_${if (dir == Sort.Direction.ASC) 1 else -1}"
            }
        val index = fields.fold(Index()) { acc, (field, dir) -> acc.on(field, dir) }
        return name to index
    }

    protected fun buildUniqueIndex(
        vararg fields: Pair<String, Sort.Direction>
    ): Pair<String, Index> {
        val (name, index) = buildIndex(*fields)
        return name to index.unique()
    }

    /**
     * Drops indexes from MongoDB that are not tracked by [ensureIndexes] calls during
     * [initCollection], plus any tracked index whose stored options no longer match the pending
     * spec (e.g. partial filter or unique flag changed). Must run after [initCollection] (so all
     * expected names are registered) and before [createPendingIndexes] (so legacy and drifted
     * indexes don't collide on `IndexOptionsConflict` / `IndexKeySpecsConflict`).
     */
    fun removeStaleIndexes() {
        val pendingByCollection = pendingIndexes.groupBy {
            mongoTemplate.getCollectionName(it.entityClass)
        }

        ensuredIndexNamesByCollection.forEach { (collectionName, expectedNames) ->
            val pendingByName =
                pendingByCollection[collectionName].orEmpty().associateBy { it.name }
            mongoTemplate.indexOps(collectionName).indexInfo.forEach { info ->
                val name = info.name
                if (name == "_id_") return@forEach

                val drop =
                    if (name !in expectedNames) {
                        true
                    } else {
                        pendingByName[name]?.let { !specMatches(it, info) } ?: false
                    }

                if (drop) {
                    try {
                        logger.info("Removing stale index: {} from {}", name, collectionName)
                        mongoTemplate.indexOps(collectionName).dropIndex(name)
                    } catch (e: Exception) {
                        throw IllegalStateException(
                            "Failed to remove stale index $name from $collectionName",
                            e,
                        )
                    }
                }
            }
        }
    }

    private fun specMatches(pending: PendingIndex, existing: IndexInfo): Boolean {
        val expectedFilter = pending.partialFilter
        val actualFilter = existing.partialFilterExpression?.let { Document.parse(it) }
        if (expectedFilter != actualFilter) return false

        val expectedUnique = pending.index.indexOptions.getBoolean("unique", false)
        if (expectedUnique != existing.isUnique) return false

        return true
    }

    /**
     * Creates the indexes registered via [ensureIndexes]. Indexes whose names already exist are
     * validated against the pending spec via [specMatches]; a name match with drifted options
     * throws rather than silently skipping creation.
     */
    fun createPendingIndexes() {
        if (pendingIndexes.isEmpty()) return

        val existingByEntity = mutableMapOf<Class<*>, Map<String, IndexInfo>>()
        val toCreate = pendingIndexes.filter { pending ->
            val existing =
                existingByEntity.getOrPut(pending.entityClass) {
                    mongoTemplate.indexOps(pending.entityClass).indexInfo.associateBy {
                        it.name
                    }
                }
            val match = existing[pending.name] ?: return@filter true
            check(specMatches(pending, match)) {
                "Index ${pending.name} for ${pending.entityClass.simpleName} exists with " +
                    "mismatched options; removeStaleIndexes should have dropped it"
            }
            false
        }

        if (toCreate.isEmpty()) {
            logger.debug(
                "All ${pendingIndexes.size} indexes already exist for ${modelObj.simpleName}"
            )
            return
        }

        val start = TimeSource.Monotonic.markNow()
        logger.info("Creating ${toCreate.size} new indexes for ${modelObj.simpleName}")

        for (pending in toCreate) {
            createIndex(pending)
        }

        logger.info(
            "Created ${toCreate.size} indexes for ${modelObj.simpleName} in {}",
            start.elapsedNow(),
        )
    }

    private fun createIndex(pending: PendingIndex) {
        try {
            logger.info("Creating index {} for {}", pending.name, pending.entityClass.simpleName)
            val indexDef = pending.index.named(pending.name).background()
            if (pending.partialFilter != null) {
                indexDef.partial(PartialIndexFilter.of(pending.partialFilter))
            }
            mongoTemplate.indexOps(pending.entityClass).createIndex(indexDef)
        } catch (e: Exception) {
            logger.error(
                "Failed to create index ${pending.name} for ${pending.entityClass.simpleName}",
                e,
            )
            throw IllegalStateException(
                "Failed to create index ${pending.name} for ${pending.entityClass.simpleName}",
                e,
            )
        }
    }
}
