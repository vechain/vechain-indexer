package org.vechain.indexer

import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.DeleteOneModel
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.WriteModel
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate

object InlineVersionService {

    @PublishedApi internal val logger = LoggerFactory.getLogger(InlineVersionService::class.java)

    @PublishedApi internal const val PREVIOUS_VERSIONS_FIELD = "_previousVersions"

    /**
     * Performs bulk upserts with inline versioning. For each updated document, if an existing
     * version is provided, the old version is prepended to the `_previousVersions` array on the
     * document itself. The array is trimmed by block window and capped at maxVersions.
     *
     * Uses the raw MongoDB driver because Spring Data's BulkOperations.upsert() does not support
     * aggregation pipeline updates.
     */
    inline fun <reified T : VersionedDocument> bulkUpsertWithVersions(
        updated: List<T>,
        existing: List<T>,
        mongoTemplate: MongoTemplate,
        blockWindow: Long,
        maxVersions: Int,
        initialVersion: Int,
        collectionName: String,
    ) {
        if (updated.isEmpty()) return

        val collection = mongoTemplate.getCollection(collectionName)
        val existingById = existing.associateBy { it.getDocumentId() }

        val writes = mutableListOf<WriteModel<Document>>()

        for (doc in updated) {
            val docId = doc.getDocumentId()
            val newBson = Document().also { mongoTemplate.converter.write(doc, it) }
            val id = newBson.remove("_id")
            // Strip _previousVersions from the new document fields (defense-in-depth)
            newBson.remove(PREVIOUS_VERSIONS_FIELD)

            val filter = Filters.eq("_id", id)
            val old = existingById[docId]

            if (old != null) {
                // Build old doc BSON, stripping _previousVersions to prevent nesting
                val oldBson = Document().also { mongoTemplate.converter.write(old, it) }
                oldBson.remove("_id")
                oldBson.remove(PREVIOUS_VERSIONS_FIELD)

                val fieldsToUnset =
                    oldBson.keys
                        .minus(newBson.keys)
                        .filterNot { it == PREVIOUS_VERSIONS_FIELD }
                        .sorted()

                // Stage 1: Set all new fields + prepend old version to _previousVersions
                val setFields = Document(newBson)
                setFields[PREVIOUS_VERSIONS_FIELD] =
                    Document(
                        "\$concatArrays",
                        listOf(
                            listOf(oldBson),
                            Document(
                                "\$ifNull",
                                listOf("\$$PREVIOUS_VERSIONS_FIELD", emptyList<Any>()),
                            ),
                        ),
                    )
                val stages = mutableListOf<Document>()
                if (fieldsToUnset.isNotEmpty()) {
                    stages += Document("\$unset", fieldsToUnset)
                }
                stages += Document("\$set", setFields)

                // Stage 2: Trim _previousVersions by block window with guaranteed minimum of 1,
                // then apply hard cap
                val currentBlock = doc.blockNumber
                val filterExpr =
                    Document(
                        "\$filter",
                        Document("input", "\$$PREVIOUS_VERSIONS_FIELD")
                            .append(
                                "cond",
                                Document(
                                    "\$gte",
                                    listOf("$\$this.blockNumber", currentBlock - blockWindow),
                                ),
                            ),
                    )

                // If filter result is empty, keep at least the first (most recent) entry
                val trimmedWithMinimum =
                    Document(
                        "\$cond",
                        Document("if", Document("\$gt", listOf(Document("\$size", filterExpr), 0)))
                            .append("then", filterExpr)
                            .append(
                                "else",
                                Document("\$slice", listOf("\$$PREVIOUS_VERSIONS_FIELD", 1)),
                            ),
                    )

                // Apply hard cap
                val capped = Document("\$slice", listOf(trimmedWithMinimum, maxVersions))

                stages += Document("\$set", Document(PREVIOUS_VERSIONS_FIELD, capped))

                writes.add(UpdateOneModel(filter, stages, UpdateOptions().upsert(true)))
            } else {
                if (doc.version > initialVersion) {
                    logger.error(
                        "Versioned document invariant violated for {}: _id={}, version={}, initialVersion={}, blockNumber={}",
                        collectionName,
                        docId,
                        doc.version,
                        initialVersion,
                        doc.blockNumber,
                    )
                    throw VersionedDocumentInvariantException(
                        "Refusing to write $collectionName/$docId at version ${doc.version} without a matching existing document (initialVersion=$initialVersion)"
                    )
                }
                // New document — just set fields, no _previousVersions manipulation
                val stage = Document("\$set", Document(newBson))
                writes.add(UpdateOneModel(filter, listOf(stage), UpdateOptions().upsert(true)))
            }
        }

        if (writes.isNotEmpty()) {
            try {
                collection.bulkWrite(writes, BulkWriteOptions().ordered(true))
            } catch (e: Exception) {
                logger.error(
                    "Bulk upsert with versions failed for {} ({} documents): {}",
                    T::class.java.simpleName,
                    updated.size,
                    e.message,
                )
                throw e
            }
        }
    }

