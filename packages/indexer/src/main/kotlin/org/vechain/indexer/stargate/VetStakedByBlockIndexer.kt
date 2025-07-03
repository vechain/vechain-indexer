package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseLogIndexer
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.stargate.VetStakedByBlockRepository
import org.vechain.indexer.service.stargate.VetStakedByBlockService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("stargate")
@Component
open class VetStakedByBlockIndexer(
    private val service: VetStakedByBlockService,
    repository: VetStakedByBlockRepository,
    thorClient: ThorClient,
    abiManager: AbiManager,
    businessEventManager: BusinessEventManager,
    @Value("\${indexer.startBlock.stargate}") startBlock: Long,
    @Value("\${indexer.syncBlockBatchSize.stargate}") private val syncBlockBatchSize: Long,
    @Value("\${indexer.syncLogInterval.stargate}") private val logInterval: Long,
    @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
    private val stargateNftContractAddress: String,
) :
    BaseLogIndexer(
        repository = repository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = logInterval,
        blockBatchSize = syncBlockBatchSize,
        logsType = setOf(LogType.EVENT, LogType.TRANSFER),
        abiManager = abiManager,
        businessEventManager = businessEventManager,
    ) {
    private val businessEventNames = listOf("STARGATE_STAKE", "STARGATE_UNSTAKE")

    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        if (events.isEmpty()) {
            return
        }

        val genericEvents =
            processBlockGenericEvents(
                events,
                transfers,
                FilterCriteria(
                    businessEventNames = businessEventNames,
                    contractAddresses = listOf(stargateNftContractAddress),
                ),
            )

        val businessEvents =
            processBlockBusinessEvents(
                genericEvents,
                FilterCriteria(businessEventNames = businessEventNames),
            )

        if (businessEvents.isEmpty()) {
            return
        }

        val newRecords = service.processEvents(businessEvents)

        if (newRecords != null) {
            service.saveRecord(newRecords)
        }
    }

    override fun rollback(blockNumber: Long) = service.rollback(blockNumber)
}
