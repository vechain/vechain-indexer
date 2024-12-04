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
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils.extractMasterChangeEvents

@Profile("contracts")
@Component
open class ContractIndexer(
    private val contractService: ContractService,
    private val contractRepository: ContractRepository,
    contractArchiveService: ArchiveService<IndexedContract, ContractArchive>,
    @Value("\${indexer.startBlock.contracts}") private val startBlock: Long,
    @Value("\${indexer.syncLogInterval.contracts}") private val syncLogInterval: Long,
    @Value("\${indexer.pruner.enabled}") private val prunerEnabled: Boolean,
    @Value("\${indexer.pruner.interval}") private val prunerInterval: Long,
    thorClient: ThorClient,
) :
    StatefulIndexer<IndexedContract, ContractArchive>(
        repository = contractRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        prunerEnabled = prunerEnabled,
        prunerInterval = prunerInterval,
        archiveService = contractArchiveService
    ) {

    override fun processBlock(block: Block) {

        // Get the master change events from the block
        val masterChangeEvents = extractMasterChangeEvents(block)
        if (masterChangeEvents.isEmpty()) return

        // Check for existing documents
        val existingContracts =
            contractRepository
                .findAllById(masterChangeEvents.map { (event) -> event.address })
                .toList()

        // Parse the contracts
        val contracts = contractService.parseContracts(block, masterChangeEvents, existingContracts)

        contractService.saveContracts(current = contracts, archived = existingContracts)
    }
}
