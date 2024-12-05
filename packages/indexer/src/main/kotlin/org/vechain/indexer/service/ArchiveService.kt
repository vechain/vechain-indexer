package org.vechain.indexer.service

import kotlin.math.min
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.exception.ArchiveException
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.IndexedDocument
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.utils.IdUtils
import org.vechain.indexer.utils.JsonUtils

open class ArchiveService<T : VersionedDocument, S : Archive<T>>(
    private val repository: BaseIndexedRepository<T>,
    private val mongoTemplate: MongoTemplate,
    private val clazz: Class<T>,
    private val archiveClazz: Class<S>,
    private val prunerLimit: Int,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    open fun getPreviousVersionId(document: VersionedDocument): String =
        IdUtils.buildArchiveId(document, document.version - 1)

    @Transactional(rollbackFor = [Exception::class])
    open fun update(updated: List<T>, toArchive: List<T>) {

        if (updated.isNotEmpty()) {
            // Save the documents with the updated version
            repository.saveAll(updated)
        }

        if (toArchive.isNotEmpty()) {
            val archives =
                toArchive.map {
                    archiveClazz
                        .getConstructor(String::class.java, it::class.java)
                        .newInstance(IdUtils.buildArchiveId(it, it.version), it)
                }
            mongoTemplate.insert(archives, archiveClazz)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun rollback(blockNumber: Long) {
        val currentDocuments = getCurrentDocuments(blockNumber)

        if (currentDocuments.isEmpty()) {
            logger.info("{}: No documents to rollback for block {}", clazz.simpleName, blockNumber)
            return
        }

        val previousDocumentIds =
            currentDocuments.filter { it.version > 1 }.map { getPreviousVersionId(it) }

        val previousDocuments =
            mongoTemplate
                .find(Query.query(Criteria.where("_id").`in`(previousDocumentIds)), archiveClazz)
                .map { clazz.cast(it.data) }
                .associateBy { it.getDocumentId() }

        val rollbackOperations = getRollbackOperation(currentDocuments, previousDocuments)

        val rollback = rollbackOperations.execute()
        mongoTemplate.remove(
            Query.query(Criteria.where("_id").`in`(previousDocumentIds)),
            archiveClazz
        )

        logger.info(
            "{} - Rollback of block {} completed: \n- {} documents rolled back \n- {} documents deleted",
            clazz.simpleName,
            blockNumber,
            rollback.modifiedCount,
            rollback.deletedCount
        )
    }

    /** Get all documents for a given block number from the collection of the given class */
    open fun getCurrentDocuments(
        blockNumber: Long,
    ): List<T> {
        val query =
            Query().addCriteria(Criteria.where(IndexedDocument::blockNumber.name).`is`(blockNumber))

        return mongoTemplate.find(query, clazz)
    }

    open fun getRollbackOperation(
        currentDocuments: List<T>,
        previousDocuments: Map<String, T>
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

    /**
     * Prune archives that are no longer needed. The finalised block number is used to determine
     * which archives can be pruned. All records that are older than the finalised block number and
     * are not the last version of a document are pruned.
     */
    open fun prune(currentBlock: Long) {
        // Assume the block 1,000 blocks ago is finalised
        val finalisedBlockNumber = currentBlock - 1_000

        // Construct the aggregation pipeline stages
        val matchStage =
            Document(
                "\$match",
                Document("data.blockNumber", Document("\$lt", finalisedBlockNumber))
            )

        val groupStage =
            Document(
                "\$group",
                Document("_id", "\$data._id")
                    .append("maxVersion", Document("\$max", "\$data.version"))
                    .append("allDocs", Document("\$push", "\$\$ROOT"))
            )

        val matchMaxVersionStage = Document("\$match", Document("maxVersion", Document("\$gt", 1)))

        val unwindStage = Document("\$unwind", "\$allDocs")

        val matchExprStage =
            Document(
                "\$match",
                Document(
                    "\$expr",
                    Document(
                        "\$lt",
                        listOf(
                            "\$allDocs.data.version",
                            Document("\$subtract", listOf("\$maxVersion", 4))
                        )
                    )
                )
            )

        val projectStage =
            Document(
                "\$project",
                Document("_id", "\$allDocs._id") // Projecting the top-level _id of the document
            )

        // Combine all stages into a pipeline
        val pipeline =
            listOf(
                matchStage,
                groupStage,
                matchMaxVersionStage,
                unwindStage,
                matchExprStage,
                projectStage
            )

        // Execute the aggregation
        val records =
            mongoTemplate
                .getCollection(mongoTemplate.getCollectionName(archiveClazz))
                .aggregate(pipeline)
                .map {
                    it.getString("_id")
                } // Assuming `_id` is a String; adjust if it's an ObjectId
                .toList()

        if (records.isEmpty()) {
            logger.info("No documents to prune for {}", clazz.simpleName)
            return
        }

        // Loop through the records and remove them in batches
        logger.info("Pruning {} archive documents for {}", records.size, clazz.simpleName)

        var currentIndex = 0
        while (currentIndex < records.size) {
            // Calculate the end index for the current batch
            val endIndex = min(currentIndex + prunerLimit, records.size)

            // Delete the current batch of records
            mongoTemplate.remove(
                Query.query(Criteria.where("_id").`in`(records.subList(currentIndex, endIndex))),
                archiveClazz
            )

            // Move the index forward
            currentIndex = endIndex
        }
    }
}
