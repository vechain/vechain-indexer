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
import org.vechain.indexer.thor.model.Block

@Profile("history-events")
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
                                "claim-reward-v2",
                                "gm-upgrade",
                                "proposal-deposit",
                                "token-ft-swap",
                                "token-vet-swap",
                                "vet-token-swap",
                                "vot3-to-b3tr-swap",
                                "token-vet-swap-2",
                                "maas-sale",
                                "wov-offer-accepted-sale",
                                "wov-non-custodial-sale",
                                "wov-action-executed-sale",
                                "wov-custodial-wov-sale",
                                "wov-custodial-vet-sale",
                                "stargate-stake-delegate",
                                "stargate-stake",
                                "stargate-unstake",
                                "stargate-rewards-claim",
                                "stargate-rewards-claim-base",
                                "stargate-rewards-claim-delegate",
                                "stargate-undelegate",
                            ),
                    ),
                )
            historyService.processBlockEvents(events, block)
        }
    }

    override fun rollback(blockNumber: Long) {
        historyService.rollback(blockNumber)
    }
}
