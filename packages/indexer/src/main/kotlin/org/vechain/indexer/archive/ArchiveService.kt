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

    private val collectionName: String
        get() = mongoTemplate.getCollectionName(clazz)

    open fun getPreviousVersionId(document: VersionedDocument): String =
        buildArchiveId(document, document.version - 1)

    open fun saveAll(documents: List<T>) {
        if (documents.isEmpty()) return

        val bsonDocs =
            documents.map { doc ->
                val bson = Document()
                mongoTemplate.converter.write(doc, bson)
                val archiveId = buildArchiveId(doc, doc.version)
                bson["_originalDocId"] = bson["_id"]
                bson["_id"] = archiveId
                bson["_isArchive"] = true
                bson
            }

        mongoTemplate
            .getCollection(collectionName)
            .insertMany(bsonDocs, InsertManyOptions().ordered(false))
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun rollback(blockNumber: Long) {
        // Step 1: Find main docs >= blockNumber (template auto-excludes archives)
        val currentDocuments = getCurrentDocuments(blockNumber)

        if (currentDocuments.isEmpty()) {
            logger.info("{}: No documents to rollback for block {}", clazz.simpleName, blockNumber)
            return
        }

        // Step 2: Find archive docs by composite _id via raw driver (bypasses template filtering)
        val previousDocumentIds =
            currentDocuments.filter { it.version > 1 }.map { getPreviousVersionId(it) }

        val previousDocuments =
            if (previousDocumentIds.isNotEmpty()) {
                mongoTemplate
                    .getCollection(collectionName)
                    .find(Filters.`in`("_id", previousDocumentIds))
                    .map { bson ->
                        // Strip archive fields and restore original _id
                        val originalDocId = bson.remove("_originalDocId")
                        bson.remove("_isArchive")
                        bson["_id"] = originalDocId
                        mongoTemplate.converter.read(clazz, bson)
                    }
                    .associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        // Step 3: Build bulk replace/delete operations
        val rollbackOperations = getRollbackOperation(currentDocuments, previousDocuments)

        // Step 4: Execute bulk operations
        val rollback = rollbackOperations.execute()

        // Step 5: Delete used archives via raw driver
        if (previousDocumentIds.isNotEmpty()) {
            mongoTemplate
                .getCollection(collectionName)
                .deleteMany(Filters.`in`("_id", previousDocumentIds))
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

        // Use raw driver to bypass FilteringMongoTemplate archive exclusion

        // 1) Match: _isArchive: true and blockNumber < endBlock
        val matchDoc =
            Document("_isArchive", true).append("blockNumber", Document("\$lt", endBlock))
        if (idsToPrune != null && idsToPrune.isNotEmpty()) {
            matchDoc.append("_originalDocId", Document("\$in", idsToPrune))
        }

        // 2) Build raw aggregation pipeline
        val pipeline =
            listOf(
                Document("\$match", matchDoc),
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
            mongoTemplate
                .getCollection(collectionName)
                .aggregate(pipeline)
                .allowDiskUse(true)
                .batchSize(batchSize)
                .cursor()

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
        mongoTemplate.getCollection(collectionName).deleteMany(Filters.`in`("_id", records))
    }
}
