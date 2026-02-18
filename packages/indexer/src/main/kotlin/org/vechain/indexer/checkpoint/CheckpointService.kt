package org.vechain.indexer.checkpoint

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import java.lang.Long.max
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexedDocument.Companion.CHECKPOINT_ID
import org.vechain.indexer.thor.model.BlockIdentifier

// Always run the checkpoint this many blocks behind the current block
const val CHECKPOINT_BUFFER = 180L

@Service
open class CheckpointService(private val mongoTemplate: MongoTemplate) {

    fun saveCheckpoint(collectionName: String, blockNumber: Long) {
        val doc =
            Document()
                .append("_id", CHECKPOINT_ID)
                .append("checkpointBlockNumber", max(0L, blockNumber - CHECKPOINT_BUFFER))
        mongoTemplate
            .getCollection(collectionName)
            .replaceOne(Filters.eq("_id", CHECKPOINT_ID), doc, ReplaceOptions().upsert(true))
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
