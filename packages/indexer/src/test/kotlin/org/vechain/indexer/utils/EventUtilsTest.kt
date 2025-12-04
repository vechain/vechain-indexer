package org.vechain.indexer.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.transfer.TransferEventType

class EventUtilsTest {
    @Nested
    inner class DetermineEventTypeTest {
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
                val params = AbiEventParameters(emptyMap(), eventType)
                val result = EventUtils.determineEventType(params)
                assertEquals(expected, result, "Failed for eventType: $eventType")
            }
        }

        @Test
        fun `determineEventType should detect FT and NFT in Transfer`() {
            val ftParams = AbiEventParameters(mapOf("value" to "100"), "Transfer")
            val nftParams = AbiEventParameters(mapOf("tokenId" to "1"), "Transfer")
            val unknownParams = AbiEventParameters(emptyMap(), "Transfer")

            assertEquals(HistoryEventName.TRANSFER_FT, EventUtils.determineEventType(ftParams))
            assertEquals(HistoryEventName.TRANSFER_NFT, EventUtils.determineEventType(nftParams))
            assertNull(EventUtils.determineEventType(unknownParams))
        }

        @Test
        fun `determineEventType should return null for unknown types`() {
            val params = AbiEventParameters(emptyMap(), "SomeRandomEvent")
            assertNull(EventUtils.determineEventType(params))
        }

        @Test
        fun `determineTransferType should detect transfer types correctly`() {
            val ftParams = AbiEventParameters(mapOf("value" to "100"), "Transfer")
            val nftParams = AbiEventParameters(mapOf("tokenId" to "1"), "Transfer")
            val sfParams = AbiEventParameters(emptyMap(), "TransferSingle")
            val vetParams = AbiEventParameters(emptyMap(), "VET_TRANSFER")
            val unknownParams = AbiEventParameters(emptyMap(), "OtherEvent")

            assertEquals(
                TransferEventType.FUNGIBLE_TOKEN,
                EventUtils.determineTransferType(ftParams),
            )
            assertEquals(TransferEventType.NFT, EventUtils.determineTransferType(nftParams))
            assertEquals(
                TransferEventType.SEMI_FUNGIBLE_TOKEN,
                EventUtils.determineTransferType(sfParams),
            )
            assertEquals(TransferEventType.VET, EventUtils.determineTransferType(vetParams))
            assertNull(EventUtils.determineTransferType(unknownParams))
        }
    }

    @Nested
    inner class PartitionBlacklistEventsTests {
        @Test
        fun `partitionBlacklistEvents separates blacklist and whitelist events`() {
            val blacklistEvent =
                buildIndexedEvent(
                    id = "event-1",
                    eventType = "NFT_Blacklisted",
                    blockNumber = 1L,
                    params =
                        AbiEventParameters(mapOf("contractAddress" to "0x123"), "NFT_Blacklisted"),
                )
            val whitelistEvent =
                buildIndexedEvent(
                    id = "event-2",
                    eventType = "NFT_Whitelisted",
                    blockNumber = 2L,
                    params =
                        AbiEventParameters(mapOf("contractAddress" to "0x456"), "NFT_Whitelisted"),
                )

            val (blacklist, whitelist) =
                EventUtils.partitionBlacklistEvents(listOf(blacklistEvent, whitelistEvent))

            assertEquals(listOf("0x123"), blacklist)
            assertEquals(listOf("0x456"), whitelist)
        }

        @Test
        fun `partitionBlacklistEvents deduplicates by contract address keeping latest event`() {
            val event1 =
                buildIndexedEvent(
                    id = "event-1",
                    eventType = "NFT_Blacklisted",
                    blockNumber = 1L,
                    params =
                        AbiEventParameters(mapOf("contractAddress" to "0x123"), "NFT_Blacklisted"),
                )
            val event2 =
                buildIndexedEvent(
                    id = "event-2",
                    eventType = "NFT_Whitelisted",
                    blockNumber = 2L,
                    params =
                        AbiEventParameters(mapOf("contractAddress" to "0x123"), "NFT_Whitelisted"),
                )

            val (blacklist, whitelist) = EventUtils.partitionBlacklistEvents(listOf(event1, event2))

            assertEquals(emptyList<String>(), blacklist)
            assertEquals(listOf("0x123"), whitelist)
        }

        @Test
        fun `partitionBlacklistEvents handles multiple addresses`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        id = "event-1",
                        eventType = "NFT_Blacklisted",
                        blockNumber = 1L,
                        params =
                            AbiEventParameters(
                                mapOf("contractAddress" to "0x111"),
                                "NFT_Blacklisted",
                            ),
                    ),
                    buildIndexedEvent(
                        id = "event-2",
                        eventType = "NFT_Blacklisted",
                        blockNumber = 2L,
                        params =
                            AbiEventParameters(
                                mapOf("contractAddress" to "0x222"),
                                "NFT_Blacklisted",
                            ),
                    ),
                    buildIndexedEvent(
                        id = "event-3",
                        eventType = "NFT_Whitelisted",
                        blockNumber = 3L,
                        params =
                            AbiEventParameters(
                                mapOf("contractAddress" to "0x333"),
                                "NFT_Whitelisted",
                            ),
                    ),
                    buildIndexedEvent(
                        id = "event-4",
                        eventType = "NFT_Whitelisted",
                        blockNumber = 4L,
                        params =
                            AbiEventParameters(
                                mapOf("contractAddress" to "0x444"),
                                "NFT_Whitelisted",
                            ),
                    ),
                )

            val (blacklist, whitelist) = EventUtils.partitionBlacklistEvents(events)

            assertEquals(setOf("0x111", "0x222"), blacklist.toSet())
            assertEquals(setOf("0x333", "0x444"), whitelist.toSet())
        }

        @Test
        fun `partitionBlacklistEvents returns empty lists for empty input`() {
            val (blacklist, whitelist) = EventUtils.partitionBlacklistEvents(emptyList())

            assertEquals(emptyList<String>(), blacklist)
            assertEquals(emptyList<String>(), whitelist)
        }

        @Test
        fun `partitionBlacklistEvents keeps only latest event per contract address`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        id = "event-1",
                        eventType = "NFT_Blacklisted",
                        blockNumber = 1L,
                        params =
                            AbiEventParameters(
                                mapOf("contractAddress" to "0x123"),
                                "NFT_Blacklisted",
                            ),
                    ),
                    buildIndexedEvent(
                        id = "event-2",
                        eventType = "NFT_Blacklisted",
                        blockNumber = 3L,
                        params =
                            AbiEventParameters(
                                mapOf("contractAddress" to "0x123"),
                                "NFT_Blacklisted",
                            ),
                    ),
                    buildIndexedEvent(
                        id = "event-3",
                        eventType = "NFT_Blacklisted",
                        blockNumber = 2L,
                        params =
                            AbiEventParameters(
                                mapOf("contractAddress" to "0x123"),
                                "NFT_Blacklisted",
                            ),
                    ),
                )

            val (blacklist, whitelist) = EventUtils.partitionBlacklistEvents(events)

            assertEquals(listOf("0x123"), blacklist)
            assertEquals(emptyList<String>(), whitelist)
        }

        @Test
        fun `partitionBlacklistEvents throws error when contractAddress is missing`() {
            val event =
                buildIndexedEvent(
                    id = "event-1",
                    eventType = "NFT_Blacklisted",
                    blockNumber = 1L,
                    params = AbiEventParameters(emptyMap(), "NFT_Blacklisted"),
                )

            val exception =
                assertThrows(IllegalStateException::class.java) {
                    EventUtils.partitionBlacklistEvents(listOf(event))
                }
            assertTrue(exception.message?.contains("No contract address in event") == true)
        }
    }

    @Nested
    inner class GroupByBlockTests {
        @Test
        fun `groupByBlock groups events by blockId`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockId = "block1",
                        blockNumber = 1L,
                        blockTimestamp = 10L,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockId = "block2",
                        blockNumber = 2,
                        blockTimestamp = 20L,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockId = "block2",
                        blockNumber = 2,
                        blockTimestamp = 20L,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                )

            val grouped = EventUtils.groupByBlock(events)

            assertEquals(2, grouped.size)
            assertEquals(1, grouped[BlockDetails("block1", 1L, 10L)]!!.size)
            assertEquals(2, grouped[BlockDetails("block2", 2L, 20L)]!!.size)
        }

        @Test
        fun `groupByBlock returns empty map for empty input list`() {
            val grouped = EventUtils.groupByBlock(emptyList())
            assertTrue(grouped.isEmpty())
        }

        @Test
        fun `groupByBlock returns map sorted by blockNumber`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockId = "block-a",
                        blockNumber = 3,
                        blockTimestamp = 30L,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockId = "block-b",
                        blockNumber = 1,
                        blockTimestamp = 10L,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockId = "block-c",
                        blockNumber = 2,
                        blockTimestamp = 20L,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                )

            val grouped = EventUtils.groupByBlock(events)

            assertEquals(listOf(1L, 2L, 3L), grouped.keys.toList().map { it.blockNumber })
        }
    }
}
