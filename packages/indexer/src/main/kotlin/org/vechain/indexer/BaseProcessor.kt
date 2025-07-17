package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.thor.model.BlockIdentifier

abstract class BaseProcessor(private val repository: BaseIndexedRepository<*, *>) :
    IndexerProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun getLastSyncedBlock(): BlockIdentifier? {
        repository.getLatestRecord()?.let {
            return BlockIdentifier(number = it.blockNumber, id = it.blockId)
        }
        logger.info("No records found in repository, returning null")
        return null
    }

    override fun rollback(blockNumber: Long) =
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
}
