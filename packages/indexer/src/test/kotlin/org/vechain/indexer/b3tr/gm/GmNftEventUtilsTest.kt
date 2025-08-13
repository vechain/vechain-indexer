package org.vechain.indexer.b3tr.gm

import java.math.BigInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

class GmNftEventUtilsTest {
    // ---------- processTokenEvent (router) ----------
    @Nested
    inner class ProcessTokenEventTest {
        @Test
        fun `mint route returns new NFT when no existing`() {
            val eMint =
                buildIndexedEvent(
                    eventType = "B3TR_GmMinted",
                    blockNumber = 10L,
                    blockId = "blk",
                    blockTimestamp = 111L,
                    params = AbiEventParameters(mapOf("tokenId" to "0xMint", "to" to "0xAlice")),
                )

            val nft = GmNftEventUtils.processTokenEvent(eMint, null)

            assertNotNull(nft)
            assertEquals("0xMint", nft!!.id)
            assertEquals("0xAlice", nft.owner)
            assertEquals(GmLevelName.EARTH, nft.level)
        }

        @Test
        fun `upgrade route requires existing and updates level & donation`() {
            val existing =
                GmNft(
                    id = "0xT",
                    version = 0,
                    blockId = "b1",
                    blockNumber = 1L,
                    blockTimestamp = 1L,
                    owner = "0xA",
                    level = GmLevelName.EARTH,
                    b3trDonated = BigInteger.ZERO,
                    attachedNodeId = null,
                )
            val eUpgrade =
                buildIndexedEvent(
                    eventType = "B3TR_GmUpgrade",
                    blockNumber = 2L,
                    blockId = "b2",
                    blockTimestamp = 2L,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "newLevel" to GmLevelName.MOON.ordinal.toString(),
                                "value" to "150",
                            )
                        ),
                )

            val updated = GmNftEventUtils.processTokenEvent(eUpgrade, existing)

