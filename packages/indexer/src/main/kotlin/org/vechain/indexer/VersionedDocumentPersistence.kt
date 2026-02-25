package org.vechain.indexer

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.TargetedPruner

@PublishedApi internal val logger = LoggerFactory.getLogger("VersionedDocumentPersistence")

/**
 * Shared persistence helper for versioned documents.
 *
 * Handles saving current records, archiving previous versions, and deferring pruning of older
 * archives to after the transaction commits. Callers are expected to annotate their `save()`
 * methods with `@Transactional` so that a failure at any step triggers a rollback when transactions
 * are available.
 *
 * Pruning is deferred to after the transaction commits to avoid inflating the transaction duration.
 * MongoDB Atlas enforces a 60-second transaction lifetime limit; including pruning (which queries
 * and deletes potentially thousands of archive records) inside the transaction was a primary cause
 * of TransientTransactionError failures.
 */
inline fun <reified T, S> saveVersionedDocuments(
    updated: List<T>,
    existing: List<T>,
    archiveService: ArchiveService<T, S>,
    pruner: TargetedPruner<T, S>,
) where T : VersionedDocument, S : Archive<T> {
    if (updated.isNotEmpty()) {
        bulkUpsert(updated, archiveService.mongoTemplate)
    }

    if (existing.isEmpty()) return

    archiveService.saveAll(existing)

    val idsToPrune = existing.filter { it.version > 1 }.map { it.getDocumentId() }
    if (idsToPrune.isEmpty()) return

    val latestBlock =
        (updated.asSequence() + existing.asSequence()).maxOfOrNull { it.blockNumber } ?: 0L

    // Defer pruning to after the transaction commits so it doesn't contribute to
    // transaction duration. Falls back to inline execution if no transaction is active.
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    pruner.run(latestBlock, idsToPrune)
                }
            }
        )
    } else {
        pruner.run(latestBlock, idsToPrune)
    }
}

@PublishedApi
internal inline fun <reified T : VersionedDocument> bulkUpsert(
    documents: List<T>,
    mongoTemplate: MongoTemplate,
) {
    if (documents.isEmpty()) return
    val clazz = T::class.java
    val ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, clazz)

    documents.forEach { doc ->
        val bson = Document().also { mongoTemplate.converter.write(doc, it) }
        val query = Query(Criteria.where("_id").`is`(bson["_id"]))
        val update = Update()
        bson.forEach { (k, v) -> if (k != "_id") update.set(k, v) }
        ops.upsert(query, update)
    }

    try {
        ops.execute()
    } catch (e: Exception) {
        logger.error(
            "Bulk upsert failed for {} ({} documents): {}",
            clazz.simpleName,
            documents.size,
            e.message,
        )
        throw e
    }
}
