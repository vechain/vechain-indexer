package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.VeVoteProposalResults
import org.vechain.indexer.repository.VeVoteProposalResultRepository
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
    private val mongoTemplate: MongoTemplate,
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

    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        fun getFileCriteria(): FilterCriteria {
            return FilterCriteria(
                contractAddresses = listOf(contractAddress),
                eventNames = listOf("VoteCast"),
            )
        }

        // Process events using the inherited method
        val processedEvents = processAllEvents(events, transfers, getFileCriteria())

        // Process votes in the service
        val aggregates = service.processVoteAggregates(processedEvents)

        // Save the results
        if (aggregates.isNotEmpty()) {
            mongoTemplate.insert(aggregates, VeVoteProposalResults::class.java)
        }
    }

    override fun rollback(blockNumber: Long) {
        veVoteProposalResultRepository.deleteAllByBlockNumberBetween(
            blockNumber - 1,
            blockNumber + 1
        )
    }
}
