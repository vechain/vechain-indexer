package org.vechain.indexer.b3tr.gm

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class GmNftServiceTest {
    @MockK lateinit var repository: GmNftRepository

    @MockK lateinit var gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>

    private lateinit var service: GmNftService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = GmNftService(repository, gmNftArchiveService)

        // default stubs
        every { gmNftArchiveService.saveAll(any<List<GmNft>>()) } just Runs
        every { repository.saveAll(any<Iterable<GmNft>>()) } answers { firstArg() }
        every { repository.deleteAllById(any<Iterable<String>>()) } just Runs
    }

    @Test
    fun `mint then transfer - saves updated only, no archive when no existing`() {
        val tokenId = "token-1"
        val eMint =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                eventType = "B3TR_GmMinted",
                params = AbiEventParameters(mapOf("tokenId" to tokenId, "to" to "owner1")),
            )
        val eTransfer =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 2L,
                eventType = "B3TR_GmTransfer",
                params =
                    AbiEventParameters(
                        mapOf("tokenId" to tokenId, "from" to "owner1", "to" to "owner2")
                    ),
            )

        every { repository.findByIdOrNull(tokenId) } returns null

        val savedSlot = slot<Iterable<GmNft>>()
        every { repository.saveAll(capture(savedSlot)) } answers { savedSlot.captured }

        service.processEvents(listOf(eMint, eTransfer))

        verify(exactly = 1) { repository.saveAll(any<Iterable<GmNft>>()) }
        verify(exactly = 0) { gmNftArchiveService.saveAll(any<List<GmNft>>()) }

        val saved = savedSlot.captured.toList()
        assertEquals(1, saved.size)
        assertEquals(tokenId, saved[0].id)
        assertEquals("owner2", saved[0].owner) // latest owner after transfer
        assertEquals(1, saved[0].version) // minted v0 → saved as v1
    }

    @Test
    fun `empty events - no writes`() {
        service.processEvents(emptyList())

        verify(exactly = 0) { repository.saveAll(any<Iterable<GmNft>>()) }
        verify(exactly = 0) { gmNftArchiveService.saveAll(any<List<GmNft>>()) }
        verify(exactly = 0) { repository.deleteAllById(any<Iterable<String>>()) }
    }

    @Test
    fun `two mints different tokens - saves two, no archive`() {
        val tokenId1 = "t1"
        val tokenId2 = "t2"
        val e1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                eventType = "B3TR_GmMinted",
                params = AbiEventParameters(mapOf("tokenId" to tokenId1, "to" to "owner1")),
            )
        val e2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 2L,
                eventType = "B3TR_GmMinted",
                params = AbiEventParameters(mapOf("tokenId" to tokenId2, "to" to "owner2")),
            )
        every { repository.findByIdOrNull(any()) } returns null

        val savedSlot = slot<Iterable<GmNft>>()
        every { repository.saveAll(capture(savedSlot)) } answers { savedSlot.captured }

        service.processEvents(listOf(e1, e2))

        verify(exactly = 1) { repository.saveAll(any<Iterable<GmNft>>()) }
        verify(exactly = 0) { gmNftArchiveService.saveAll(any<List<GmNft>>()) }

        val saved = savedSlot.captured.toList()
        assertEquals(setOf(tokenId1, tokenId2), saved.map { it.id }.toSet())
    }

    @Test
    fun `existing then transfer - archives old and saves updated`() {
        val tokenId = "token-1"
        val existing =
            GmNft(
                tokenId = tokenId,
                version = 3,
                blockId = "b",
                blockNumber = 10L,
                blockTimestamp = 100L,
                owner = "owner1",
                level = GmLevelName.EARTH,
                attachedNodeId = null,
                b3trDonated = BigInteger.ZERO,
            )
        val eTransfer =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 12L,
                eventType = "B3TR_GmTransfer",
                params =
                    AbiEventParameters(
                        mapOf("tokenId" to tokenId, "from" to "owner1", "to" to "owner2")
                    ),
            )

        every { repository.findByIdOrNull(tokenId) } returns existing

        val savedSlot = slot<Iterable<GmNft>>()
        val archivedSlot = slot<List<GmNft>>()
        every { repository.saveAll(capture(savedSlot)) } answers { savedSlot.captured }
        every { gmNftArchiveService.saveAll(capture(archivedSlot)) } just Runs

        service.processEvents(listOf(eTransfer))

        verify(exactly = 1) { gmNftArchiveService.saveAll(any<List<GmNft>>()) }
        verify(exactly = 1) { repository.saveAll(any<Iterable<GmNft>>()) }

        val archived = archivedSlot.captured
        assertEquals(1, archived.size)
        assertEquals(tokenId, archived[0].id)
        assertEquals("owner1", archived[0].owner)

        val saved = savedSlot.captured.toList()
        assertEquals(1, saved.size)
        assertEquals(tokenId, saved[0].id)
        assertEquals("owner2", saved[0].owner)
        assertEquals(existing.version + 1, saved[0].version)
    }

    @Test
    fun `burn - archives existing and deletes by id`() {
        val tokenId = "token-1"
        val existing =
            GmNft(
                tokenId = tokenId,
                version = 1,
                blockId = "b",
                blockNumber = 1L,
                blockTimestamp = 10L,
                owner = "owner1",
                level = GmLevelName.EARTH,
                attachedNodeId = null,
                b3trDonated = BigInteger.ZERO,
            )
        val eBurn =
            buildIndexedEvent(
                id = "e3",
                blockNumber = 5L,
                eventType = "GM_Burned",
                params = AbiEventParameters(mapOf("tokenId" to tokenId)),
            )

        every { repository.findByIdOrNull(tokenId) } returns existing

        service.processEvents(listOf(eBurn))

        verify(exactly = 1) {
            gmNftArchiveService.saveAll(match { it.size == 1 && it.first().id == tokenId })
        }
        verify(exactly = 1) { repository.deleteAllById(match { it.toList() == listOf(tokenId) }) }
        verify(exactly = 0) { repository.saveAll(any<Iterable<GmNft>>()) }
    }

    @Test
    fun `upgrade - updates level and donation, archives existing, version+1`() {
        val tokenId = "t1"
        val existing =
            GmNft(tokenId, 2, "b", 10, 100, "owner", GmLevelName.EARTH, null, BigInteger.TEN)
        val eUpgrade =
            buildIndexedEvent(
                eventType = "B3TR_GmUpgrade",
                blockNumber = 11,
                params =
                    AbiEventParameters(
                        mapOf(
                            "tokenId" to tokenId,
                            "newLevel" to "${GmLevelName.MOON.ordinal}",
                            "value" to "5",
                        )
                    ),
            )
        every { repository.findByIdOrNull(tokenId) } returns existing

        val saved = slot<Iterable<GmNft>>()
        val archived = slot<List<GmNft>>()
        every { repository.saveAll(capture(saved)) } answers { saved.captured }
        every { gmNftArchiveService.saveAll(capture(archived)) } just Runs

        service.processEvents(listOf(eUpgrade))

        assertEquals(1, archived.captured.size)
        assertEquals(1, saved.captured.count())
        val u = saved.captured.first()
        assertEquals(GmLevelName.MOON, u.level)
        assertEquals(BigInteger.valueOf(15), u.b3trDonated)
        assertEquals(existing.version + 1, u.version)
    }

    @Test
    fun `node attach - sets node id and level`() {
        val tokenId = "t1"
        val existing =
            GmNft(tokenId, 1, "b", 1, 1, "owner", GmLevelName.EARTH, null, BigInteger.ZERO)
        val e =
            buildIndexedEvent(
                eventType = "B3TR_GmNodeAttached",
                blockNumber = 2,
                params =
                    AbiEventParameters(
                        mapOf(
                            "tokenId" to tokenId,
                            "level" to "${GmLevelName.MOON.ordinal}",
                            "nodeTokenId" to "node-1",
                        )
                    ),
            )
        every { repository.findByIdOrNull(tokenId) } returns existing
        val saved = slot<Iterable<GmNft>>()
        every { repository.saveAll(capture(saved)) } answers { saved.captured }

        service.processEvents(listOf(e))

        val u = saved.captured.first()
        assertEquals("node-1", u.attachedNodeId)
        assertEquals(GmLevelName.MOON, u.level)
    }

    @Test
    fun `node detach - clears node id and updates level`() {
        val tokenId = "t1"
        val existing =
            GmNft(tokenId, 1, "b", 1, 1, "owner", GmLevelName.MARS, "node-x", BigInteger.ZERO)
        val e =
            buildIndexedEvent(
                eventType = "B3TR_GmNodeDetached",
                blockNumber = 2,
                params =
                    AbiEventParameters(
                        mapOf("tokenId" to tokenId, "level" to "${GmLevelName.MOON.ordinal}")
                    ),
            )
        every { repository.findByIdOrNull(tokenId) } returns existing
        val saved = slot<Iterable<GmNft>>()
        every { repository.saveAll(capture(saved)) } answers { saved.captured }

        service.processEvents(listOf(e))
        val u = saved.captured.first()
        assertNull(u.attachedNodeId)
        assertEquals(GmLevelName.MOON, u.level)
    }

    @Test
    fun `node level check - changed level updates`() {
        val tokenId = "t1"
        val existing =
            GmNft(tokenId, 1, "b", 1, 1, "owner", GmLevelName.EARTH, null, BigInteger.ZERO)
        val e =
            buildIndexedEvent(
                eventType = "B3TR_GmNodeLevel",
                blockNumber = 2,
                params =
                    AbiEventParameters(
                        mapOf("tokenId" to tokenId, "level" to "${GmLevelName.MOON.ordinal}")
                    ),
            )
        every { repository.findByIdOrNull(tokenId) } returns existing
        val saved = slot<Iterable<GmNft>>()
        val archived = slot<List<GmNft>>()
        every { repository.saveAll(capture(saved)) } answers { saved.captured }
        every { gmNftArchiveService.saveAll(capture(archived)) } just Runs

        service.processEvents(listOf(e))

        assertEquals(1, archived.captured.size)
        assertEquals(GmLevelName.MOON, saved.captured.first().level)
    }

    @Test
    fun `node level check - unchanged still version bumps`() {
        val tokenId = "t1"
        val existing =
            GmNft(tokenId, 5, "b", 1, 1, "owner", GmLevelName.MOON, null, BigInteger.ZERO)
        val e =
            buildIndexedEvent(
                eventType = "B3TR_GmNodeLevel",
                params =
                    AbiEventParameters(
                        mapOf("tokenId" to tokenId, "level" to "${GmLevelName.MOON.ordinal}")
                    ),
            )
        every { repository.findByIdOrNull(tokenId) } returns existing
        val saved = slot<Iterable<GmNft>>()
        every { repository.saveAll(capture(saved)) } answers { saved.captured }

        service.processEvents(listOf(e))

        assertEquals(existing.version + 1, saved.captured.first().version)
    }

    @Test
    fun `burn short-circuits subsequent events`() {
        val tokenId = "t1"
        val existing =
            GmNft(tokenId, 1, "b", 1, 1, "owner", GmLevelName.EARTH, null, BigInteger.ZERO)
        val burn =
            buildIndexedEvent(
                eventType = "GM_Burned",
                params = AbiEventParameters(mapOf("tokenId" to tokenId)),
            )
        val transferAfter =
            buildIndexedEvent(
                eventType = "B3TR_GmTransfer",
                params = AbiEventParameters(mapOf("tokenId" to tokenId, "to" to "new")),
            )
        every { repository.findByIdOrNull(tokenId) } returns existing

        service.processEvents(listOf(burn, transferAfter))

        verify(exactly = 1) { repository.deleteAllById(match { it.toList() == listOf(tokenId) }) }
        verify(exactly = 0) { repository.saveAll(any<Iterable<GmNft>>()) }
    }
}
