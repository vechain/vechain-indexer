package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.exception.ArchiveException
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedDocument
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.ArchiveRepository
import org.vechain.indexer.utils.IdUtils
import org.vechain.indexer.utils.JsonUtils

@Service
open class ArchiveService(
    private val archiveRepository: ArchiveRepository,
    private val mongoTemplate: MongoTemplate
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    open fun getPreviousVersionId(document: VersionedDocument): String =
        IdUtils.buildArchiveId(document, document.version - 1)

    open fun saveAll(documents: List<VersionedDocument>) {

        if (documents.isEmpty()) return

        val archives =
            documents.map {
                val archiveId = IdUtils.buildArchiveId(it, it.version)
                Archive(archiveId, it)
            }
        archiveRepository.saveAll(archives)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun <T : VersionedDocument> rollback(blockNumber: Long, clazz: Class<T>) {
        val currentDocuments = getCurrentDocuments(blockNumber, clazz)

        if (currentDocuments.isEmpty()) {
            logger.info("{}: No documents to rollback for block {}", clazz.simpleName, blockNumber)
            return
        }

        val previousDocumentIds =
            currentDocuments.filter { it.version > 1 }.map { getPreviousVersionId(it) }

        val previousDocuments =
            archiveRepository
                .findAllById(previousDocumentIds)
                .map { it.data }
                .filter { clazz.isInstance(it) }
                .map { clazz.cast(it) }
                .associateBy { it.getDocumentId() }

        val rollbackOperations = getRollbackOperation(currentDocuments, previousDocuments, clazz)

        val rollback = rollbackOperations.execute()
        archiveRepository.deleteAllById(previousDocumentIds)

        logger.info(
            "{} - Rollback of block {} completed: \n- {} documents rolled back \n- {} documents deleted",
            clazz.simpleName,
            blockNumber,
            rollback.modifiedCount,
            rollback.deletedCount
        )
    }

    open fun <T : VersionedDocument> getCurrentDocuments(
        blockNumber: Long,
        clazz: Class<T>
    ): List<T> {
        val query =
            Query().addCriteria(Criteria.where(IndexedDocument::blockNumber.name).`is`(blockNumber))

        return mongoTemplate.find(query, clazz)
    }

    open fun <T : VersionedDocument> getRollbackOperation(
        currentDocuments: List<T>,
        previousDocuments: Map<String, T>,
        clazz: Class<T>
    ): BulkOperations {
        val bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, clazz)

        for (document in currentDocuments) {
            val query = Query().addCriteria(Criteria.where("_id").`is`(document.getDocumentId()))

            // If the document has been updated, replace it with the previous version
            if (document.version > 1) {
                val previousDocument = previousDocuments[document.getDocumentId()]
                if (previousDocument == null) {
                    logger.error(
                        "Could not find previous document for rollback (${clazz.simpleName}): {}",
                        JsonUtils.mapper.writeValueAsString(document)
                    )
                    throw ArchiveException("Could not find previous document for rollback")
                }

                bulkOperations.replaceOne(query, previousDocument)
            } else {
                // If the document has not been updated, remove it
                bulkOperations.remove(query)
            }
        }

        return bulkOperations
    }
}
