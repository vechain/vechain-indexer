package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.model.IndexedHistoryEvent
import org.vechain.indexer.repository.HistoryEventRepository
import org.vechain.indexer.service.HistoryService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

@Profile("history_events")
@Component
open class HistoryIndexer(
    private val historyRepository: HistoryEventRepository,
    private val historyService: HistoryService,
    private val mongoTemplate: MongoTemplate,
    thorClient: ThorClient,
    abiManager: AbiManager,
    businessEventManager: BusinessEventManager,
    @Value("\${indexer.startBlock.history}") private val startBlock: Long,
    @Value("\${indexer.syncLogInterval.history}") private val syncLogInterval: Long,
) :
    BaseIndexer(
        repository = historyRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        abiManager = abiManager,
        businessEventManager = businessEventManager,
    ) {
    override fun processBlock(block: Block) {
        if (block.transactions.isNotEmpty()) {
            val events =
                processAllEvents(
                    block,
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
            val historyEvents = historyService.processBlockEvents(events, block)
            mongoTemplate.insert(historyEvents, IndexedHistoryEvent::class.java)
        }
    }

    override fun rollback(blockNumber: Long) {
        historyRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
