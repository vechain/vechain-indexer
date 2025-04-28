package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.repository.VoteAggregateRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("vevote-events")
@Component
open class VoteAggregateIndexer(
    thorClient: ThorClient,
    abiManager: AbiManager,
    private val vevoteCommentRepository: VoteAggregateRepository,
    @Value("\${indexer.startBlock.vevote}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.vevote}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.vevote}") private val syncBlockBatchSize: Long,
    @Value("\${veworld.contract.vevote.address}") private val contractAddress: String
) :
    BaseLogIndexer(
        repository = VoteAggregateRepository,
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
        // Get filter criteria from service
        val filterCriteria = commentService.getFilterCriteria()
        val processedEvents = processAllEvents(events, transfers, filterCriteria)
        val allowedReason = commentService.processComment(processedEvents)
        // Save the results
        if (allowedReason.isNotEmpty()) {
            VoteAggregateRepository.saveAll(allowedReason)
        }
    }

    override fun rollback(blockNumber: Long) {
        vevoteCommentRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
