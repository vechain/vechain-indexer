package org.vechain.indexer

import org.vechain.indexer.thor.model.Block

object SimpleBlockIndexerCoordinator {
    suspend fun launch(indexer: BlockIndexer, blocks: List<Block>) {
        // Initialize
        indexer.initialise()

        // Process blocks
        blocks.forEach { block -> indexer.processBlock(block) }
    }
}
