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
    thorService: ThorService,
    private val blockRepo: BlockRepo,
) :
    VeWorldIndexer(thorService, blockRepo) {

    override fun processBlock(block: Block) {
        blockRepo.save(IndexedBlock(block))
    }

}
