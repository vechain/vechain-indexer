package org.vechain.indexer.archive

import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.BulkOperationException
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext
import org.springframework.data.mongodb.core.aggregation.AggregationOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.util.CloseableIterator
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.utils.JsonUtils
import org.vechain.indexer.utils.buildArchiveId

/** Helper to inject a raw aggregation stage (here: $setWindowFields). */
class RawStage(private val stage: Document) : AggregationOperation {
    @Deprecated("Deprecated in Java")
    override fun toDocument(context: AggregationOperationContext): Document = stage
}

open class ArchiveService<T : VersionedDocument, S : Archive<T>>(
    open val mongoTemplate: MongoTemplate,
    private val clazz: Class<T>,
    private val archiveClazz: Class<S>,
    private val queryLimit: Long,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    open fun getPreviousVersionId(document: VersionedDocument): String =
        buildArchiveId(document, document.version - 1)

    open fun saveAll(documents: List<T>) {
        if (documents.isEmpty()) return

        val archives =
            documents.map {
                archiveClazz
                    .getConstructor(String::class.java, it::class.java)
                    .newInstance(buildArchiveId(it, it.version), it)
            }

        val bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, archiveClazz)
        bulkOps.insert(archives)

        try {
            bulkOps.execute()
        } catch (e: BulkOperationException) {
            val nonDuplicateErrors = e.errors.filter { it.code != DUPLICATE_KEY_ERROR_CODE }
            if (nonDuplicateErrors.isNotEmpty()) {
                throw e
            }
            val duplicateCount = e.errors.size - nonDuplicateErrors.size
            logger.debug(
                "{}: Skipped {} duplicate archive(s) out of {} documents",
                archiveClazz.simpleName,
                duplicateCount,
                documents.size,
            )
        }
    }

    companion object {
        private const val DUPLICATE_KEY_ERROR_CODE = 11000
    }

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

    /** Get all documents greater than or equal to the block number */
    open fun getCurrentDocuments(blockNumber: Long): List<T> {
        val query =
            Query().addCriteria(Criteria.where(IndexedDocument::blockNumber.name).gte(blockNumber))

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

    open fun findRecordsToPrune(
        endBlock: Long,
        batchSize: Int,
        idsToPrune: List<String>? = null,
    ): CloseableIterator<String> {
        require(batchSize > 0) { "Batch size must be greater than zero" }
        logger.debug("Finding records to prune for {}", clazz.simpleName)

        // 1) Create the match stage. If ids are provided, filter by them as well.
        val matchCriteria = Criteria.where("data.blockNumber").lt(endBlock)
        if (idsToPrune != null && idsToPrune.isNotEmpty()) {
            matchCriteria.and("data._id").`in`(idsToPrune)
        }

        // 2) Rank by recordId/version (desc)
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

        // 3) Build pipeline
        val pipeline =
            Aggregation.newAggregation(
                    Aggregation.match(matchCriteria),
                    Aggregation.sort(
                        Sort.by(Sort.Order.asc("data._id"), Sort.Order.desc("data.version"))
                    ),
                    setWindowFields,
                    Aggregation.match(Criteria.where("rn").gt(1)),
                    Aggregation.project("_id"),
                    Aggregation.limit(queryLimit),
                )
                .withOptions(
                    AggregationOptions.builder()
                        .allowDiskUse(true)
                        .cursorBatchSize(batchSize)
                        .build()
                )

        val stream =
            mongoTemplate.aggregateStream(
                pipeline,
                mongoTemplate.getCollectionName(archiveClazz),
                Document::class.java,
            )
        val iterator = stream.iterator()

        return object : CloseableIterator<String> {
            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): String = iterator.next().getString("_id")

            override fun remove() {
                throw UnsupportedOperationException("remove is not supported")
            }

            override fun close() {
                stream.close()
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun removeAll(records: List<String>) {
        logger.debug("Removing {} archives for {}", records.size, clazz.simpleName)
        mongoTemplate.remove(Query.query(Criteria.where("_id").`in`(records)), archiveClazz)
    }
}
