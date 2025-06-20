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
                                "B3TR_ActionReward",
                                "B3TR_ProposalVote",
                                "B3TR_B3trToVot3Swap",
                                "B3TR_XAllocationVote",
                                "B3TR_ClaimReward",
                                "B3TR_ClaimReward2",
                                "B3TR_GMUpgrade",
                                "MAAS_SALE",
                                "B3TR_ProposalDeposit",
                                "Token_FTSwap",
                                "FT_VET_Swap",
                                "FT_VET_Swap2",
                                "VET_FT_Swap",
                                "B3TR_Vot3ToB3trSwap",
                                "WOV_Action_Executed_Sale",
                                "WOV_Custodial_VET_Sale",
                                "WOV_Custodial_WOV_Sale",
                                "WOV_Non_Custodial_Sale",
                                "WOV_Offer_Accepted_Sale",
                                "STARGATE_DELEGATE",
                                "STARGATE_STAKE",
                                "STARGATE_UNSTAKE",
                                "STARGATE_CLAIM_REWARDS",
                                "STARGATE_CLAIM_REWARDS_BASE",
                                "STARGATE_CLAIM_REWARDS_DELEGATE",
                                "STARGATE_UNDELEGATE",
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