            assertNotNull(updated)
            assertEquals(GmLevelName.MOON, updated!!.level)
            assertEquals(BigInteger.valueOf(150), updated.b3trDonated)
            assertEquals(2L, updated.blockNumber)
        }

        @Test
        fun `upgrade route with null existing throws NPE due to contract`() {
            val eUpgrade =
                buildIndexedEvent(
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(
                            mapOf("newLevel" to GmLevelName.MOON.ordinal.toString(), "value" to "1")
                        ),
                )
            assertThrows<NullPointerException> { GmNftEventUtils.processTokenEvent(eUpgrade, null) }
        }

        @Test
        fun `unknown event type returns null`() {
            val eOther = buildIndexedEvent(eventType = "SOMETHING_ELSE")
            val res = GmNftEventUtils.processTokenEvent(eOther, null)
            assertNull(res)
        }
    }

    // ---------- processMintedEvent ----------
    @Nested
    inner class ProcessMintedEventTest {
        @Test
        fun `creates NFT with correct fields`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmMinted",
                    blockNumber = 123L,
                    blockTimestamp = 1_666_000L,
                    blockId = "block-mint",
                    params = AbiEventParameters(mapOf("tokenId" to "0xMinted", "to" to "0xOwner")),
                )

            val nft = GmNftEventUtils.processMintedEvent(e)

            assertEquals("0xMinted", nft.id)
            assertEquals("0xOwner", nft.owner)
            assertEquals(GmLevelName.EARTH, nft.level)
            assertEquals("block-mint", nft.blockId)
            assertEquals(123L, nft.blockNumber)
            assertEquals(1_666_000L, nft.blockTimestamp)
            assertEquals(BigInteger.ZERO, nft.b3trDonated)
            assertNull(nft.attachedNodeId)
        }

        @Test
        fun `throws if tokenId missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmMinted",
                    params = AbiEventParameters(mapOf("to" to "0xOwner")),
                )
            val ex = assertThrows<IllegalStateException> { GmNftEventUtils.processMintedEvent(e) }
            assertTrue(ex.message!!.contains("Missing 'tokenId'"))
        }

        @Test
        fun `throws if owner missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmMinted",
                    params = AbiEventParameters(mapOf("tokenId" to "0xMinted")),
                )
            val ex = assertThrows<IllegalStateException> { GmNftEventUtils.processMintedEvent(e) }
            assertTrue(ex.message!!.contains("Missing 'to'"))
        }
    }

    // ---------- processUpgradedEvent ----------
    @Nested
    inner class ProcessUpgradedEventTest {
        private val base =
            GmNft(
                id = "0x123",
                version = 1,
                blockId = "b",
                blockNumber = 100L,
                blockTimestamp = 1_000_000L,
                owner = "0xabc",
                level = GmLevelName.EARTH,
                b3trDonated = BigInteger.valueOf(100),
                attachedNodeId = null,
            )

        @Test
        fun `updates level, donation, and block metadata`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmUpgrade",
                    blockNumber = 101L,
                    blockId = "b2",
                    blockTimestamp = 2_000_000L,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "newLevel" to GmLevelName.MOON.ordinal.toString(),
                                "value" to "200",
                            )
                        ),
                )

            val updated = GmNftEventUtils.processUpgradedEvent(e, base)

            assertEquals(GmLevelName.MOON, updated.level)
            assertEquals(BigInteger.valueOf(300), updated.b3trDonated)
            assertEquals(101L, updated.blockNumber)
            assertEquals("b2", updated.blockId)
            assertEquals(2_000_000L, updated.blockTimestamp)
            // version NOT incremented here (service does that)
        }

        @Test
        fun `throws if newLevel missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmUpgrade",
                    params = AbiEventParameters(mapOf("value" to "10")),
                )
            val ex =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processUpgradedEvent(e, base)
                }
            assertTrue(ex.message!!.contains("Missing 'newLevel'"))
        }

        @Test
        fun `throws if value missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmUpgrade",
                    params =
                        AbiEventParameters(mapOf("newLevel" to GmLevelName.MARS.ordinal.toString())),
                )
            val ex =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processUpgradedEvent(e, base)
                }
            assertTrue(ex.message!!.contains("Missing 'b3trDonated'"))
        }
    }

    // ---------- processNodeAttachedEvent ----------
    @Nested
    inner class ProcessNodeAttachedEventTest {
        private val base =
            GmNft(
                id = "0x123",
                version = 1,
                blockId = "b",
                blockNumber = 100L,
                blockTimestamp = 1_000_000L,
                owner = "0xabc",
                level = GmLevelName.MARS,
                b3trDonated = BigInteger.ZERO,
                attachedNodeId = null,
            )

        @Test
        fun `sets node id and level`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmNodeAttached",
                    blockNumber = 101L,
                    params =
                        AbiEventParameters(
                            mapOf(
                                "level" to GmLevelName.MOON.ordinal.toString(),
                                "nodeTokenId" to "node-123",
                            )
                        ),
                )

            val updated = GmNftEventUtils.processNodeAttachedEvent(e, base)

            assertEquals("node-123", updated.attachedNodeId)
            assertEquals(GmLevelName.MOON, updated.level)
            assertEquals(101L, updated.blockNumber)
        }

        @Test
        fun `throws if level missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmNodeAttached",
                    params = AbiEventParameters(mapOf("nodeTokenId" to "node-123")),
                )
            val ex =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processNodeAttachedEvent(e, base)
                }
            assertTrue(ex.message!!.contains("Missing 'level'"))
        }

        @Test
        fun `throws if nodeTokenId missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmNodeAttached",
                    params =
                        AbiEventParameters(mapOf("level" to GmLevelName.MOON.ordinal.toString())),
                )
            val ex =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processNodeAttachedEvent(e, base)
                }
            assertTrue(ex.message!!.contains("Missing 'nodeTokenId'"))
        }
    }

    // ---------- processNodeDetachedEvent ----------
    @Nested
    inner class ProcessNodeDetachedEventTest {
        private val base =
            GmNft(
                id = "0x123",
                version = 1,
                blockId = "b",
                blockNumber = 100L,
                blockTimestamp = 1_000_000L,
                owner = "0xabc",
                level = GmLevelName.MARS,
                b3trDonated = BigInteger.ZERO,
                attachedNodeId = "node-xyz",
            )

        @Test
        fun `clears node and updates level`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmNodeDetached",
                    blockNumber = 101L,
                    params =
                        AbiEventParameters(mapOf("level" to GmLevelName.MOON.ordinal.toString())),
                )

            val updated = GmNftEventUtils.processNodeDetachedEvent(e, base)

            assertNull(updated.attachedNodeId)
            assertEquals(GmLevelName.MOON, updated.level)
            assertEquals(101L, updated.blockNumber)
        }

        @Test
        fun `throws if level missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmNodeDetached",
                    params = AbiEventParameters(emptyMap()),
                )
            val ex =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processNodeDetachedEvent(e, base)
                }
            assertTrue(ex.message!!.contains("Missing 'level'"))
        }
    }

    // ---------- processTransferEvent ----------
    @Nested
    inner class ProcessTransferEventTest {
        private val base =
            GmNft(
                id = "0x123",
                version = 1,
                blockId = "b",
                blockNumber = 100L,
                blockTimestamp = 1_000_000L,
                owner = "0xabc",
                level = GmLevelName.EARTH,
                b3trDonated = BigInteger.ZERO,
                attachedNodeId = null,
            )

        @Test
        fun `updates owner and block metadata`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmTransfer",
                    blockNumber = 101L,
                    blockId = "b2",
                    blockTimestamp = 2_000_000L,
                    params = AbiEventParameters(mapOf("to" to "0xNEW")),
                )
            val updated = GmNftEventUtils.processTransferEvent(e, base)

            assertNotNull(updated)
            assertEquals("0xNEW", updated!!.owner)
            assertEquals(101L, updated.blockNumber)
            assertEquals("b2", updated.blockId)
            assertEquals(2_000_000L, updated.blockTimestamp)
        }

        @Test
        fun `throws when 'to' missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmTransfer",
                    params = AbiEventParameters(emptyMap()),
                )
            val ex =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processTransferEvent(e, base)
                }
            assertTrue(ex.message!!.contains("Missing 'to'"))
        }
    }

    // ---------- processLevelCheckEvent ----------
    @Nested
    inner class ProcessLevelCheckEventTest {
        private val base =
            GmNft(
                id = "0x123",
                version = 1,
                blockId = "b",
                blockNumber = 100L,
                blockTimestamp = 1_000_000L,
                owner = "0xabc",
                level = GmLevelName.EARTH,
                b3trDonated = BigInteger.ZERO,
                attachedNodeId = null,
            )

        @Test
        fun `updates level when changed`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmNodeLevel",
                    blockNumber = 101L,
                    blockId = "b2",
                    blockTimestamp = 2_000_000L,
                    params =
                        AbiEventParameters(mapOf("level" to GmLevelName.MOON.ordinal.toString())),
                )
            val updated = GmNftEventUtils.processLevelCheckEvent(e, base)

            assertEquals(GmLevelName.MOON, updated.level)
            assertEquals(101L, updated.blockNumber)
            assertEquals("b2", updated.blockId)
            assertEquals(2_000_000L, updated.blockTimestamp)
        }

        @Test
        fun `returns existing when level unchanged`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmNodeLevel",
                    params =
                        AbiEventParameters(mapOf("level" to GmLevelName.EARTH.ordinal.toString())),
                )
            val res = GmNftEventUtils.processLevelCheckEvent(e, base)
            assertSame(base, res)
        }

        @Test
        fun `throws when level missing`() {
            val e =
                buildIndexedEvent(
                    eventType = "B3TR_GmNodeLevel",
                    params = AbiEventParameters(emptyMap()),
                )
            val ex =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.processLevelCheckEvent(e, base)
                }
            assertTrue(ex.message!!.contains("Missing 'level'"))
        }
    }

    // ---------- groupByTokenId ----------
    @Nested
    inner class GroupByTokenIdTest {
        @Test
        fun `groups and sorts by blockNumber per token`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 3,
                        params = AbiEventParameters(mapOf("tokenId" to "0xA")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 1,
                        params = AbiEventParameters(mapOf("tokenId" to "0xB")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 2,
                        params = AbiEventParameters(mapOf("tokenId" to "0xA")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 5,
                        params = AbiEventParameters(mapOf("tokenId" to "0xC")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 4,
                        params = AbiEventParameters(mapOf("tokenId" to "0xB")),
                    ),
                )

            val grouped = GmNftEventUtils.groupByTokenId(events)

            assertEquals(3, grouped.size)
            assertEquals(listOf(2L, 3L), grouped["0xa"]!!.map { it.blockNumber })
            assertEquals(listOf(1L, 4L), grouped["0xb"]!!.map { it.blockNumber })
            assertEquals(listOf(5L), grouped["0xc"]!!.map { it.blockNumber })
        }

        @Test
        fun `throws when any event missing tokenId`() {
            val ok =
                buildIndexedEvent(
                    blockNumber = 1,
                    params = AbiEventParameters(mapOf("tokenId" to "0x1")),
                )
            val bad = buildIndexedEvent(blockNumber = 2, params = AbiEventParameters(emptyMap()))

            val ex =
                assertThrows<IllegalStateException> {
                    GmNftEventUtils.groupByTokenId(listOf(ok, bad))
                }
            assertTrue(ex.message!!.contains("Missing tokenId"))
        }

        @Test
        fun `case-insensitive grouping`() {
            val events =
                listOf(
                    buildIndexedEvent(
                        blockNumber = 2,
                        params = AbiEventParameters(mapOf("tokenId" to "0xabc")),
                    ),
                    buildIndexedEvent(
                        blockNumber = 1,
                        params = AbiEventParameters(mapOf("tokenId" to "0xAbC")),
                    ),
                )
            val grouped = GmNftEventUtils.groupByTokenId(events)
            assertEquals(1, grouped.size)
        }

        @Test
        fun `returns empty for empty input`() {
            val grouped = GmNftEventUtils.groupByTokenId(emptyList())
            assertTrue(grouped.isEmpty())
        }
    }
}
