package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.thor.model.BlockIdentifier

/** One collection plus its `__checkpoint__` document. Resume is the higher of the two. */
open class MongoIndexerStore(
    private val repository: BaseIndexedRepository<*, *>,
    protected val checkpointService: CheckpointService,
    val collectionName: String,
    private val indexerName: String,
) : IndexerStore {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun lastSynced(): BlockIdentifier? {
        val checkpoint = checkpointService.getCheckpoint(collectionName)
        val latestRecord =
            try {
                repository.getLatestRecord()?.let {
                    BlockIdentifier(number = it.blockNumber, id = it.blockId)
                }
            } catch (e: Exception) {
                logger.error(
                    "Failed to get latest record for {} (collection: {})",
                    indexerName,
                    collectionName,
                    e,
                )
                throw e
            }
        return listOfNotNull(latestRecord, checkpoint).maxByOrNull { it.number }
    }

    override fun rollbackFrom(blockNumber: Long) {
        checkpointService.saveCheckpoint(collectionName, blockNumber - 1)
        deleteFrom(blockNumber)
    }

    protected open fun deleteFrom(blockNumber: Long) {
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)
    }
}

/** Versioned collections restore the pre-block version of each document instead of deleting. */
class VersionedMongoIndexerStore(
    repository: BaseIndexedRepository<*, *>,
    private val mongoTemplate: MongoTemplate,
    checkpointService: CheckpointService,
    collectionName: String,
    indexerName: String,
) : MongoIndexerStore(repository, checkpointService, collectionName, indexerName) {

    override fun deleteFrom(blockNumber: Long) {
        InlineVersionService.rollback(
            collectionName,
            blockNumber,
            mongoTemplate,
            VersionedDocumentInitialVersions.forCollection(collectionName),
        )
    }
}
