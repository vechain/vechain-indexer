package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.IndexedAuthorityNode
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("authority-nodes")
@Component
open class AuthorityNodeIndexer(
    private val authorityNodeRepository: AuthorityNodeRepository,
    private val mongoTemplate: MongoTemplate,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.authorityNodes}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.authorityNodes}") private val syncLogInterval: Long,
    @Value("\${indexer.syncBlockBatchSize.authorityNodes}") private val syncBlockBatchSize: Long,
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

    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        val candidateEvents =
            processAllEvents(
                events,
                transfers,
                FilterCriteria(
                    contractAddresses = listOf("0x0000000000000000000000417574686f72697479"),
                    eventNames = listOf("Candidate"),
                ),
            )
        for (event in candidateEvents) {
            val nodeMaster = event.params.getAsString("nodeMaster") ?: continue
            val action = event.params.getAsString("action") ?: continue

            println("NodeMaster: $nodeMaster, Action: $action")

            when (action) {
                ACTION_ADDED -> {
                    println("Added nodeMaster: $nodeMaster")
                    val node =
                        IndexedAuthorityNode(
                            nodeMaster = nodeMaster,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                        )
                    authorityNodeRepository.save(node)
                }
                ACTION_REVOKED -> {
                    println("→ DELETING from DB $nodeMaster")
                    authorityNodeRepository.deleteById(nodeMaster)
                }
            }
        }
    }

    override fun rollback(blockNumber: Long) {
        authorityNodeRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
