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

    private fun getPreviousVersionId(document: VersionedDocument): String =
      IdUtils.buildArchiveId(document, document.version - 1)

    fun saveAll(documents: List<VersionedDocument>) {

        if (documents.isEmpty()) return

        val archives =
          documents.map {
              val archiveId = IdUtils.buildArchiveId(it, it.version)
              Archive(archiveId, it)
          }
        archiveRepository.saveAll(archives)
    }

    @Transactional
    open fun <T : VersionedDocument> rollback(blockNumber: Long, clazz: Class<T>) {
        val currentDocuments = getCurrentDocuments(blockNumber, clazz)

        if (currentDocuments.isEmpty()) return

        // Split the documents by first time entities and subsequent entities
        val currentPartitions = currentDocuments.partition { it.version == 1 }

        logger.debug(
          "${clazz.simpleName}: Deleting ${currentPartitions.first.size} first time entities and rolling back ${currentPartitions.second.size} subsequent entities"
        )

        // Replace the documents with version > 1 with the previous version
        if (currentPartitions.second.isNotEmpty())
          rollbackCurrentDocuments(currentPartitions.second, clazz)

        // Remove the documents with version == 1
        if (currentPartitions.first.isNotEmpty())
          removeCurrentDocuments(currentPartitions.first, clazz)
    }

    private fun <T : VersionedDocument> getCurrentDocuments(
      blockNumber: Long,
      clazz: Class<T>
    ): List<T> {
        val query =
          Query().addCriteria(Criteria.where(IndexedDocument::blockNumber.name).`is`(blockNumber))

        return mongoTemplate.find(query, clazz)
    }

    private fun <T : VersionedDocument> rollbackCurrentDocuments(
      documents: List<T>,
      clazz: Class<T>
    ) {

        val previousDocumentIds = documents.map { getPreviousVersionId(it) }

        // Find the archives for the documents with version > 1
        val previousDocuments =
          archiveRepository
            .findAllById(previousDocumentIds)
            .map { it.data }
            .filter { clazz.isInstance(it) }
            .map { clazz.cast(it) }

        // Check if all previous documents were found
        if (previousDocumentIds.size != previousDocuments.size) {
            logger.error(
              "Could not find all previous documents for rollback (${clazz.simpleName}): {}",
              JsonUtils.mapper.writeValueAsString(documents.map { it.getDocumentId() })
            )
            throw ArchiveException("Could not find all previous documents for rollback")
        }

        // Perform the rollback
        if (previousDocuments.isNotEmpty()) {
            replaceCurrentDocuments(previousDocuments, clazz)
            archiveRepository.deleteAllById(previousDocumentIds)
        }
    }

    private fun <T : VersionedDocument> replaceCurrentDocuments(
      documents: List<T>,
      clazz: Class<T>
    ) {
        if (documents.isEmpty()) return

        logger.debug(
          "Replacing documents (${clazz.simpleName}): {}",
          JsonUtils.mapper.writeValueAsString(documents.map { it.getDocumentId() })
        )

        val bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, clazz)

        for (document in documents) {
            val query = Query().addCriteria(Criteria.where("_id").`is`(document.getDocumentId()))
            bulkOperations.replaceOne(query, document)
        }

        val update = bulkOperations.execute()

        logger.debug("${clazz.simpleName}: Updated ${update.modifiedCount} documents")
    }

    private fun <T : VersionedDocument> removeCurrentDocuments(
      documents: List<T>,
      clazz: Class<T>
    ) {
        if (documents.isEmpty()) return

        logger.debug(
          "Removing documents (${clazz.simpleName}): {}",
          JsonUtils.mapper.writeValueAsString(documents.map { it.getDocumentId() })
        )

        val bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, clazz)

        for (document in documents) {
            val query = Query().addCriteria(Criteria.where("_id").`is`(document.getDocumentId()))
            bulkOperations.remove(query)
        }

        val removal = bulkOperations.execute()

        logger.debug("${clazz.simpleName}: Removed ${removal.deletedCount} documents")
    }
}
