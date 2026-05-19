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
        minVersions: Int,
        initialVersion: Int,
        collectionName: String,
    ) {
        if (updated.isEmpty()) return

        val collection = mongoTemplate.getCollection(collectionName)
        val existingGroupedById = existing.groupBy { it.getDocumentId() }

        val writes = mutableListOf<WriteModel<Document>>()

        for (doc in updated) {
            val docId = doc.getDocumentId()
            val newBson = Document().also { mongoTemplate.converter.write(doc, it) }
            val id = newBson.remove("_id")
            // Strip _previousVersions from the new document fields (defense-in-depth)
            newBson.remove(PREVIOUS_VERSIONS_FIELD)

            val filter = Filters.eq("_id", id)
            val oldVersions = existingGroupedById[docId].orEmpty()

            if (oldVersions.isNotEmpty()) {
                // Preserve every prior snapshot for the document from this save call.
                val oldBsons =
                    oldVersions.asReversed().map { old ->
                        Document()
                            .also { mongoTemplate.converter.write(old, it) }
                            .apply {
                                remove("_id")
                                remove(PREVIOUS_VERSIONS_FIELD)
                            }
                    }

                val fieldsToUnset =
                    oldBsons
                        .flatMap { it.keys }
                        .toSet()
                        .minus(newBson.keys)
                        .minus(setOf("_id", PREVIOUS_VERSIONS_FIELD))

                // Stage 1: Set all new fields + prepend every old version to _previousVersions
                val setFields = Document(newBson)
                setFields[PREVIOUS_VERSIONS_FIELD] =
                    Document(
                        "\$concatArrays",
                        listOf(
                            oldBsons,
                            Document(
                                "\$ifNull",
                                listOf("\$$PREVIOUS_VERSIONS_FIELD", emptyList<Any>()),
                            ),
                        ),
                    )
                val stage1 = Document("\$set", setFields)

                // Stage 2: Remove fields that were present before but are intentionally absent now.
                val stage2 =
                    fieldsToUnset
                        .takeIf { it.isNotEmpty() }
                        ?.let { Document("\$unset", it.toList()) }

                // Stage 3: Trim _previousVersions by block window with a floor of minVersions
                // entries (rollback safety horizon), then apply the maxVersions hard cap.
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

                // If the block-window filter retains fewer than minVersions entries, fall back
                // to the most recent minVersions slice instead. This preserves at least
                // minVersions entries when that many prior versions exist; otherwise it keeps
                // all available prior versions, including for infrequently updated documents
                // whose older history has aged out of the window.
                val trimmedWithMinimum =
                    Document(
                        "\$cond",
                        Document(
                                "if",
                                Document(
                                    "\$gte",
                                    listOf(Document("\$size", filterExpr), minVersions),
                                ),
                            )
                            .append("then", filterExpr)
                            .append(
                                "else",
                                Document(
                                    "\$slice",
                                    listOf("\$$PREVIOUS_VERSIONS_FIELD", minVersions),
                                ),
                            ),
                    )

                // Apply hard cap
                val capped = Document("\$slice", listOf(trimmedWithMinimum, maxVersions))

                val stage3 = Document("\$set", Document(PREVIOUS_VERSIONS_FIELD, capped))

                val pipeline = listOfNotNull(stage1, stage2, stage3)

                writes.add(UpdateOneModel(filter, pipeline, UpdateOptions().upsert(true)))
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

                // Walk _previousVersions (newest first) and pick the first snapshot that predates
                // the rollback target. Single-step rollback breaks for any depth greater than one
                // update: e.g. a doc updated at blocks 100/200/300/400, rolling back to 250 with
                // previousVersions=[v3@300, v2@200, v1@100] must restore v2 (not v3@300, which is
                // still in the rolled-back range).
                val restoreIndex =
                    previousVersions.indexOfFirst { entry ->
                        val bn = (entry.get("blockNumber") as? Number)?.toLong()
                        bn != null && bn < blockNumber
                    }

                if (restoreIndex < 0) {
                    // No retained snapshot predates the target. Two sub-cases:
                    //
                    // 1. The oldest retained snapshot IS the document's birth (its version equals
                    //    initialVersion). The doc came into existence inside the rolled-back range
                    //    and had no pre-target state — delete it, mirroring how the version <=
                    //    initialVersion branch below treats brand-new docs.
                    //
                    // 2. The oldest retained snapshot is itself > initialVersion. Earlier history
                    //    was trimmed by the blockWindow / maxVersions caps, so we cannot tell
                    //    whether the doc existed before the target. Throw so the operator decides;
                    //    indexer-core's alignComponents will aggregate this into its actionable
                    //    "drop state for these indexers" error.
                    val oldestEntry = previousVersions.last()
                    val oldestRetainedVersion = oldestEntry.getInteger("version", initialVersion)
                    val oldestRetainedBlock = (oldestEntry.get("blockNumber") as? Number)?.toLong()
                    if (oldestRetainedVersion <= initialVersion) {
                        logger.info(
                            "Rolling back ({}): _id={} born within target range (current v{}, oldest retained v{} >= {}), deleting",
                            collectionName,
                            docId,
                            version,
                            oldestRetainedVersion,
                            blockNumber,
                        )
                        writes.add(DeleteOneModel(idFilter))
                        continue
                    }
                    val currentBlock = doc.getLong("blockNumber")
                    logger.error(
                        "Retained versions exhausted for rollback ({}): _id={}, current v{} @ block {}, retained={}, oldest retained v{} @ block {}, target={}",
                        collectionName,
                        docId,
                        version,
                        currentBlock,
                        previousVersions.size,
                        oldestRetainedVersion,
                        oldestRetainedBlock,
                        blockNumber,
                    )
                    throw RollbackException(
                        "Retained versions exhausted rolling back $collectionName/$docId to block $blockNumber: " +
                            "every retained snapshot is at block >= $blockNumber and pre-target history was trimmed " +
                            "(retained=${previousVersions.size}, current v$version @ block $currentBlock, " +
                            "oldest retained v$oldestRetainedVersion @ block $oldestRetainedBlock)"
                    )
                }

                // Take the chosen entry as the restored version
                val restored = Document(previousVersions[restoreIndex])
                // Strip any _previousVersions from the restored entry (defense-in-depth)
                restored.remove(PREVIOUS_VERSIONS_FIELD)

                // Build replacement: restored fields + original _id + remaining (older) versions
                val replacement = Document(restored)
                replacement["_id"] = docId
                val remaining =
                    if (restoreIndex + 1 < previousVersions.size) {
                        previousVersions.subList(restoreIndex + 1, previousVersions.size)
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
