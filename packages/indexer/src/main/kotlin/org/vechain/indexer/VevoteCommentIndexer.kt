package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.service.CommentService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog
import org.vechain.indexer.utils.EventUtils

@Profile("vevote-events")
@Component
open class VevoteCommentIndexer(
    private val vevoteCommentRepository: VevoteCommentRepository,
    private val commentService: CommentService,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.vevote}") startBlock: Long,
    @Value("\${indexer.events.vevote}") private val eventName: String,
    @Value("\${indexer.syncLogInterval.vevote}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.vevote}") private val syncBlockBatchSize: Long,
    @Value("\${veworld.contract.vevote.address}") private val contractAddress: String,
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
        val processedEvents =
            processAllEvents(
                events,
                transfers,
                FilterCriteria(
                    contractAddresses = listOf(contractAddress),
                    eventNames = listOf(eventName)
                )
            )

        // Process events to extract Reason
        val potentialReason =
            processedEvents.mapNotNull { event -> EventUtils.extractVevoteCommentEvent(event) }

        val allowedReason =
            potentialReason
                .filter { vote -> vote.reason.isNotEmpty() }
                .filter { vote -> commentService.allowComment(vote.proposalId, vote.reason) }

        // only save the filtered valid reason
        if (allowedReason.isNotEmpty()) {
            allowedReason.forEach { comment ->
                logger.info(
                    "ProposalID: ${comment.proposalId}, " +
                        "Voter: ${comment.voter}, " +
                        "Choices: ${comment.choices}"
                )
            }

            vevoteCommentRepository.saveAll(allowedReason)
        }
    }

    override fun rollback(blockNumber: Long) {
        vevoteCommentRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
