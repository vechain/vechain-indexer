package org.vechain.indexer.checkpoint

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.model.BlockIdentifier

@Service
open class CheckpointService(private val mongoTemplate: MongoTemplate) {

    companion object {
        const val CHECKPOINT_ID = "__checkpoint__"
        const val CHECKPOINT_INTERVAL = 100
    }

    fun saveCheckpoint(collectionName: String, blockNumber: Long, blockId: String) {
        val doc =
            Document()
                .append("_id", CHECKPOINT_ID)
                .append("blockNumber", blockNumber)
                .append("blockId", blockId)
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
        val blockNumber = doc.getLong("blockNumber") ?: return null
        val blockId = doc.getString("blockId") ?: ""
        return BlockIdentifier(number = blockNumber, id = blockId)
    }

    fun deleteCheckpoint(collectionName: String) {
        mongoTemplate.getCollection(collectionName).deleteOne(Filters.eq("_id", CHECKPOINT_ID))
    }
}
