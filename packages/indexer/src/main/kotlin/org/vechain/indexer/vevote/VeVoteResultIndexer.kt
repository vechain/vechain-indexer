package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseLogIndexer
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.VeVoteProposalResultRepository
import org.vechain.indexer.service.VeVoteResultService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("vevote-results")
@Component
open class VeVoteResultIndexer(
    thorClient: ThorClient,
    abiManager: AbiManager,
    private val service: VeVoteResultService,
    private val veVoteProposalResultRepository: VeVoteProposalResultRepository,
    @Value("\${indexer.startBlock.vevote}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.vevote}") private val syncLogInterval: Long,
    @Value("\${veworld.contract.vevote.address}") private val contractAddress: String,
    @Value("\${indexer.syncBlockBatchSize.vevote}") private val syncBlockBatchSize: Long,
) :
    BaseLogIndexer(
        repository = veVoteProposalResultRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT),
        abiManager = abiManager,
    ) {
    override fun processLogs(
        events: List<EventLog>,
        transfers: List<TransferLog>,
    ) {
        // Process events using the inherited method
        val processedEvents =
            processAllEvents(
                events,
                transfers,
                FilterCriteria(
                    contractAddresses = listOf(contractAddress),
                    eventNames = listOf("VoteCast"),
                ),
            )

        // Process votes in the service
        val results = service.processVeVoteResults(processedEvents)

        // Save the results
        if (results.isNotEmpty()) {
            veVoteProposalResultRepository.saveAll(results)
        }
    }

    override fun rollback(blockNumber: Long) {
        veVoteProposalResultRepository.deleteAllByBlockNumberBetween(
            blockNumber - 1,
            blockNumber + 1,
        )
    }
}
