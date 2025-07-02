package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.service.AuthorityNodeEventService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("authority-nodes")
@Component
open class AuthorityNodeEndorserIndexer(
    private val authorityNodeRepository: AuthorityNodeRepository,
    private val authorityNodeEventService: AuthorityNodeEventService,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.authorityNodes}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.authorityNodes}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.authorityNodes}") private val syncBlockBatchSize: Long,
    @Value("\${veworld.contract.authority_node.address}") private val contractAddress: String,
) :
    BaseLogIndexer(
        repository = authorityNodeRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT),
        abiManager = abiManager,
    ) {

    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        val candidateEvents =
            processAllEvents(
                events,
                transfers,
                FilterCriteria(
                    contractAddresses = listOf(contractAddress),
                    eventNames = listOf("Candidate"),
                ),
            )

        // Process the candidate events to service
        authorityNodeEventService.processCandidateEvents(
            candidateEvents,
            this.status == Status.FULLY_SYNCED,
        )

        // Check endorsers when fully synced
        if (this.status == Status.FULLY_SYNCED) {
            logger.info("Indexer fully synced - checking all node endorsers...")
            authorityNodeEventService.syncEndorsersForAllNodes()
            logger.info("Checked endorsers for all Authority Nodes")
        }
    }

    override fun rollback(blockNumber: Long) {
        authorityNodeRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
