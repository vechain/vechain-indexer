package org.vechain.indexer.stargate.nftOwnerBalance

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.stargate.nftHolders.NftOwnerBalanceRepository

@Profile("stargate", "nft-owner-balance")
@Component
open class NftOwnerBalanceProcessor(
    private val service: NftOwnerBalanceService,
    repository: NftOwnerBalanceRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.NFT_OWNER_BALANCE.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NFT_OWNER_BALANCE.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val newRecords = service.processEvents(entry.events())

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }
}
