package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.ContractArchive
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.*
import org.vechain.indexer.utils.BlockUtils.extractMasterChangeEvents

@Profile("contracts")
@Component
open class ContractIndexer(
    private val contractService: ContractService,
    contractArchiveService: ArchiveService<IndexedContract, ContractArchive>,
    contractRepository: ContractRepository,
    thorClient: ThorClient,
    @Value("\${indexer.startBlock.contracts}") private val startBlock: Long,
    @Value("\${indexer.pruner.removalChunkSize}") private val prunerRemovalChunkSize: Int,
    @Value("\${indexer.syncLogInterval.contracts}") private val syncLogInterval: Long,
) :
    StatefulIndexer<IndexedContract, ContractArchive>(
        repository = contractRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        archiveService = contractArchiveService,
        prunerRemovalChunkSize = prunerRemovalChunkSize
    ) {

    override fun processBlock(block: Block) {
        // Extract any relevant data from the block
        val data = extractMasterChangeEvents(block)
        if (data.isEmpty()) return

        // Find any existing records
        val existing = contractService.getExisting(data.map { (event) -> event.address })

        // Process the updated records
        val updated = contractService.parseContracts(block, data, existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            contractService.update(updated, existing)
        }
    }
}
