package org.vechain.indexer

import org.vechain.indexer.repos.BaseIndexedRepo
import org.vechain.indexer.service.ThorService
import org.vechain.thor.model.Block

abstract class VeWorldIndexer(
    private val thorService: ThorService,
    private val repo: BaseIndexedRepo<*>
) : Indexer(thorService.getBlock(0).id) {

    override fun getBlockFromChain(blockNumber: Long): Block {
        return thorService.getBlock(blockNumber)
    }

    override fun getBestBlockFromChain(): Block {
        return thorService.getBestBlock()
    }

    override fun getLastSyncedBlock(): Block {
        repo.getMaxBlockNumber()?.let {
            return getBlockFromChain(it)
        }
        return getBlockFromChain(0)
    }

    override fun purgeRecords(blockNumber: Long) {
        repo.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
