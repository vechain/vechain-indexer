package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog
import org.vechain.indexer.utils.EventUtils

@Profile("vevote-events")
@Component
open class VevoteCommentIndexer(
    private val vevoteCommentRepository: VevoteCommentRepository,
    private val mongoTemplate: MongoTemplate,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.vevote}") startBlock: Long,
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
                    eventNames = listOf("VoteCast")
                )
            )

        // Process events to extract comments
        val potentialComments =
            processedEvents.mapNotNull { event -> EventUtils.extractVevoteCommentEvent(event) }

        val votesWithReason = potentialComments.filter { comment -> comment.reason.isNotEmpty() }

        if (votesWithReason.isNotEmpty()) {
            votesWithReason.forEach { comment ->
                logger.info(
                    "ProposalID: ${comment.proposalId}, " +
                        "Voter: ${comment.voter}, " +
                        "Choices: ${comment.choices}"
                )
            }

            //            mongoTemplate.insert(votesWithReason, vevoteCommentRepository::class.java)
            vevoteCommentRepository.saveAll(votesWithReason)
        }
    }

    override fun rollback(blockNumber: Long) {
        vevoteCommentRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
