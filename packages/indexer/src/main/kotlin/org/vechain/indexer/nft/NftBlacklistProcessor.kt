package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.StatefulMongoProcessor
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.nft.backfill.NftBlacklistBackfillService

@Profile("nfts", "history")
@Component
open class NftBlacklistProcessor(
    private val service: NftBlacklistService,
    private val backfillService: NftBlacklistBackfillService,
    repository: NftBlacklistRepository,
    private val mongoTemplate: MongoTemplate,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    StatefulMongoProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.NFT_BLACKLIST.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NFT_BLACKLIST.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val (updated, existing) = service.processBlock(entry.events())
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.save(updated, existing)
        }
    }

    /**
     * Rows flagged from a reverted event are re-flagged from whatever state the rollback leaves.
     */
    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        val touched =
            mongoTemplate.find(byBlockNumberFrom(blockNumber), NftBlacklist::class.java).map {
                it.id
            }
        super.rollback(blockNumber)
        if (touched.isEmpty()) return
        val restored =
            mongoTemplate
                .find(Query(Criteria.where("_id").`in`(touched)), NftBlacklist::class.java)
                .associateBy { it.id }
        val states = touched.map { id ->
            restored[id]
                ?: NftBlacklist(
                    id = id,
                    isBlacklisted = false,
                    blockId = "",
                    blockNumber = blockNumber - 1,
                    blockTimestamp = 0,
                    version = 0,
                )
        }
        backfillService.enqueue(states)
    }

    private fun byBlockNumberFrom(blockNumber: Long) =
        Query(Criteria.where(NftBlacklist::blockNumber.name).gte(blockNumber))
}