    /**
     * Rolls back all documents in the given collection that have blockNumber >= [blockNumber].
     *
     * For each affected document:
     * - If version > 1 and _previousVersions is non-empty: restore the first entry from
     *   _previousVersions and set _previousVersions to the remaining entries.
     * - If version <= initialVersion (1): delete the document.
     * - If version > 1 but _previousVersions is empty: throw RollbackException.
     *
     * Uses raw MongoDB driver to bypass FilteringMongoTemplate's projection exclusion.
     */
    fun rollback(
        collectionName: String,
        blockNumber: Long,
        mongoTemplate: MongoTemplate,
        initialVersion: Int = 1,
    ) {
        val collection = mongoTemplate.getCollection(collectionName)

        // Find all documents where blockNumber >= blockNumber, including _previousVersions
        val filter = Filters.gte("blockNumber", blockNumber)
        val documents = collection.find(filter).toList()

        if (documents.isEmpty()) {
            logger.info("{}: No documents to rollback for block {}", collectionName, blockNumber)
            return
        }

        val writes = mutableListOf<WriteModel<Document>>()

        for (doc in documents) {
            val docId = doc["_id"]
            val version = doc.getInteger("version", 1)
            val idFilter = Filters.eq("_id", docId)

            if (version > initialVersion) {
                @Suppress("UNCHECKED_CAST")
                val previousVersions =
                    doc.get(PREVIOUS_VERSIONS_FIELD) as? List<Document> ?: emptyList()

                if (previousVersions.isEmpty()) {
                    logger.error(
                        "Could not find previous version for rollback ({}): _id={}, version={}, blockNumber={}",
                        collectionName,
                        docId,
                        version,
                        doc.getLong("blockNumber"),
                    )
                    throw RollbackException(
                        "Could not find previous document for rollback: collection=$collectionName, _id=$docId, version=$version, blockNumber=${doc.getLong("blockNumber")}"
                    )
                }

                // Take the first entry as the restored version
                val restored = Document(previousVersions[0])
                // Strip any _previousVersions from the restored entry (defense-in-depth)
                restored.remove(PREVIOUS_VERSIONS_FIELD)

                // Build replacement: restored fields + original _id + remaining versions
                val replacement = Document(restored)
                replacement["_id"] = docId
                val remaining =
                    if (previousVersions.size > 1) {
                        previousVersions.subList(1, previousVersions.size)
                    } else {
                        emptyList()
                    }
                replacement[PREVIOUS_VERSIONS_FIELD] = remaining

                writes.add(ReplaceOneModel(idFilter, replacement))
            } else {
                // Version <= initialVersion means this was a new doc, so delete it
                writes.add(DeleteOneModel(idFilter))
            }
        }

        if (writes.isNotEmpty()) {
            val result = collection.bulkWrite(writes, BulkWriteOptions().ordered(true))
            logger.info(
                "{} - Rollback of block {} completed: {} documents replaced, {} documents deleted",
                collectionName,
                blockNumber,
                result.modifiedCount,
                result.deletedCount,
            )
        }
    }
}
