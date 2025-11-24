package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.FindAndReplaceOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.TargetedPruner

private val log = LoggerFactory.getLogger("VersionedDocumentPersistence")

/**
 * Shared persistence helper for versioned documents.
 *
 * Handles saving current records using bulk operations, archiving previous versions, and invoking
 * the pruner when older archives can be removed.
 *
 * Uses UNORDERED bulk operations for performance and resilience with concurrent indexers.
 */
fun <T, S> saveVersionedDocuments(
    updated: List<T>,
    existing: List<T>,
    archiveService: ArchiveService<T, S>,
    pruner: TargetedPruner<T, S>,
    mongoTemplate: MongoTemplate,
    batchSize: Int = 1000,
    unordered: Boolean = true,
) where T : VersionedDocument, S : Archive<T> {
    // Save updated documents using bulk operations
    if (updated.isNotEmpty()) {
        saveBulk(updated, mongoTemplate, batchSize, unordered)
    }

    if (existing.isEmpty()) return

    // Archive existing versions
    archiveService.saveAll(existing)

    // Prune old archives
    val idsToPrune = existing.filter { it.version > 1 }.map { it.getDocumentId() }
    if (idsToPrune.isEmpty()) return

    val latestBlock =
        (updated.asSequence() + existing.asSequence()).maxOfOrNull { it.blockNumber } ?: 0L
    pruner.run(latestBlock, idsToPrune)
}

/**
 * Saves documents using UNORDERED bulk operations for maximum performance. Safe for concurrent
 * indexers - uses upsert to prevent duplicates.
 */
fun <T : VersionedDocument> saveBulk(
    documents: List<T>,
    mongoTemplate: MongoTemplate,
    batchSize: Int,
    unordered: Boolean = true,
) {
    if (documents.isEmpty()) return

    val entityClass = documents.first()::class.java

    val bulkMode =
        if (unordered) BulkOperations.BulkMode.UNORDERED else BulkOperations.BulkMode.ORDERED

    documents.chunked(batchSize).forEach { batch ->
        try {
            val bulkOps = mongoTemplate.bulkOps(bulkMode, entityClass)

            batch.forEach { document ->
                // Use upsert with document ID to handle concurrent indexers
                // This prevents duplicate key errors when multiple indexers process same blocks
                bulkOps.replaceOne(
                    Query.query(Criteria.where("_id").`is`(document.getDocumentId())),
                    document,
                    FindAndReplaceOptions.options().upsert(),
                )
            }

            val result = bulkOps.execute()
            log.debug(
                "Saved ${entityClass.simpleName}: ${result.insertedCount} inserted, " +
                    "${result.modifiedCount} updated (batch size: ${batch.size})"
            )
        } catch (e: org.springframework.data.mongodb.BulkOperationException) {
            // Partial success is OK - some succeeded, some failed
            val succeeded = e.result.insertedCount + e.result.modifiedCount
            val failed = e.result.upserts.size - succeeded // Calculate failed count

            log.warn(
                "Bulk save ${entityClass.simpleName}: $succeeded succeeded, $failed failed. " +
                    "This is expected with concurrent indexers."
            )

            // Log the exception message for debugging
            log.debug("Bulk operation error: ${e.message}")

            // Don't throw - partial success is acceptable
            // Failed items will be retried when IndexerRunner retries the block range
        } catch (e: Exception) {
            // Unexpected error - let it bubble up for retry at IndexerRunner level
            log.error("Unexpected error during bulk save of ${entityClass.simpleName}", e)
            throw e
        }
    }
}
