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
    contractRepository: ContractRepository,
    contractArchiveService: ArchiveService<IndexedContract, ContractArchive>,
    @Value("\${indexer.startBlock.contracts}") private val startBlock: Long,
    @Value("\${indexer.syncLogInterval.contracts}") private val syncLogInterval: Long,
    @Value("\${indexer.pruner.enabled}") private val prunerEnabled: Boolean,
    @Value("\${indexer.pruner.interval}") private val prunerInterval: Long,
    thorClient: ThorClient,
) :
    StatefulIndexer<IndexedContract, ContractArchive, Triple<TxEvent, Transaction, Clause>>(
        repository = contractRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        prunerEnabled = prunerEnabled,
        prunerInterval = prunerInterval,
        archiveService = contractArchiveService
    ) {

    override fun extractData(block: Block): List<Triple<TxEvent, Transaction, Clause>> {
        return extractMasterChangeEvents(block)
    }

    override fun findExisting(
        data: List<Triple<TxEvent, Transaction, Clause>>
    ): List<IndexedContract> {
        return contractService.getExisting(data.map { (event) -> event.address })
    }

    override fun parseRecords(
        block: Block,
        data: List<Triple<TxEvent, Transaction, Clause>>,
        existing: List<IndexedContract>
    ): List<IndexedContract> {
        return contractService.parseContracts(block, data, existing)
    }
}
