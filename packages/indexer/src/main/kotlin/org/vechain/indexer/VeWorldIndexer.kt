package org.vechain.indexer

import org.vechain.indexer.repos.BaseIndexedRepo
import org.vechain.indexer.service.ThorService

abstract class VeWorldIndexer(
    thorService: ThorService,
    private val repo: BaseIndexedRepo<*>,
    thorUrl: String,
) : Indexer(thorService.getBlock(0).id, thorUrl) {

    override fun getLastSyncedBlockNumber(): Long {
        repo.getLatestRecord()?.let {
            return it.blockNumber
        }
        return 0
    }

    override fun purgeRecords(blockNumber: Long) {
        repo.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
