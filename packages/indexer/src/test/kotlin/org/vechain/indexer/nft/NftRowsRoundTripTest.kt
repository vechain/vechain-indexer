package org.vechain.indexer.nft

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.vechain.indexer.fixtures.IndexedEventsFixtures

/** `assemble(flatten(nft)) == nft` over every NFT fixture, projected the way the indexer does. */
class NftRowsRoundTripTest {

    private val service = NftService(mockk(relaxed = true))

    private val fixtures =
        mapOf(
            "NFT_TRANSFER" to IndexedEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER,
            "NFT_TRANSFER_DUPLICATE" to IndexedEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE,
            "NFT_MINT" to IndexedEventsFixtures.INDEXED_EVENTS_NFT_MINT,
        )

    @TestFactory
    fun `every fixture survives flatten and assemble`(): List<DynamicTest> =
        fixtures.map { (name, events) ->
            dynamicTest(name) {
                val nfts = service.processBlock(events)
                assertTrue(nfts.isNotEmpty())
                nfts.forEach { assertEquals(it, NftRowMapping.assemble(NftRowMapping.flatten(it))) }
            }
        }
}
