package org.vechain.indexer.b3tr.gm

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

class GmNftEventUtilsTest {

    @Nested
    inner class ProcessAllTokenEventsTest {
        @Test
        fun `processAllTokenEvents creates GmNft from mint event`() {
            val mintEvent =
                buildIndexedEvent(
                    blockNumber = 100,
                    blockId = "block-mint",
                    blockTimestamp = 1_666_000,
                    eventType = "B3TR_GmMinted",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xMinted", "to" to "0xOwner")
                        ),
                )

            val result = GmNftEventUtils.processAllTokenEvents(null, listOf(mintEvent))

            assertEquals("0xMinted", result.id)
            assertEquals("0xOwner", result.owner)
            assertEquals(100L, result.blockNumber)
            assertEquals("block-mint", result.blockId)
            assertEquals(1_666_000L, result.blockTimestamp)
            assertEquals(GmLevelName.EARTH, result.level)
            assertEquals(java.math.BigInteger.ZERO, result.b3trDonated)
            assertNull(result.attachedNodeId)
        }

        @Test
        fun `processAllTokenEvents processes upgrade event after mint`() {
            val mintEvent =
                buildIndexedEvent(
                    blockNumber = 100,
                    blockId = "block-mint",
                    blockTimestamp = 1_666_000,
                    eventType = "B3TR_GmMinted",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xtoken", "to" to "0xOwner")
                        ),
                )

