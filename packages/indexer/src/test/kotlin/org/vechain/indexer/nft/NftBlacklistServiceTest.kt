package org.vechain.indexer.nft

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class NftBlacklistServiceTest {
    @MockK lateinit var repository: NftBlacklistRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private val blacklistContract = "0x0f9b01618cd5e0030f8e26ff61bc1349cb9eb8d5"
    private val collectionA = "0xAAAA000000000000000000000000000000000001"
    private val collectionB = "0xBBBB000000000000000000000000000000000002"

    private lateinit var service: NftBlacklistService

    @BeforeEach
    fun setUp() {
        service = NftBlacklistService(repository, mongoTemplate, inlineVersioningProperties)
        every { repository.findById(any<String>()) } returns Optional.empty()
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
    }

    private fun blacklistEvent(
        eventType: String,
        nft: String,
        blockNumber: Long = 10L,
        blockId: String = "0xblock$blockNumber",
    ) =
        buildIndexedEvent(
            id = "$eventType-$nft-$blockNumber",
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10L,
            address = blacklistContract,
            eventType = eventType,
            params = AbiEventParameters(returnValues = mapOf("nft" to nft), eventType = eventType),
        )

    @Test
    fun `ignores blocks without blacklist events`() {
        val (updated, existing) =
            service.processBlock(listOf(buildIndexedEvent(eventType = "Transfer")))

        assertTrue(updated.isEmpty())
        assertTrue(existing.isEmpty())
    }

    @Test
    fun `NFTBlacklisted creates a flagged document keyed by the normalised address`() {
        val (updated, existing) =
            service.processBlock(
                listOf(blacklistEvent(NftBlacklistService.NFT_BLACKLISTED, collectionA))
            )

        val doc = updated.single()
        assertEquals(collectionA.lowercase(), doc.id)
        assertTrue(doc.isBlacklisted)
        assertEquals(10L, doc.blockNumber)
        assertEquals(1, doc.version)
        assertTrue(existing.isEmpty())
    }

    @Test
    fun `NFTWhitelisted bumps the version and clears the flag on an existing document`() {
        val stored =
            NftBlacklist(
                id = collectionA.lowercase(),
                isBlacklisted = true,
                blockId = "0xblock5",
                blockNumber = 5L,
                blockTimestamp = 50L,
                version = 1,
            )
        every { repository.findAllById(any<Iterable<String>>()) } returns listOf(stored)

        val (updated, existing) =
            service.processBlock(
                listOf(blacklistEvent(NftBlacklistService.NFT_WHITELISTED, collectionA, 12L))
            )

        val doc = updated.single()
        assertFalse(doc.isBlacklisted)
        assertEquals(2, doc.version)
        assertEquals(12L, doc.blockNumber)
        assertEquals(listOf(stored), existing)
    }

    @Test
    fun `the last event for an address in a block wins`() {
        val (updated, _) =
            service.processBlock(
                listOf(
                    blacklistEvent(NftBlacklistService.NFT_BLACKLISTED, collectionA),
                    blacklistEvent(NftBlacklistService.NFT_WHITELISTED, collectionA),
                    blacklistEvent(NftBlacklistService.NFT_BLACKLISTED, collectionB),
                )
            )

        val byId = updated.associateBy { it.id }
        assertEquals(2, byId.size)
        assertFalse(byId.getValue(collectionA.lowercase()).isBlacklisted)
        assertTrue(byId.getValue(collectionB.lowercase()).isBlacklisted)
    }
}
