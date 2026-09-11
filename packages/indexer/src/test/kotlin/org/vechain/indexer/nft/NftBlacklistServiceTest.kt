package org.vechain.indexer.nft

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

internal class NftBlacklistServiceTest {
    private val repository = mockk<NftBlacklistWriteRepository>(relaxed = true)
    private val service = NftBlacklistService(repository)

    private val blacklistContract = "0x0f9b01618cd5e0030f8e26ff61bc1349cb9eb8d5"
    private val collectionA = "0xAAAA000000000000000000000000000000000001"
    private val collectionB = "0xBBBB000000000000000000000000000000000002"

    private fun blacklistEvent(eventType: String, nft: String, blockNumber: Long = 10L) =
        buildIndexedEvent(
            id = "$eventType-$nft-$blockNumber",
            blockId = "0xblock$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10L,
            address = blacklistContract,
            eventType = eventType,
            params = AbiEventParameters(returnValues = mapOf("nft" to nft), eventType = eventType),
        )

    @Test
    fun `ignores blocks without blacklist events`() {
        assertTrue(
            service.processBlock(listOf(buildIndexedEvent(eventType = "Transfer"))).isEmpty()
        )
    }

    @Test
    fun `NFTBlacklisted yields a flagged state keyed by the normalised address`() {
        val state =
            service
                .processBlock(
                    listOf(blacklistEvent(NftBlacklistService.NFT_BLACKLISTED, collectionA))
                )
                .single()

        assertEquals(collectionA.lowercase(), state.contractAddress)
        assertTrue(state.isBlacklisted)
        assertEquals(10L, state.blockNumber)
        assertEquals("0xblock10", state.blockId)
        assertEquals(100L, state.blockTimestamp)
    }

    @Test
    fun `the last event for an address in a block wins, one state per block otherwise`() {
        val states =
            service.processBlock(
                listOf(
                    blacklistEvent(NftBlacklistService.NFT_BLACKLISTED, collectionA),
                    blacklistEvent(NftBlacklistService.NFT_WHITELISTED, collectionA),
                    blacklistEvent(NftBlacklistService.NFT_BLACKLISTED, collectionB),
                    blacklistEvent(NftBlacklistService.NFT_BLACKLISTED, collectionA, 12L),
                )
            )

        assertEquals(
            listOf(collectionA.lowercase() to false, collectionB.lowercase() to true) +
                (collectionA.lowercase() to true),
            states.map { it.contractAddress to it.isBlacklisted },
        )
        assertEquals(listOf(10L, 10L, 12L), states.map { it.blockNumber })
    }

    @Test
    fun `save hands the states to the writer and skips an empty list`() {
        val states =
            service.processBlock(
                listOf(blacklistEvent(NftBlacklistService.NFT_BLACKLISTED, collectionA))
            )

        service.save(states)
        service.save(emptyList())

        verify(exactly = 1) { repository.save(states) }
        assertFalse(states.isEmpty())
    }
}
