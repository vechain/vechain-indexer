package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.data.mongodb.core.query.Update.update
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedBlock
import org.vechain.indexer.repository.BlockRepository
import org.vechain.indexer.service.ThorService
import org.vechain.thor.model.Block

@Profile("blocks")
@Component
open class BlockIndexer(
    private val thorService: ThorService,
    private val blockRepository: BlockRepository,
    private val mongoTemplate: MongoTemplate,
    thorClient: ThorClient,
    @Value("\${indexer.startBlock.blocks}") startBlock: Long,
    @Value("\${indexer.syncLoggerInterval.blocks}") private val syncLoggerInterval: Long,
) : VeWorldIndexer(
    thorClient = thorClient,
    startBlock = startBlock,
    repository = blockRepository,
    syncLoggerInterval = syncLoggerInterval
) {


    override fun rollback(blockNumber: Long) {
        blockRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }

    override fun processBlock(block: Block) {

        // Every 180 blocks do a finality check
        if (block.number % 180 == 0L) {
            finalityCheck()
        }

        blockRepository.save(IndexedBlock(block))
    }

    private fun finalityCheck() {
        blockRepository.findTopByIsFinalizedOrderByBlockNumberAsc(false)?.let {
            val finalityBlock = thorService.getFinalisedBlock()
            if (finalityBlock.number > it.blockNumber) {
                logger.info(
                    "Finalising blocks in range ${it.blockNumber} - ${finalityBlock.number}"
                )

                mongoTemplate.updateMulti(
                    query(where("blockNumber").gte(it.blockNumber).lte(finalityBlock.number)),
                    update("isFinalized", true),
                    IndexedBlock::class.java
                )

                return
            }
        }
    }
}
