package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.ContractArchive
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.service.PrunerService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.*

@Profile("contracts")
@Component
open class ContractIndexer(
    private val contractService: ContractService,
    contractArchiveService: ArchiveService<IndexedContract, ContractArchive>,
    contractRepository: ContractRepository,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.contracts}") startBlock: Long,
    @Value("\${indexer.pruner.removalChunkSize}") private val prunerRemovalChunkSize: Int,
    @Value("\${indexer.syncLogInterval.contracts}") private val syncLogInterval: Long,
) :
    StatefulLogsIndexer<IndexedContract, ContractArchive>(
        repository = contractRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        archiveService = contractArchiveService,
        blockBatchSize = 1,
        logsType = setOf(LogType.EVENT),
        abiManager = abiManager,
        businessEventManager = null,
        prunerService = PrunerService(contractArchiveService, prunerRemovalChunkSize),
    ) {
    override fun processLogs(
        events: List<EventLog>,
        transfers: List<TransferLog>,
    ) {
        val masterChangeEvents =
            processBlockGenericEvents(
                events,
                transfers,
                FilterCriteria(
                    abiNames = listOf("prototype-event"),
                    eventNames = listOf("\$Master"),
                ),
            )

        if (masterChangeEvents.isEmpty()) return

        // Find any existing records
        val existing = contractService.getExisting(masterChangeEvents.mapNotNull { it.address })

        // Process the updated records
        val updated = contractService.parseContracts(masterChangeEvents, existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            contractService.update(updated, existing)
        }
    }
}
