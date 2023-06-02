package org.vechain.indexer

import org.vechain.indexer.repos.BaseIndexedRepo
import org.vechain.indexer.service.ThorService
import org.vechain.thor.model.Block

abstract class VeWorldIndexer(
    private val thorService: ThorService,
    private val repo: BaseIndexedRepo<*>
) : Indexer(thorService.getBlock(0).id) {

    override fun getBlockFromChain(blockNumber: Long?): Block {
        blockNumber?.let { return thorService.getBlock(it) }
        return thorService.getBestBlock()
    }

    override fun getLastSyncedBlock(): Block {
        repo.getMaxBlockNumber()?.let {
            return getBlockFromChain(it)
        }
        return getBlockFromChain(0)
    }

    override fun purgeRecords(startBlock: Long, endBlock: Long) {
        repo.deleteAllByBlockNumberBetween(startBlock, endBlock)
    }
}
