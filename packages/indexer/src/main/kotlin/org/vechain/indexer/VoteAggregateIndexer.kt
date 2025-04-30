package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.repository.VoteAggregateRepository
import org.vechain.indexer.service.VoteAggregateService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("vevote-result")
@Component
open class VoteAggregateIndexer(
    thorClient: ThorClient,
    abiManager: AbiManager,
    private val service: VoteAggregateService,
    private val voteAggregateRepository: VoteAggregateRepository,
    @Value("\${indexer.startBlock.vevote}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.vevote}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.vevote}") private val syncBlockBatchSize: Long,
) :
    BaseLogIndexer(
        repository = voteAggregateRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT),
        abiManager = abiManager,
    ) {

    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        // Get filter criteria from service
        val filterCriteria = service.getFileCriteria()

        // Process events using the inherited method
        val processedEvents = processAllEvents(events, transfers, filterCriteria)

        // Process votes in the service
        val aggregates = service.processVoteAggregates(processedEvents)

        // Save the results
        if (aggregates.isNotEmpty()) {
            voteAggregateRepository.saveAll(aggregates)
        }
    }

    override fun rollback(blockNumber: Long) {
        voteAggregateRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