            val upgradeEvent =
                buildIndexedEvent(
                    blockNumber = 100,
                    blockId = "block-upgrade",
                    blockTimestamp = 1_666_100,
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "tokenId" to "0xtoken",
                                    "newLevel" to GmLevelName.MOON.ordinal.toString(),
                                    "value" to "150",
                                )
                        ),
                )

            val result =
                GmNftEventUtils.processAllTokenEvents(null, listOf(mintEvent, upgradeEvent))

            assertEquals(GmLevelName.MOON, result.level)
            assertEquals(java.math.BigInteger.valueOf(150), result.b3trDonated)
            assertEquals("block-upgrade", result.blockId)
            assertEquals(1_666_100L, result.blockTimestamp)
            assertEquals(1, result.version)
        }

        @Test
        fun `processAllTokenEvents throws if tokenId changes between events`() {
            val event1 =
                buildIndexedEvent(
                    blockNumber = 10,
                    eventType = "B3TR_GmMinted",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xA", "to" to "0xOwnerA")
                        ),
                )

            val event2 =
                buildIndexedEvent(
                    blockNumber = 10,
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(returnValues = mapOf("newLevel" to "1", "value" to "10")),
                )

            val exception =
                assertThrows<IllegalArgumentException> {
                    GmNftEventUtils.processAllTokenEvents(null, listOf(event1, event2))
                }

            assertTrue(exception.message!!.contains("All events must have the same tokenId"))
        }

        @Test
        fun `processAllTokenEvents throws if no mint and no existing NFT`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 50,
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("tokenId" to "0xtoken", "newLevel" to "1", "value" to "10")
                        ),
                )

            val exception =
                assertThrows<IllegalArgumentException> {
                    GmNftEventUtils.processAllTokenEvents(null, listOf(event))
                }

            assertTrue(exception.message!!.contains("No mint event found for tokenId"))
        }

        fun `processAllTokenEvents throws if events have different block numbers`() {
            val event1 =
                buildIndexedEvent(
                    blockNumber = 100,
                    eventType = "B3TR_GmMinted",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xSame", "to" to "0xOwner")
                        ),
                )

            val event2 =
                buildIndexedEvent(
                    blockNumber = 101,
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(returnValues = mapOf("newLevel" to "1", "value" to "10")),
                )

            val exception =
                assertThrows<IllegalArgumentException> {
                    GmNftEventUtils.processAllTokenEvents(null, listOf(event1, event2))
                }

            assertTrue(
                exception.message!!.contains(
                    "All events must have the same tokenId and blockNumber"
                )
            )
        }

        @Test
        fun `throws if multiple mint events are present`() {
            val event1 =
                buildIndexedEvent(
                    blockNumber = 10,
                    eventType = "B3TR_GmMinted",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xMinted", "to" to "0xOwner1")
                        ),
                )
            val event2 =
                buildIndexedEvent(
                    blockNumber = 10,
                    eventType = "B3TR_GmMinted",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xMinted", "to" to "0xOwner2")
                        ),
                )

            val exception =
                assertThrows<IllegalArgumentException> {
                    GmNftEventUtils.processAllTokenEvents(null, listOf(event1, event2))
                }

            assertTrue(exception.message!!.contains("Multiple mint events"))
        }

        @Test
        fun `throws if no mint event and no existing nft`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 10,
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("tokenId" to "0xMinted", "newLevel" to "1", "value" to "100")
                        ),
                )

            val exception =
                assertThrows<IllegalArgumentException> {
                    GmNftEventUtils.processAllTokenEvents(null, listOf(event))
                }

            assertTrue(exception.message!!.contains("No mint event"))
        }

        @Test
        fun `throws if mint event present and existing nft provided`() {
            val existing =
                GmNft(
                    tokenId = "0xMinted",
                    version = 1,
                    blockId = "blk",
                    blockNumber = 10,
                    blockTimestamp = 1000,
                    owner = "0xabc",
                    level = GmLevelName.EARTH,
                    b3trDonated = BigInteger.ZERO,
                    attachedNodeId = null,
                )
            val mintEvent =
                buildIndexedEvent(
                    blockNumber = 10,
                    eventType = "B3TR_GmMinted",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xMinted", "to" to "0xOwner")
                        ),
                )

            val exception =
                assertThrows<IllegalArgumentException> {
                    GmNftEventUtils.processAllTokenEvents(existing, listOf(mintEvent))
                }

            assertTrue(exception.message!!.contains("Mint event should not be present"))
        }
    }

    @Nested
    inner class ProcessMintedEventTest {

        @Test
        fun `processMintedEvent creates GmNft with correct values`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 123,
                    blockTimestamp = 1_666_000,
                    blockId = "block-mint",
                    eventType = "B3TR_GmMinted",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xMinted", "to" to "0xOwner")
                        ),
                )

            val nft = GmNftEventUtils.processMintedEvent(event)

            assertEquals("0xMinted", nft.id)
            assertEquals("0xOwner", nft.owner)
            assertEquals(GmLevelName.EARTH, nft.level)
            assertEquals("block-mint", nft.blockId)
            assertEquals(123L, nft.blockNumber)
            assertEquals(1_666_000L, nft.blockTimestamp)
            assertEquals(java.math.BigInteger.ZERO, nft.b3trDonated)
            assertNull(nft.attachedNodeId)
        }

        @Test
        fun `processMintedEvent throws error if event type is not B3TR_GmMinted`() {
            val event =
                buildIndexedEvent(
                    eventType = "InvalidType",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("tokenId" to "0xMinted", "owner" to "0xOwner")
                        ),
                )

            val exception =
                assertThrows<IllegalStateException> { GmNftEventUtils.processMintedEvent(event) }

            assertTrue(exception.message!!.contains("Invalid event type for mint"))
        }

        @Test
        fun `processMintedEvent throws error if tokenId is missing`() {
            val event =
                buildIndexedEvent(
                    eventType = "B3TR_GmMinted",
                    params = AbiEventParameters(returnValues = mapOf("owner" to "0xOwner")),
                )

            val exception =
                assertThrows<IllegalStateException> { GmNftEventUtils.processMintedEvent(event) }

            assertTrue(exception.message!!.contains("Missing 'tokenId' param in event"))
        }

        @Test
        fun `processMintedEvent throws error if owner is missing`() {
            val event =
                buildIndexedEvent(
                    eventType = "B3TR_GmMinted",
                    params = AbiEventParameters(returnValues = mapOf("tokenId" to "0xMinted")),
                )

            val exception =
                assertThrows<IllegalStateException> { GmNftEventUtils.processMintedEvent(event) }

            assertTrue(exception.message!!.contains("Missing 'to' param in event"))
        }
    }

    @Nested
    inner class ProcessUpgradedEventTest {
        private val baseNft =
            GmNft(
                version = 1,
                blockId = "original-block",
                blockNumber = 100,
                blockTimestamp = 1_000_000,
                id = "0x123",
                owner = "0xabc",
                level = GmLevelName.EARTH,
                b3trDonated = java.math.BigInteger.valueOf(100),
                attachedNodeId = null,
            )

        @Test
        fun `processUpgradedEvent updates level, donation, and block metadata`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 101,
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "newLevel" to GmLevelName.MOON.ordinal.toString(),
                                    "value" to "200",
                                )
                        ),
                )

            val updated = GmNftEventUtils.processUpgradedEvent(event, baseNft)

            assertEquals(GmLevelName.MOON, updated.level)
            assertEquals(java.math.BigInteger.valueOf(300), updated.b3trDonated)
            assertEquals(101L, updated.blockNumber)
            assertEquals(event.blockId, updated.blockId)
            assertEquals(event.blockTimestamp, updated.blockTimestamp)
        }

        @Test
        fun `processUpgradedEvent throws error if event type is incorrect`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 102,
                    eventType = "InvalidType",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "newLevel" to GmLevelName.MARS.ordinal.toString(),
                                    "value" to "150",
                                )
                        ),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processUpgradedEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Invalid event type for upgrade"))
        }

        @Test
        fun `processUpgradedEvent throws error if newLevel param is missing`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 103,
                    eventType = "B3TR_GmUpgrade",
                    params = AbiEventParameters(returnValues = mapOf("value" to "150")),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processUpgradedEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Missing 'newLevel' param in event"))
        }

        @Test
        fun `processUpgradedEvent throws error if value param is missing`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 104,
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("newLevel" to GmLevelName.MARS.ordinal.toString())
                        ),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processUpgradedEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Missing 'b3trDonated' param in event"))
        }
    }

    @Nested
    inner class ProcessNodeAttachedEventTest {
        private val baseNft =
            GmNft(
                version = 1,
                blockId = "block-id",
                blockNumber = 100,
                blockTimestamp = 1_000_000,
                id = "0x123",
                owner = "0xabc",
                level = GmLevelName.MARS,
                b3trDonated = java.math.BigInteger.ZERO,
                attachedNodeId = null,
            )

        @Test
        fun `processNodeAttachedEvent sets attached node id and updates level`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 101,
                    eventType = "B3TR_GmNodeAttached",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "level" to GmLevelName.MOON.ordinal.toString(),
                                    "nodeTokenId" to "node-123",
                                )
                        ),
                )

            val updated = GmNftEventUtils.processNodeAttachedEvent(event, baseNft)

            assertEquals("node-123", updated.attachedNodeId)
            assertEquals(GmLevelName.MOON, updated.level)
            assertEquals(101L, updated.blockNumber)
        }

        @Test
        fun `processNodeAttachedEvent throws error if event type is incorrect`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 102,
                    eventType = "InvalidType",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "level" to GmLevelName.MOON.ordinal.toString(),
                                    "nodeTokenId" to "node-123",
                                )
                        ),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processNodeAttachedEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Invalid event type for node attached"))
        }

        @Test
        fun `processNodeAttachedEvent throws error if level is missing`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 103,
                    eventType = "B3TR_GmNodeAttached",
                    params = AbiEventParameters(returnValues = mapOf("nodeTokenId" to "node-123")),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processNodeAttachedEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Missing 'level' param in event"))
        }

        @Test
        fun `processNodeAttachedEvent throws error if nodeTokenId is missing`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 104,
                    eventType = "B3TR_GmNodeAttached",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("level" to GmLevelName.MOON.ordinal.toString())
                        ),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processNodeAttachedEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Missing 'nodeTokenId' param in event"))
        }
    }

    @Nested
    inner class ProcessNodeDetachedEventTest {
        private val baseNft =
            GmNft(
                version = 1,
                blockId = "block-id",
                blockNumber = 100,
                blockTimestamp = 1_000_000,
                id = "0x123",
                owner = "0xabc",
                level = GmLevelName.MARS,
                b3trDonated = java.math.BigInteger.ZERO,
                attachedNodeId = "node-xyz",
            )

        @Test
        fun `processNodeDetachedEvent clears attached node and updates level`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 101,
                    eventType = "B3TR_GmNodeDetached",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("level" to GmLevelName.MOON.ordinal.toString())
                        ),
                )

            val updated = GmNftEventUtils.processNodeDetachedEvent(event, baseNft)

            assertEquals(null, updated.attachedNodeId)
            assertEquals(GmLevelName.MOON, updated.level)
            assertEquals(101L, updated.blockNumber)
        }

        @Test
        fun `processNodeDetachedEvent throws error if event type is not GM_NodeDetached`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 102,
                    eventType = "InvalidType",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("level" to GmLevelName.MOON.ordinal.toString())
                        ),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processNodeDetachedEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Invalid event type for node detached"))
        }

        @Test
        fun `processNodeDetachedEvent throws error if level is missing`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 103,
                    eventType = "B3TR_GmNodeDetached",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processNodeDetachedEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Missing 'level' param in event"))
        }
    }

    @Nested
    inner class ProcessTransferEventTest {
        private val baseNft =
            GmNft(
                version = 1,
                blockId = "block-id",
                blockNumber = 100,
                blockTimestamp = 1_000_000,
                id = "0x123",
                owner = "0xabc",
                level = GmLevelName.EARTH,
                b3trDonated = java.math.BigInteger.ZERO,
                attachedNodeId = null,
            )

        private val gmContractAddress = "0xGM"

        @Test
        fun `processTransferEvent updates owner and block metadata for B3TR_GmTransfer`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 101,
                    address = gmContractAddress,
                    eventType = "B3TR_GmTransfer",
                    params = AbiEventParameters(returnValues = mapOf("to" to "0xNEWOWNER")),
                )

            val updated = GmNftEventUtils.processTransferEvent(event, baseNft)

            assertNotNull(updated)
            assertEquals("0xNEWOWNER", updated?.owner)
            assertEquals(101L, updated?.blockNumber)
            assertEquals(event.blockId, updated?.blockId)
            assertEquals(event.blockTimestamp, updated?.blockTimestamp)
        }

        @Test
        fun `processTransferEvent updates owner and block metadata for B3TR_GmBurned`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 102,
                    address = gmContractAddress,
                    eventType = "B3TR_GmBurned",
                    params = AbiEventParameters(returnValues = mapOf("to" to "0xBURNED")),
                )

            val updated = GmNftEventUtils.processTransferEvent(event, baseNft)

            assertNotNull(updated)
            assertEquals("0xBURNED", updated?.owner)
            assertEquals(102L, updated?.blockNumber)
            assertEquals(event.blockId, updated?.blockId)
            assertEquals(event.blockTimestamp, updated?.blockTimestamp)
        }

        @Test
        fun `processTransferEvent throws error for wrong event type`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 104,
                    address = gmContractAddress,
                    eventType = "NonTransfer",
                    params = AbiEventParameters(returnValues = mapOf("to" to "0xNEWOWNER")),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processTransferEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Invalid event type or missing address"))
        }

        @Test
        fun `processTransferEvent throws error when 'to' param is missing`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 105,
                    address = gmContractAddress,
                    eventType = "B3TR_GmBurned",
                    params = AbiEventParameters(returnValues = emptyMap()),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processTransferEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Missing 'to' param in event"))
        }
    }

    @Nested
    inner class ProcessLevelCheckEventTest {
        private val baseNft =
            GmNft(
                version = 1,
                blockId = "block-id",
                blockNumber = 100,
                blockTimestamp = 1_000_000,
                id = "0x123",
                owner = "0xabc",
                level = GmLevelName.EARTH,
                b3trDonated = java.math.BigInteger.ZERO,
                attachedNodeId = null,
            )

        @Test
        fun `processLevelCheckEvent updates level and block metadata`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 101,
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("level" to GmLevelName.MOON.ordinal.toString())
                        ),
                )

            val updated = GmNftEventUtils.processLevelCheckEvent(event, baseNft)

            assertNotNull(updated)

            updated?.let {
                assertEquals(GmLevelName.MOON, it.level)
                assertEquals(event.blockNumber, it.blockNumber)
                assertEquals(event.blockId, it.blockId)
                assertEquals(event.blockTimestamp, it.blockTimestamp)
            }
        }

        @Test
        fun `processLevelCheckEvent returns existing if newLevel is the same as the old`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 102,
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("level" to GmLevelName.EARTH.ordinal.toString())
                        ),
                )

            val result = GmNftEventUtils.processLevelCheckEvent(event, baseNft)

            assertEquals(baseNft, result)
        }

        @Test
        fun `processLevelCheckEvent works with attached node`() {
            val nftWithNode = baseNft.copy(attachedNodeId = "node-xyz", level = GmLevelName.MARS)
            val event =
                buildIndexedEvent(
                    blockNumber = 103,
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("level" to GmLevelName.MOON.ordinal.toString())
                        ),
                )

            val updated = GmNftEventUtils.processLevelCheckEvent(event, nftWithNode)
            assertNotNull(updated)

            updated?.let {
                assertEquals(GmLevelName.MOON, it.level)
                assertEquals("node-xyz", it.attachedNodeId)
            }
        }

        @Test
        fun `processLevelCheckEvent throws if event doesn't contain level`() {
            val event =
                buildIndexedEvent(
                    blockNumber = 104,
                    params = AbiEventParameters(returnValues = emptyMap()),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processLevelCheckEvent(event, baseNft)
                }

            assertTrue(exception.message!!.contains("Missing 'level' param in event"))
        }
    }

    @Nested
    inner class GroupByTokenIdTest {

        @Test
        fun `groupByTokenId groups multiple events correctly by tokenId`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 3,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0xA")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 1,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0xB")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 2,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0xA")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 5,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0xC")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 4,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0xB")),
                    ),
                )

            val grouped = GmNftEventUtils.groupByTokenId(events)

            assertEquals(3, grouped.size)
            assertEquals(listOf(2L, 3L), grouped["0xa"]!!.map { it.blockNumber })
            assertEquals(listOf(1L, 4L), grouped["0xb"]!!.map { it.blockNumber })
            assertEquals(listOf(5L), grouped["0xc"]!!.map { it.blockNumber })
        }

        @Test
        fun `groupByTokenId throws exception when event is missing tokenId`() {
            val validEvent =
                buildIndexedEvent(
                    blockNumber = 1,
                    params = AbiEventParameters(returnValues = mapOf("tokenId" to "0x1")),
                )
            val invalidEvent =
                buildIndexedEvent(
                    blockNumber = 2,
                    params = AbiEventParameters(returnValues = emptyMap()),
                )

            val exception =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.groupByTokenId(listOf(validEvent, invalidEvent))
                }

            assertTrue(exception.message!!.contains("Missing tokenId in event"))
        }

        @Test
        fun `groupByTokenId groups events with the same tokenId regardless of case (case insensitive)`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 2,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0xabc")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 1,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0xAbC")),
                    ),
                )

            val grouped = GmNftEventUtils.groupByTokenId(events)

            assertEquals(1, grouped.size)
        }

        @Test
        fun `groupByTokenId correctly groups multiple tokenIds with unsorted input`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 5,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0x2")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 3,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0x1")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 2,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0x2")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 1,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0x1")),
                    ),
                )

            val grouped = GmNftEventUtils.groupByTokenId(events)

            assertEquals(2, grouped.size)
            assertEquals(listOf(1L, 3L), grouped["0x1"]!!.map { it.blockNumber })
            assertEquals(listOf(2L, 5L), grouped["0x2"]!!.map { it.blockNumber })
        }

        @Test
        fun `groupByTokenId preserves event order based on blockNumber within each group`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 8,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0x9")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 3,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0x9")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 5,
                        params = AbiEventParameters(returnValues = mapOf("tokenId" to "0x9")),
                    ),
                )

            val grouped = GmNftEventUtils.groupByTokenId(events)

            assertEquals(1, grouped.size)
            assertEquals(listOf(3L, 5L, 8L), grouped["0x9"]!!.map { it.blockNumber })
        }

        @Test
        fun `groupByTokenId throws exception when all events are missing tokenId`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 1,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockNumber = 2,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                )

            val exception =
                assertThrows<IllegalStateException> { GmNftEventUtils.groupByTokenId(events) }

            assertTrue(exception.message!!.contains("Missing tokenId in event"))
        }

        @Test
        fun `groupByTokenId returns empty map for empty input list`() {
            val grouped = GmNftEventUtils.groupByTokenId(emptyList())
            assertTrue(grouped.isEmpty())
        }
    }

    @Nested
    inner class GroupByBlockNumber {
        @Test
        fun `groupByBlockNumber groups events by blockNumber`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 1L,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockNumber = 2,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockNumber = 1,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                )

            val grouped = GmNftEventUtils.groupByBlockNumber(events)

            assertEquals(2, grouped.size)
            assertEquals(2, grouped[1]!!.size)
            assertEquals(1, grouped[2]!!.size)
        }

        @Test
        fun `groupByBlockNumber returns empty map for empty input list`() {
            val grouped = GmNftEventUtils.groupByBlockNumber(emptyList())
            assertTrue(grouped.isEmpty())
        }

        @Test
        fun `groupByBlockNumber returns map sorted by blockNumber`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 3,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockNumber = 1,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                    buildIndexedEvent(
                        blockNumber = 2,
                        params = AbiEventParameters(returnValues = emptyMap()),
                    ),
                )

            val grouped = GmNftEventUtils.groupByBlockNumber(events)

            assertEquals(listOf(1L, 2L, 3L), grouped.keys.toList())
        }
    }
}
