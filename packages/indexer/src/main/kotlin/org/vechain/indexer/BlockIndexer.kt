package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedBlock
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService
import org.vechain.thor.model.Block

@Profile("blocks")
@Component
open class BlockIndexer(
    private val thorService: ThorService,
    private val blockRepo: BlockRepo,
) :
    VeWorldIndexer(thorService, blockRepo) {

    override fun processBlock(block: Block) {

        // Every 180 blocks do a finality check
        if (block.number % 180 == 0L) {
            finalityCheck()
        }

        blockRepo.save(IndexedBlock(block))
    }

    private fun finalityCheck() {
        blockRepo.getLowestUnfinalisedBlock()?.let {
            val finalityBlock = thorService.getFinalisedBlock()
            if (finalityBlock.number > it) {
                logger.info("Finalising blocks in range $it - ${finalityBlock.number}")
                blockRepo.updateAllIsFinalizedByBlockNumberBetween(true, it - 1, finalityBlock.number + 1)
                return
            }
        }
    }

}
