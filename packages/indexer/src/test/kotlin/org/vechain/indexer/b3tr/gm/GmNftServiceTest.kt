package org.vechain.indexer.b3tr.gm

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class GmNftServiceTest {
    @MockK lateinit var repository: GmNftWriteRepository

    private lateinit var service: GmNftService

    @BeforeEach
    fun setUp() {
        service = GmNftService(repository)
        every { repository.findCurrentByTokenIds(any()) } returns emptyList()
    }

    private fun event(
        eventType: String,
        tokenId: String,
        blockNumber: Long,
        vararg params: Pair<String, Any>,
    ) =
        buildIndexedEvent(
            blockId = "block-$blockNumber",
            blockNumber = blockNumber,
            eventType = eventType,
            params = AbiEventParameters(returnValues = mapOf("tokenId" to tokenId, *params)),
        )

    private fun nft(tokenId: String, owner: String, level: GmLevelName = GmLevelName.EARTH) =
        GmNft(
            tokenId = tokenId,
            blockId = "block-1",
            blockNumber = 1L,
            blockTimestamp = 100L,
            owner = owner,
            level = level,
            attachedNodeId = null,
            b3trDonated = BigInteger.valueOf(500),
        )

    @Test
    fun `a mint and a later transfer yield one row per block`() {
        val rows =
            service.processEvents(
                listOf(
                    event("B3TR_GmMinted", "1", 1, "to" to "owner1"),
                    event("B3TR_GmTransfer", "1", 2, "from" to "owner1", "to" to "owner2"),
                )
            )

        assertEquals(listOf(1L, 2L), rows.map { it.blockNumber })
        assertEquals(listOf("owner1", "owner2"), rows.map { it.owner })
        assertEquals(listOf("1", "1"), rows.map { it.tokenId })
    }

    @Test
    fun `no events yield no rows`() {
        assertTrue(service.processEvents(emptyList()).isEmpty())
    }

    @Test
    fun `two tokens minted in the same block yield a row each`() {
        val rows =
            service.processEvents(
                listOf(
                    event("B3TR_GmMinted", "1", 1, "to" to "owner1"),
                    event("B3TR_GmMinted", "2", 1, "to" to "owner2"),
                )
            )

        assertEquals(setOf("1", "2"), rows.map { it.tokenId }.toSet())
        assertEquals(setOf(1L), rows.map { it.blockNumber }.toSet())
    }

    @Test
    fun `a transfer carries the stored token forward`() {
        every { repository.findCurrentByTokenIds(setOf("1")) } returns listOf(nft("1", "owner1"))

        val updated =
            service
                .processEvents(
                    listOf(event("B3TR_GmTransfer", "1", 2, "from" to "owner1", "to" to "owner2"))
                )
                .single()

        assertEquals("owner2", updated.owner)
        assertEquals(BigInteger.valueOf(500), updated.b3trDonated)
        assertEquals(2L, updated.blockNumber)
    }

    @Test
    fun `an event that changes nothing writes no row`() {
        every { repository.findCurrentByTokenIds(setOf("1")) } returns listOf(nft("1", "owner1"))

        assertTrue(
            service.processEvents(listOf(event("B3TR_GmNodeLevel", "1", 2, "level" to 1))).isEmpty()
        )
    }
}
