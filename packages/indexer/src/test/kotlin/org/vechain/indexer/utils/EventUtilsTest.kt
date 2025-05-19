package org.vechain.indexer.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.model.history.HistoryEventName

class EventUtilsTest {
    @Test
    fun `determineEventType should detect known types`() {
        val cases =
            mapOf(
                "B3TR_Vot3ToB3trSwap" to HistoryEventName.B3TR_SWAP_VOT3_TO_B3TR,
                "B3TR_B3trToVot3Swap" to HistoryEventName.B3TR_SWAP_B3TR_TO_VOT3,
                "B3TR_ProposalDeposit" to HistoryEventName.B3TR_PROPOSAL_SUPPORT,
                "B3TR_ClaimReward" to HistoryEventName.B3TR_CLAIM_REWARD,
                "B3TR_GMUpgrade" to HistoryEventName.B3TR_UPGRADE_GM,
                "B3TR_ActionReward" to HistoryEventName.B3TR_ACTION,
                "B3TR_ProposalVote" to HistoryEventName.B3TR_PROPOSAL_VOTE,
                "B3TR_XAllocationVote" to HistoryEventName.B3TR_XALLOCATION_VOTE,
                "TransferSingle" to HistoryEventName.TRANSFER_SF,
                "TransferBatch" to HistoryEventName.TRANSFER_SF,
                "VET_TRANSFER" to HistoryEventName.TRANSFER_VET,
                "FT_VET_Swap" to HistoryEventName.SWAP_FT_TO_VET,
                "VET_FT_Swap" to HistoryEventName.SWAP_VET_TO_FT,
                "Token_FTSwap" to HistoryEventName.SWAP_FT_TO_FT,
            )

        cases.forEach { (eventType, expected) ->
            val params = GenericEventParameters(emptyMap(), eventType)
            val result = EventUtils.determineEventType(params)
            assertEquals(expected, result, "Failed for eventType: $eventType")
        }
    }

    @Test
    fun `determineEventType should detect FT and NFT in Transfer`() {
        val ftParams = GenericEventParameters(mapOf("value" to "100"), "Transfer")
        val nftParams = GenericEventParameters(mapOf("tokenId" to "1"), "Transfer")
        val unknownParams = GenericEventParameters(emptyMap(), "Transfer")

        assertEquals(HistoryEventName.TRANSFER_FT, EventUtils.determineEventType(ftParams))
        assertEquals(HistoryEventName.TRANSFER_NFT, EventUtils.determineEventType(nftParams))
        assertNull(EventUtils.determineEventType(unknownParams))
    }

    @Test
    fun `determineEventType should return null for unknown types`() {
        val params = GenericEventParameters(emptyMap(), "SomeRandomEvent")
        assertNull(EventUtils.determineEventType(params))
    }

    @Test
    fun `determineTransferType should detect transfer types correctly`() {
        val ftParams = GenericEventParameters(mapOf("value" to "100"), "Transfer")
        val nftParams = GenericEventParameters(mapOf("tokenId" to "1"), "Transfer")
        val sfParams = GenericEventParameters(emptyMap(), "TransferSingle")
        val vetParams = GenericEventParameters(emptyMap(), "VET_TRANSFER")
        val unknownParams = GenericEventParameters(emptyMap(), "OtherEvent")

        assertEquals(TransferEventType.FUNGIBLE_TOKEN, EventUtils.determineTransferType(ftParams))
        assertEquals(TransferEventType.NFT, EventUtils.determineTransferType(nftParams))
        assertEquals(
            TransferEventType.SEMI_FUNGIBLE_TOKEN,
            EventUtils.determineTransferType(sfParams),
        )
        assertEquals(TransferEventType.VET, EventUtils.determineTransferType(vetParams))
        assertNull(EventUtils.determineTransferType(unknownParams))
    }
}
