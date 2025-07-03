package org.vechain.indexer.stargate

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseLogIndexer
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.stargate.NftHoldersByBlockRepository
import org.vechain.indexer.service.stargate.NftHolderByBlockService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("stargate")
@Component
open class NftHoldersByBlockIndexer(
    private val service: NftHolderByBlockService,
    repository: NftHoldersByBlockRepository,
    thorClient: ThorClient,
    abiManager: AbiManager,
    businessEventManager: BusinessEventManager,
    @Value("\${indexer.startBlock.stargate}") startBlock: Long,
    @Value("\${indexer.syncBlockBatchSize.stargate}") private val syncBlockBatchSize: Long,
    @Value("\${indexer.syncLogInterval.stargate}") private val logInterval: Long,
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
    private val businessEventNames =
        listOf("STARGATE_STAKE", "STARGATE_STAKE_DELEGATE", "STARGATE_UNSTAKE")

    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
        if (events.isEmpty()) {
            return
        }

        // Extract Business Events from the logs
        val businessEvents =
            processBlockBusinessEvents(
                processBlockGenericEvents(
                    events,
                    transfers,
                    FilterCriteria(businessEventNames = businessEventNames),
                ),
                FilterCriteria(businessEventNames = businessEventNames),
            )

        if (businessEvents.isEmpty()) {
            return
        }

        val newRecord = service.processEvents(businessEvents)

        if (newRecord != null) {
            service.saveRecord(newRecord)
        }
    }

    override fun rollback(blockNumber: Long) = service.rollback(blockNumber)
}
