package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.NFTBlacklist
import org.vechain.indexer.model.NFTBlacklistArchive
import org.vechain.indexer.pruner.Pruner
import org.vechain.indexer.repository.NFTBlacklistRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.NFTBlacklistService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("nft-events")
@Component
open class NFTBlacklistIndexer(
    private val nftBlacklistService: NFTBlacklistService,
    nftBlacklistArchiveService: ArchiveService<NFTBlacklist, NFTBlacklistArchive>,
    thorClient: ThorClient,
    repository: NFTBlacklistRepository,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.nft_blacklist}") startBlock: Long,
    @Value("\${indexer.blacklist.contract_address}") private val blacklistContract: String,
    @Value("\${indexer.pruner.removalChunkSize}") private val prunerRemovalChunkSize: Int,
    @Value("\${indexer.syncLogInterval.nfts}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.nfts}") private val syncBlockBatchSize: Long,
) :
    StatefulLogsIndexer<NFTBlacklist, NFTBlacklistArchive>(
        repository = repository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        archiveService = nftBlacklistArchiveService,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT),
        abiManager = abiManager,
        businessEventManager = null,
        pruner =
            Pruner(NFTBlacklistArchive::class, nftBlacklistArchiveService, prunerRemovalChunkSize),
    ) {
    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        if (events.isEmpty()) return

        // Extract any relevant data from the block
        val nftEvents =
            processBlockGenericEvents(
                events,
                transfers,
                FilterCriteria(
                    abiNames = listOf("NFTBlacklist"),
                    eventNames = listOf("NFTBlacklisted"),
                    contractAddresses = listOf(blacklistContract),
                ),
            )
        if (nftEvents.isEmpty()) return

        // Find any existing records
        val existing = nftBlacklistService.getExisting(nftEvents)

        // Process the updated records
        val updated = nftBlacklistService.parseRecords(nftEvents, existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            nftBlacklistService.update(updated, existing)
        }
    }
}
