package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.BlockUtils.extractMasterChangeEvents
import org.vechain.thor.model.Block

@Profile("contracts")
@Component
open class ContractIndexer(
    private val contractService: ContractService,
    private val contractRepository: ContractRepository,
    private val archiveService: ArchiveService,
    @Value("\${indexer.startBlock.contracts}") private val startBlock: Long,
    @Value("\${indexer.syncLoggerInterval.contracts}") private val syncLoggerInterval: Long,
    thorClient: ThorClient,
) :
    VeWorldIndexer(
        repository = contractRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLoggerInterval = syncLoggerInterval
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

    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber, IndexedContract::class.java)
    }
}
