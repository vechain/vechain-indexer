package org.vechain.indexer

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.model.generateId
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

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
) :
    BaseLogIndexer(
        repository = vevoteCommentRepository,
        startBlock = 21432863,
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
                    contractAddresses = listOf("0xfcc8f0d6ef2eef8d6fcf376ecf42d7851171a5cc"),
                    eventNames = listOf("VoteCast")
                ),
            )
        val votesWithReason =
            processedEvents.filter { event ->
                val reason = event.params.getReturnValues()["reason"] as? String
                reason != null && reason.isNotEmpty()
            }

        if (votesWithReason.isNotEmpty()) {
            val vevoteComment =
                votesWithReason
                    .distinctBy { vote ->
                        val proposalId =
                            vote.params.getReturnValues()["proposalId"]?.toString() ?: ""
                        val reason = vote.params.getReturnValues()["reason"] as? String ?: ""
                        generateId(proposalId, reason)
                    }
                    .map { vote ->
                        val proposalId =
                            vote.params.getReturnValues()["proposalId"]?.toString() ?: ""
                        val reason = vote.params.getReturnValues()["reason"] as String

                        // Using model's constructor:
                        VevoteProposalComment(
                            id = generateId(proposalId, reason),
                            blockId = vote.blockId,
                            blockNumber = vote.blockNumber,
                            blockTimestamp = vote.blockTimestamp,
                            voter = vote.params.getReturnValues()["voter"] as String,
                            proposalId = proposalId,
                            choice =
                                (vote.params.getReturnValues()["choices"] as? Number)?.toInt() ?: 0,
                            weight =
                                vote.params.getReturnValues()["weight"] as? BigInteger
                                    ?: BigInteger.ZERO,
                            reason = reason
                        )
                    }

            vevoteCommentRepository.saveAll(vevoteComment)
        }
    }

    override fun rollback(blockNumber: Long) {
        vevoteCommentRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
