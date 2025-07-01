package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.AuthorityNodeEndorser
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.service.AuthorityNodeEndorserService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("authority-nodes")
@Component
open class AuthorityNodeEndorserIndexer(
    private val authorityNodeRepository: AuthorityNodeRepository,
    private val authorityNodeEndorserService: AuthorityNodeEndorserService,
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
    companion object {
        const val ACTION_ADDED =
            "0x6164646564000000000000000000000000000000000000000000000000000000"
        const val ACTION_REVOKED =
            "0x7265766f6b656400000000000000000000000000000000000000000000000000"
    }

    private var endorsersChecked = false

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

        for (event in candidateEvents) {
            val nodeMaster = event.params.getAsString("nodeMaster") ?: continue
            val action = event.params.getAsString("action") ?: continue

            when (action) {
                ACTION_ADDED -> {
                    val node =
                        AuthorityNodeEndorser(
                            nodeMaster = nodeMaster,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                        )
                    authorityNodeRepository.save(node)
                }
                ACTION_REVOKED -> {
                    authorityNodeRepository.deleteById(nodeMaster)
                }
            }
        }

        // Check endorsers when fully synced
        if (this.status == Status.FULLY_SYNCED && !endorsersChecked) {
            logger.info("Indexer fully synced - checking all node endorsers...")
            authorityNodeEndorserService.syncEndorsersForAllNodes()
            endorsersChecked = true
            logger.info("Checked endorsers for all Authority Nodes")
        }
    }

    override fun rollback(blockNumber: Long) {
        authorityNodeRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
        endorsersChecked = false
    }
}
