package org.vechain.indexer.archive

import com.mongodb.client.model.Filters
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

open class ArchiveService<T : VersionedDocument>(
    private val mongoTemplate: MongoTemplate,
    private val clazz: Class<T>,
    private val queryLimit: Long,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val collectionName = mongoTemplate.getCollectionName(clazz)

    open fun getPreviousVersionId(document: VersionedDocument): String =
        buildArchiveId(document, document.version - 1)

    open fun saveAll(documents: List<T>) {
        if (documents.isEmpty()) return

        val archiveDocs =
            documents.map { doc ->
                val bsonDoc = Document()
                mongoTemplate.converter.write(doc, bsonDoc)
                bsonDoc["_id"] = buildArchiveId(doc, doc.version)
                bsonDoc["_isArchive"] = true
                bsonDoc["_originalDocId"] = doc.getDocumentId()
                bsonDoc
            }
        mongoTemplate.getCollection(collectionName).insertMany(archiveDocs)
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

        // Read archive docs from main collection, strip extra fields, convert back
        val previousDocuments =
            if (previousDocumentIds.isNotEmpty()) {
                mongoTemplate
                    .getCollection(collectionName)
                    .find(Filters.`in`("_id", previousDocumentIds))
                    .map { doc ->
                        doc.remove("_isArchive")
                        val originalId = doc.remove("_originalDocId")
                        doc["_id"] = originalId
                        mongoTemplate.converter.read(clazz, doc)
                    }
                    .associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val rollbackOperations = getRollbackOperation(currentDocuments, previousDocuments)

        val rollback = rollbackOperations.execute()

        // Delete used archives from main collection
        if (previousDocumentIds.isNotEmpty()) {
            mongoTemplate.remove(
                Query.query(Criteria.where("_id").`in`(previousDocumentIds)),
                collectionName,
            )
        }

        logger.info(
            "{} - Rollback of block {} completed: \n- {} documents rolled back \n- {} documents deleted",
            clazz.simpleName,
            blockNumber,
            rollback.modifiedCount,
            rollback.deletedCount,
        )
    }

    /** Get all non-archive documents greater than or equal to the block number */
    open fun getCurrentDocuments(blockNumber: Long): List<T> {
        val query =
            Query()
                .addCriteria(Criteria.where(IndexedDocument::blockNumber.name).gte(blockNumber))
                .addCriteria(Criteria.where("_isArchive").ne(true))

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

        // 1) Match archive docs in the main collection
        val matchCriteria = Criteria.where("_isArchive").`is`(true).and("blockNumber").lt(endBlock)
        if (idsToPrune != null && idsToPrune.isNotEmpty()) {
            matchCriteria.and("_originalDocId").`in`(idsToPrune)
        }

        // 2) Rank by _originalDocId/version (desc)
        val setWindowFields =
            RawStage(
                Document(
                    "\$setWindowFields",
                    Document()
                        .append("partitionBy", "\$_originalDocId")
                        .append("sortBy", Document("version", -1))
                        .append("output", Document("rn", Document("\$documentNumber", Document()))),
                )
            )

        // 3) Build pipeline
        val pipeline =
            Aggregation.newAggregation(
                    Aggregation.match(matchCriteria),
                    Aggregation.sort(
                        Sort.by(Sort.Order.asc("_originalDocId"), Sort.Order.desc("version"))
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

        val stream = mongoTemplate.aggregateStream(pipeline, collectionName, Document::class.java)
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
        mongoTemplate.remove(Query.query(Criteria.where("_id").`in`(records)), collectionName)
    }
}
