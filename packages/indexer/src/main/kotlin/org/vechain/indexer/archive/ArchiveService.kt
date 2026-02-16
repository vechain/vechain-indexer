package org.vechain.indexer.archive

import com.mongodb.client.model.Filters
import com.mongodb.client.model.InsertManyOptions
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext
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
    open val clazz: Class<T>,
    private val queryLimit: Long,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val collectionName: String by lazy { mongoTemplate.getCollectionName(clazz) }

    open fun getPreviousVersionId(document: VersionedDocument): String =
        buildArchiveId(document, document.version - 1)

    open fun saveAll(documents: List<T>) {
        if (documents.isEmpty()) return

        val bsonDocs =
            documents.map { doc ->
                val bson = Document()
                mongoTemplate.converter.write(doc, bson)
                val originalId = bson.remove("_id")
                val archiveId = buildArchiveId(doc, doc.version)
                bson["_id"] = archiveId
                bson["_isArchive"] = true
                bson["_originalDocId"] = originalId
                bson
            }

        mongoTemplate
            .getCollection(collectionName)
            .insertMany(bsonDocs, InsertManyOptions().ordered(false))
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun rollback(blockNumber: Long) {
        val currentDocuments = getCurrentDocuments(blockNumber)

        if (currentDocuments.isEmpty()) {
            logger.info("{}: No documents to rollback for block {}", clazz.simpleName, blockNumber)
            return
        }

        // Find archive documents for previous versions
        val previousArchiveIds =
            currentDocuments.filter { it.version > 1 }.map { getPreviousVersionId(it) }

        val collection = mongoTemplate.getCollection(collectionName)

        // Fetch archive docs using raw driver (bypasses FilteringMongoTemplate exclusion)
        val previousDocuments =
            if (previousArchiveIds.isNotEmpty()) {
                collection
                    .find(Filters.`in`("_id", previousArchiveIds))
                    .map { archiveDoc ->
                        // Strip archive fields and restore original _id
                        val originalDocId = archiveDoc.remove("_originalDocId")
                        archiveDoc.remove("_isArchive")
                        archiveDoc["_id"] = originalDocId
                        val entity = mongoTemplate.converter.read(clazz, archiveDoc)
                        entity.getDocumentId() to entity
                    }
                    .toList()
                    .toMap()
            } else {
                emptyMap()
            }

        val rollbackOperations = getRollbackOperation(currentDocuments, previousDocuments)

        val rollback = rollbackOperations.execute()

        // Remove the used archive documents
        if (previousArchiveIds.isNotEmpty()) {
            collection.deleteMany(Filters.`in`("_id", previousArchiveIds))
        }

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

        val collection = mongoTemplate.getCollection(collectionName)

        // Build match filter: _isArchive: true AND blockNumber < endBlock
        val matchFilter =
            Document("_isArchive", true).append("blockNumber", Document("\$lt", endBlock))
        if (idsToPrune != null && idsToPrune.isNotEmpty()) {
            matchFilter.append("_originalDocId", Document("\$in", idsToPrune))
        }

        // Build aggregation pipeline using raw driver
        val pipeline =
            listOf(
                Document("\$match", matchFilter),
                Document("\$sort", Document("_originalDocId", 1).append("version", -1)),
                Document(
                    "\$setWindowFields",
                    Document()
                        .append("partitionBy", "\$_originalDocId")
                        .append("sortBy", Document("version", -1))
                        .append("output", Document("rn", Document("\$documentNumber", Document()))),
                ),
                Document("\$match", Document("rn", Document("\$gt", 1))),
                Document("\$project", Document("_id", 1)),
                Document("\$limit", queryLimit),
            )

        val cursor =
            collection.aggregate(pipeline).allowDiskUse(true).batchSize(batchSize).iterator()

        return object : CloseableIterator<String> {
            override fun hasNext(): Boolean = cursor.hasNext()

            override fun next(): String = cursor.next().getString("_id")

            override fun remove() {
                throw UnsupportedOperationException("remove is not supported")
            }

            override fun close() {
                cursor.close()
            }
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun removeAll(records: List<String>) {
        logger.debug("Removing {} archives for {}", records.size, clazz.simpleName)
        val collection = mongoTemplate.getCollection(collectionName)
        collection.deleteMany(Filters.`in`("_id", records))
    }
}
