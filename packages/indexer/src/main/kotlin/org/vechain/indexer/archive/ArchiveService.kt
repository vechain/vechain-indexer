package org.vechain.indexer.archive

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext
import org.springframework.data.mongodb.core.aggregation.AggregationOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.timing.WithTiming
import org.vechain.indexer.utils.IdUtils
import org.vechain.indexer.utils.JsonUtils

/** Helper to inject a raw aggregation stage (here: $setWindowFields). */
class RawStage(private val stage: Document) : AggregationOperation {
    @Deprecated("Deprecated in Java")
    override fun toDocument(context: AggregationOperationContext): Document = stage
}

open class ArchiveService<T : VersionedDocument, S : Archive<T>>(
    private val mongoTemplate: MongoTemplate,
    private val clazz: Class<T>,
    private val archiveClazz: Class<S>,
    private val queryLimit: Long,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    open fun getPreviousVersionId(document: VersionedDocument): String =
        IdUtils.buildArchiveId(document, document.version - 1)

    open fun saveAll(documents: List<T>) {

        if (documents.isEmpty()) return

        val archives =
            documents.map {
                archiveClazz
                    .getConstructor(String::class.java, it::class.java)
                    .newInstance(IdUtils.buildArchiveId(it, it.version), it)
            }
        mongoTemplate.insert(archives, archiveClazz)
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
            archiveClazz,
        )

        logger.info(
            "{} - Rollback of block {} completed: \n- {} documents rolled back \n- {} documents deleted",
            clazz.simpleName,
            blockNumber,
            rollback.modifiedCount,
            rollback.deletedCount,
        )
    }

    /** Get all documents for a given block number from the collection of the given class */
    open fun getCurrentDocuments(blockNumber: Long): List<T> {
        val query =
            Query().addCriteria(Criteria.where(IndexedDocument::blockNumber.name).`is`(blockNumber))

        return mongoTemplate.find(query, clazz)
    }

    open fun getRollbackOperation(
        currentDocuments: List<T>,
        previousDocuments: Map<String, T>,
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
                        JsonUtils.mapper.writeValueAsString(document),
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

    @WithTiming("Pruner - findRecordsToPrune")
    open fun findRecordsToPrune(endBlock: Long): List<String> {
        logger.info("Finding records to prune for {}", clazz.simpleName)

        // 1) Rank by recordId/version (desc)
        val setWindowFields =
            RawStage(
                Document(
                    "\$setWindowFields",
                    Document()
                        .append("partitionBy", "\$data._id")
                        .append("sortBy", Document("data.version", -1))
                        .append("output", Document("rn", Document("\$documentNumber", Document()))),
                )
            )

        // 2) Build pipeline
        val pipeline =
            Aggregation.newAggregation(
                    Aggregation.match(Criteria.where("data.blockNumber").lt(endBlock)),
                    Aggregation.sort(
                        Sort.by(Sort.Order.asc("data._id"), Sort.Order.desc("data.version"))
                    ),
                    setWindowFields,
                    Aggregation.match(Criteria.where("rn").gt(1)),
                    Aggregation.project("_id"),
                    Aggregation.limit(queryLimit),
                )
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build())

        // Execute the aggregation
        return mongoTemplate
            .aggregate(
                pipeline,
                mongoTemplate.getCollectionName(archiveClazz),
                Document::class.java,
            )
            .map { it.getString("_id") }
            .toList()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun removeAll(records: List<String>) {
        logger.debug("Removing {} archives for {}", records.size, clazz.simpleName)
        mongoTemplate.remove(Query.query(Criteria.where("_id").`in`(records)), archiveClazz)
    }
}
