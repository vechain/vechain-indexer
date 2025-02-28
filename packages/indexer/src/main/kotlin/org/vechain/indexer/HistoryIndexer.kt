package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.HistoryEventRepository
import org.vechain.indexer.service.HistoryService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@Profile("history_events")
@Component
open class HistoryIndexer(
    historyRepository: HistoryEventRepository,
    private val historyService: HistoryService,
    thorClient: ThorClient,
    abiManager: AbiManager,
    businessEventManager: BusinessEventManager,
    @Value("\${indexer.startBlock.history}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.history}") private val syncLogInterval: Long,
) :
    BaseLogIndexer(
        repository = historyRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        logsType = setOf(LogType.EVENT, LogType.TRANSFER),
        abiManager = abiManager,
        businessEventManager = businessEventManager,
    ) {
    override fun processLogs(
        events: List<EventLog>,
        transfers: List<TransferLog>,
    ) {
        val historyEvents =
            processAllEvents(
                events,
                transfers,
                FilterCriteria(
                    vetTransfers = true,
                    eventNames = listOf("Transfer", "TransferSingle", "TransferBatch"),
                    businessEventNames =
                        listOf(
                            "action-reward",
                            "b3tr-proposal-vote",
                            "b3tr-to-vot3-swap",
                            "b3tr-x-allocation-vote",
                            "claim-reward",
                            "gm-upgrade",
                            "proposal-deposit",
                            "token-ft-swap",
                            "token-vet-swap",
                            "vet-token-swap",
                            "vot3-to-b3tr-swap",
                        ),
                ),
            )

        historyService.processBlockEvents(historyEvents, events)
    }

    override fun rollback(blockNumber: Long) {
        historyService.rollback(blockNumber)
    }
}
