package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.service.AuthorityNodesService
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("authority-nodes")
@Component
open class AuthorityNodeIndexer(
    private val authorityNodeRepository: AuthorityNodeRepository,
    private val authorityNodeService: AuthorityNodesService,
    private val thorService: ThorService,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.syncLogInterval.authority_nodes}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.authority_nodes}") private val syncBlockBatchSize: Long,
    @Value("\${veworld.contract.authority_node.address}") private val contractAddress: String,
) :
    BaseLogIndexer(
        repository = authorityNodeRepository,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT),
        abiManager = abiManager,
    ) {
    private var hasSynced = false

    override fun getLastSyncedBlock(): BlockIdentifier? {
        if (!hasSynced && authorityNodeRepository.count() == 0L) {
            val bestBlock = thorService.getBestBlock()
            return BlockIdentifier(id = bestBlock.id, number = bestBlock.number)
        }
        return super.getLastSyncedBlock()
    }

    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        if (!hasSynced && authorityNodeRepository.count() == 0L) {
            logger.info("No Authority Nodes found – syncing after collection setup...")
            authorityNodeService.syncEndorsersForAllNodes()
            logger.info("Initial Authority Node sync complete.")

            hasSynced = true
        }

        val candidateEvents =
            processBlockGenericEvents(
                events,
                transfers,
                FilterCriteria(
                    contractAddresses = listOf(contractAddress),
                    eventNames = listOf("Candidate"),
                ),
            )

        authorityNodeService.processCandidateEvents(candidateEvents)
    }

    override fun rollback(blockNumber: Long) {
        authorityNodeRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
