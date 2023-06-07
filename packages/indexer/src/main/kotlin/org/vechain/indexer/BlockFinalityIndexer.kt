package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.exception.FullySynchronisedException
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService
import org.vechain.thor.model.Block

@Profile("blocks")
@Component
class BlockFinalityIndexer(
    private val thorService: ThorService,
    private val blockRepo: BlockRepo
) : Indexer(thorService.getBlock(0).id) {

    override fun processBlock(block: Block) {
        // Check if the block is finalised
        if (!block.isFinalized) {
            throw FullySynchronisedException("Block ${block.number} is not finalised")
        }

        // Get the block from the repo
        val indexedBlock = blockRepo.findByBlockNumber(block.number)
            ?: throw FullySynchronisedException("Block ${block.number} not found in repo")

        // If the block is already finalised, throw an exception
        if (indexedBlock.isFinalized) throw Exception(
            "Block ${block.number} already finalised. " +
                    "It is likely that the block indexer is still syncing. Will attempt to restart the indexer."
        )

        // Update the block in the repo
        indexedBlock.isFinalized = true
        blockRepo.save(indexedBlock)
    }

    override fun getBlockFromChain(blockNumber: Long): Block {
        return thorService.getBlock(blockNumber)
    }

    override fun getLatestBlockFromChain(): Block {
        return thorService.getFinalisedBlock()
    }

    override fun getLastSyncedBlock(): Block {
        blockRepo.getLowestUnfinalisedBlock()?.let {
            return getBlockFromChain(it)
        }
        return getBlockFromChain(0)
    }

    override fun purgeRecords(blockNumber: Long) {
        logger.warn("BlockFinalityIndexer does not support purging records as it is a secondary indexer")
    }
}