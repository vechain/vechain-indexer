package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate

@PublishedApi internal val logger = LoggerFactory.getLogger("VersionedDocumentPersistence")

/**
 * Shared persistence helper for versioned documents.
 *
 * Handles saving current records with inline versioning: previous versions are stored in a
 * `_previousVersions` array on each document. Callers are expected to annotate their `save()`
 * methods with `@Transactional` so that a failure at any step triggers a rollback when transactions
 * are available.
 */
inline fun <reified T : VersionedDocument> saveVersionedDocuments(
    updated: List<T>,
    existing: List<T>,
    mongoTemplate: MongoTemplate,
    blockWindow: Long,
    maxVersions: Int,
) {
    if (updated.isEmpty()) return
    InlineVersionService.bulkUpsertWithVersions(
        updated,
        existing,
        mongoTemplate,
        blockWindow,
        maxVersions,
    )
}
