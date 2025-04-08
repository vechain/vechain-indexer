package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.pruner.Pruner
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("nft-events")
@Component
open class NFTEventIndexer(
    private val nftService: NFTService,
    nftArchiveService: ArchiveService<IndexedNFT, NFTArchive>,
    thorClient: ThorClient,
    nftRepository: NFTRepository,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.nfts}") startBlock: Long,
    @Value("\${indexer.pruner.removalChunkSize}") private val prunerRemovalChunkSize: Int,
    @Value("\${indexer.syncLogInterval.nfts}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.nfts}") private val syncBlockBatchSize: Long,
) :
    StatefulLogsIndexer<IndexedNFT, NFTArchive>(
        repository = nftRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        archiveService = nftArchiveService,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT),
        abiManager = abiManager,
        businessEventManager = null,
        pruner = Pruner(NFTArchive::class, nftArchiveService, prunerRemovalChunkSize),
    ) {
    override fun processLogs(
        events: List<EventLog>,
        transfers: List<TransferLog>,
    ) {
        // Extract any relevant data from the block
        val nftEvents =
            processBlockGenericEvents(
                events,
                transfers,
                FilterCriteria(
                    abiNames = listOf("erc721"),
                    eventNames = listOf("Transfer"),
                ),
            )
        if (nftEvents.isEmpty()) return

        // Find any existing records
        val existing = nftService.getExisting(nftEvents)

        // Process the updated records
        val updated = nftService.parseRecords(nftEvents, existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            nftService.update(updated, existing)
        }
    }
}
