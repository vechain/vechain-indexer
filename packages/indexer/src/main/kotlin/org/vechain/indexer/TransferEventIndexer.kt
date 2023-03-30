package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.repos.NFTRepo
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils

@Profile("transfer-event-indexer", "prod")
@Component
open class TransferEventIndexer(
    private val thorService: ThorService,
    private val transferEventRepo: TransferEventRepo,
    private val nftRepo: NFTRepo
) : Indexer() {

    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)

        val transferEvents = BlockUtils.getTransferEvents(block)

        if (transferEvents.isNotEmpty()) transferEventRepo.saveAll(transferEvents)
    }

    override fun getStartingBlock(): Long {

        val lastTransfersBlock = transferEventRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
        val lastNFTBlock = nftRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0

        return minOf(lastTransfersBlock, lastNFTBlock)
    }

}