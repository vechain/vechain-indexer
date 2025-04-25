package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.service.commentService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("vevote-events")
@Component
open class VevoteCommentIndexer(
    private val vevoteCommentRepository: VevoteCommentRepository,
    private val commentService: commentService,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.vevote}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.vevote}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.vevote}") private val syncBlockBatchSize: Long,
) :
    BaseLogIndexer(
        repository = vevoteCommentRepository,
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

        // Process events using the inherited method
        val processedEvents = processAllEvents(events, transfers, filterCriteria)

        // Use service to get filtered comments
        val allowedReason = commentService.processComments(processedEvents)

        // Save the results
        if (allowedReason.isNotEmpty()) {
            vevoteCommentRepository.saveAll(allowedReason)
        }
    }

    override fun rollback(blockNumber: Long) {
        vevoteCommentRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
