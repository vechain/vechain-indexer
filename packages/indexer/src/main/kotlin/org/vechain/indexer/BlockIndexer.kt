package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService

@Profile("blocks")
@Component
open class BlockIndexer(thorService: ThorService, private val blockRepo: BlockRepo, mongoTemplate: MongoTemplate) :
    Indexer(thorService, blockRepo, mongoTemplate) {

    override fun processBlock(block: Block) {
        blockRepo.save(block)
    }

}
