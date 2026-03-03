package org.vechain.indexer.checkpoint

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import java.util.concurrent.ConcurrentHashMap
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexedDocument.Companion.CHECKPOINT_ID
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.thor.model.BlockIdentifier

@Service
open class CheckpointService(
    private val mongoTemplate: MongoTemplate,
    private val checkpointProperties: CheckpointProperties,
) {

    private val logger = LoggerFactory.getLogger(CheckpointService::class.java)
    private val lastSaveTimeNanos = ConcurrentHashMap<String, Long>()

    fun saveCheckpoint(collectionName: String, blockNumber: Long) {
        val doc =
            Document().append("_id", CHECKPOINT_ID).append("checkpointBlockNumber", blockNumber)
        mongoTemplate
            .getCollection(collectionName)
            .replaceOne(Filters.eq("_id", CHECKPOINT_ID), doc, ReplaceOptions().upsert(true))
    }

    /**
     * Best-effort, throttled checkpoint save. Skips the save if the configured interval has not
     * elapsed since the last successful save for this collection. Failures are logged as warnings
     * rather than propagated.
     */
    fun trySaveCheckpoint(collectionName: String, blockNumber: Long) {
        val now = System.nanoTime()
        val intervalNanos = checkpointProperties.saveIntervalSeconds * 1_000_000_000L
        val lastSave = lastSaveTimeNanos[collectionName]

        if (lastSave != null && (now - lastSave) < intervalNanos) {
            return
        }

        try {
            saveCheckpoint(collectionName, blockNumber)
            lastSaveTimeNanos[collectionName] = now
        } catch (e: Exception) {
            logger.warn(
                "Failed to save checkpoint for {} at block {}",
                collectionName,
                blockNumber,
                e,
            )
        }
    }

    fun getCheckpoint(collectionName: String): BlockIdentifier? {
        val doc =
            mongoTemplate
                .getCollection(collectionName)
                .find(Filters.eq("_id", CHECKPOINT_ID))
                .first() ?: return null
        val blockNumber = doc.getLong("checkpointBlockNumber") ?: return null
        return BlockIdentifier(number = blockNumber, id = null)
    }

    fun deleteCheckpoint(collectionName: String) {
        mongoTemplate.getCollection(collectionName).deleteOne(Filters.eq("_id", CHECKPOINT_ID))
    }
}
