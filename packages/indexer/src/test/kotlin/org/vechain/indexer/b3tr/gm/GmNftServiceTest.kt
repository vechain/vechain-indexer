package org.vechain.indexer.b3tr.gm

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner

@ExtendWith(MockKExtension::class)
internal class GmNftServiceTest {
    @MockK lateinit var repository: GmNftRepository

    @MockK lateinit var gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>

    @MockK lateinit var pruner: TargetedPruner<GmNft, GmNftArchive>

    @MockK(relaxed = true) lateinit var mongoTemplate: MongoTemplate

    private lateinit var service: GmNftService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = GmNftService(repository, gmNftArchiveService, pruner, mongoTemplate)
    }

    @Test
    fun `processEvents should return updatedNfts and archiveNfts correctly`() {
        // Create two events for the same tokenId, different blockNumbers
        val tokenId = "token-1"
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-1",
                blockNumber = 1L,
                eventType = "B3TR_GmMinted",
                params =
                    AbiEventParameters(returnValues = mapOf("tokenId" to tokenId, "to" to "owner1")),
            )
        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockId = "block-2",
                blockNumber = 2L,
                eventType = "B3TR_GmTransfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("tokenId" to tokenId, "from" to "owner1", "to" to "owner2")
                    ),
            )

        // Mock repository to return null for first lookup
        every { repository.findByIdOrNull(tokenId) } returns null

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        // Only one updated NFT should be returned (latest event)
        assertEquals(1, updated.size)
        // Only one archived NFT should be returned (previous state)
        assertEquals(1, archived.size)
        // The updated NFT should have id = tokenId
        assertEquals(tokenId, updated.first().id)
        assertEquals("owner2", updated.first().owner)
        assertEquals(2, updated.first().version)

        // The archived NFT should also have id = tokenId
        assertEquals(tokenId, archived.first().id)
        assertEquals("owner1", archived.first().owner)
        assertEquals(1, archived.first().version)
    }

    @Test
    fun `processEvents with empty events returns empty lists`() {
        val (updated, archived) = service.processEvents(emptyList())
        assertEquals(0, updated.size)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents with multiple tokenIds returns correct updated and archived lists`() {
        val tokenId1 = "token-1"
        val tokenId2 = "token-2"
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                eventType = "B3TR_GmMinted",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("tokenId" to tokenId1, "to" to "owner1")
                    ),
            )
        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 2L,
                eventType = "B3TR_GmMinted",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("tokenId" to tokenId2, "to" to "owner2")
                    ),
            )
        every { repository.findByIdOrNull(tokenId1) } returns null
        every { repository.findByIdOrNull(tokenId2) } returns null

        val (updated, archived) = service.processEvents(listOf(event1, event2))
        assertEquals(2, updated.size)
        updated.forEach { assertEquals(1, it.version) }
        assertEquals(0, archived.size)
        assertEquals(setOf(tokenId1, tokenId2), updated.map { it.id }.toSet())
    }

    @Test
    fun `processEvents archives existing NFT and updates it`() {
        val tokenId = "token-1"

        val event1 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 2L,
                eventType = "B3TR_GmTransfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("tokenId" to tokenId, "from" to "owner1", "to" to "owner2")
                    ),
            )
        val existingNft =
            GmNft(
                tokenId = tokenId,
                version = 1,
                blockId = "block1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                owner = "owner1",
                level = GmLevelName.EARTH,
                attachedNodeId = null,
                b3trDonated = BigInteger.valueOf(500),
            )

        every { repository.findByIdOrNull(tokenId) } returns existingNft

        val (updated, archived) = service.processEvents(listOf(event1))

        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals(tokenId, updated.first().id)
        assertEquals(tokenId, archived.first().id)
        assertEquals("owner2", updated.first().owner)
        assertEquals("owner1", archived.first().owner)
        assertEquals(2, updated.first().version)
        assertEquals(1, archived.first().version)
    }

    @Test
    fun `processEvents is the existing and updated NFT are the same, no archive or update occurs`() {
        val tokenId = "token-1"

        val event1 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 2L,
                eventType = "B3TR_GmNodeLevel",
                params =
                    AbiEventParameters(returnValues = mapOf("tokenId" to tokenId, "level" to 1)),
            )

        // Existing NFT has same level as event1 so no change should occur
        val existingNft =
            GmNft(
                tokenId = tokenId,
                version = 1,
                blockId = "block1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                owner = "owner1",
                level = GmLevelName.EARTH,
                attachedNodeId = null,
                b3trDonated = BigInteger.valueOf(500),
            )

        every { repository.findByIdOrNull(tokenId) } returns existingNft

        val (updated, archived) = service.processEvents(listOf(event1))

        assertEquals(0, updated.size)
        assertEquals(0, archived.size)
    }
}
