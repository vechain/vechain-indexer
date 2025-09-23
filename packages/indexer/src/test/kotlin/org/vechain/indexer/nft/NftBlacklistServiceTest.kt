package org.vechain.indexer.nft

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST_DUPLICATE
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_WHITELIST
import org.vechain.indexer.utils.ParamUtils.getAsString
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class NftBlacklistServiceTest {

    @MockK lateinit var repository: NftBlacklistRepository
    @MockK
    lateinit var nftBlacklistArchiveService: ArchiveService<NftBlacklist, NftBlacklistArchive>

    @MockK lateinit var pruner: Pruner

    private lateinit var nftBlacklistService: NftBlacklistService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        nftBlacklistService =
            NftBlacklistService(
                repository = repository,
                nftBlacklistArchiveService = nftBlacklistArchiveService,
                nftBlacklistPruner = pruner,
            )
    }

    // Update

    @Test
    fun `update - should save updated and existing records`() {
        val updated = listOf(NftBlacklist("contract1", 1, true, "block1", 1, 1))
        val existing = listOf(NftBlacklist("contract2", 1, false, "block2", 2, 2))

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.save(updated, existing)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 1) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll if updated is empty`() {
        val updated = emptyList<NftBlacklist>()
        val existing = listOf(NftBlacklist("contract2", 1, false, "block2", 2, 2))

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.save(updated, existing)

        verify(exactly = 0) { repository.saveAll(updated) }
        verify(exactly = 1) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll if existing is empty`() {
        val updated = listOf(NftBlacklist("contract1", 1, true, "block1", 1, 1))
        val existing = emptyList<NftBlacklist>()

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.save(updated, existing)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 0) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll with empty lists`() {
        val updated = emptyList<NftBlacklist>()
        val existing = emptyList<NftBlacklist>()

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.save(updated, existing)

        verify(exactly = 0) { repository.saveAll(updated) }
        verify(exactly = 0) { nftBlacklistArchiveService.saveAll(existing) }
    }

    // parseRecords

    @Test
    fun `parseRecords - should parse valid blacklist records`() {
        val events = INDEXED_EVENTS_BLACKLIST

        val result = nftBlacklistService.parseRecords(events, emptyList())

        // Verify the result
        expect {
            that(result.size).isEqualTo(2)
            that(result[0].version).isEqualTo(1)
            that(result[0].contractAddress).isEqualTo("0xf416bc92ffab1704bc247d620322fa95a178d496")
            that(result[0].isBlacklisted).isEqualTo(true)
            that(result[0].blockId)
                .isEqualTo("0x014c41463390a4defcdf60c6f652d8b31b21fc0f971f947486f5c5cc52ea7857")
            that(result[0].blockNumber).isEqualTo(21774662L)
            that(result[0].blockTimestamp).isEqualTo(1748275840)

            that(result[1].version).isEqualTo(1)
            that(result[1].contractAddress).isEqualTo("0x4d4a0fcda8963879e1fbacfb25a179111b8e4ae0")
            that(result[1].isBlacklisted).isEqualTo(true)
            that(result[1].blockId)
                .isEqualTo("0x014c41463390a4defcdf60c6f652d8b31b21fc0f971f947486f5c5cc52ea7857")
            that(result[1].blockNumber).isEqualTo(21774662L)
            that(result[1].blockTimestamp).isEqualTo(1748275840)
        }
    }

    @Test
    fun `parseRecords - should parse valid de-blacklist record`() {
        val events = INDEXED_EVENTS_WHITELIST

        val result = nftBlacklistService.parseRecords(events, emptyList())

        // Verify the result
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].version).isEqualTo(1)
            that(result[0].contractAddress).isEqualTo("0x9d51a33a211e77fd3621cbf135d543cd3bb7490a")
            that(result[0].isBlacklisted).isEqualTo(false)
            that(result[0].blockId)
                .isEqualTo("0x014c40db5f6d8cda7dc38381b056c0d8f348553d072708e9101d21d2c8c972f4")
            that(result[0].blockNumber).isEqualTo(21774555L)
            that(result[0].blockTimestamp).isEqualTo(1748274770L)
        }
    }

    @Test
    fun `parseRecords - should handle empty list`() {
        val events = emptyList<IndexedEvent>()

        val result = nftBlacklistService.parseRecords(events, emptyList())

        // Verify the result
        expect { that(result.size).isEqualTo(0) }
    }

    @Test
    fun `parseRecords - should update an existing record`() {
        val events = INDEXED_EVENTS_BLACKLIST

        val existing =
            listOf(
                NftBlacklist(
                    version = 1,
                    contractAddress = "0xf416bc92ffab1704bc247d620322fa95a178d496",
                    isBlacklisted = false,
                    blockId = "0xdead",
                    blockNumber = 8L,
                    blockTimestamp = 1L,
                )
            )

        val result = nftBlacklistService.parseRecords(events, existing)

        // Verify the result
        expect {
            that(result.size).isEqualTo(2)
            that(result[0].version).isEqualTo(2)
            that(result[0].contractAddress).isEqualTo("0xf416bc92ffab1704bc247d620322fa95a178d496")
            that(result[0].isBlacklisted).isEqualTo(true)
            that(result[0].blockId)
                .isEqualTo("0x014c41463390a4defcdf60c6f652d8b31b21fc0f971f947486f5c5cc52ea7857")
            that(result[0].blockNumber).isEqualTo(21774662L)
            that(result[0].blockTimestamp).isEqualTo(1748275840L)

            that(result[1].version).isEqualTo(1)
            that(result[1].contractAddress).isEqualTo("0x4d4a0fcda8963879e1fbacfb25a179111b8e4ae0")
            that(result[1].isBlacklisted).isEqualTo(true)
            that(result[1].blockId)
                .isEqualTo("0x014c41463390a4defcdf60c6f652d8b31b21fc0f971f947486f5c5cc52ea7857")
            that(result[1].blockNumber).isEqualTo(21774662L)
            that(result[1].blockTimestamp).isEqualTo(1748275840L)
        }
    }

    @Test
    fun `parseRecords - duplicate records should use the latest one`() {
        val events = INDEXED_EVENTS_BLACKLIST_DUPLICATE

        val result = nftBlacklistService.parseRecords(events, emptyList())

        // Verify the result
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].version).isEqualTo(1)
            that(result[0].contractAddress).isEqualTo("0x9d51a33a211e77fd3621cbf135d543cd3bb7490a")
            that(result[0].isBlacklisted).isEqualTo(false)
            that(result[0].blockId)
                .isEqualTo("0x014c40db5f6d8cda7dc38381b056c0d8f348553d072708e9101d21d2c8c972f4")
            that(result[0].blockNumber).isEqualTo(21774999L)
            that(result[0].blockTimestamp).isEqualTo(1748299999L)
        }
    }

    @Test
    fun `parseRecords - duplicate records should use the latest one regardless of list ordering`() {
        val events = INDEXED_EVENTS_BLACKLIST_DUPLICATE.reversed()

        val result = nftBlacklistService.parseRecords(events, emptyList())

        // Verify the result
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].version).isEqualTo(1)
            that(result[0].contractAddress).isEqualTo("0x9d51a33a211e77fd3621cbf135d543cd3bb7490a")
            that(result[0].isBlacklisted).isEqualTo(false)
            that(result[0].blockId)
                .isEqualTo("0x014c40db5f6d8cda7dc38381b056c0d8f348553d072708e9101d21d2c8c972f4")
            that(result[0].blockNumber).isEqualTo(21774999L)
            that(result[0].blockTimestamp).isEqualTo(1748299999L)
        }
    }

    @Test
    fun `parseRecords - should throw on unexpected eventType`() {
        val event =
            mockk<IndexedEvent> {
                every { id } returns "event123"
                every { eventType } returns "UnknownEvent"
                every { params.getAsString("nft") } returns "0x123"
                every { blockId } returns "0xblock"
                every { blockNumber } returns 1L
                every { blockTimestamp } returns 1000L
            }
        val events = listOf(event)

        expect {
            try {
                nftBlacklistService.parseRecords(events, emptyList())
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                that(
                    e.message?.contains("Unexpected eventType")
                        ?: fail { "Unexpected eventType not found" }
                )
            }
        }
    }

    // getExisting
    @Test
    fun `getExisting - should return existing records`() {
        val events = INDEXED_EVENTS_BLACKLIST

        val existing =
            listOf(
                NftBlacklist(
                    version = 1,
                    contractAddress = "0x4d2b488dd3638459f75040bd7bdf77b17cef7712",
                    isBlacklisted = true,
                    blockId = "0xdead",
                    blockNumber = 8L,
                    blockTimestamp = 1L,
                )
            )

        val contractAddresses =
            listOf(
                "0xf416bc92ffab1704bc247d620322fa95a178d496",
                "0x4d4a0fcda8963879e1fbacfb25a179111b8e4ae0",
            )

        every { repository.findAllById(contractAddresses) } returns existing

        val result = nftBlacklistService.getExisting(events)

        verify(exactly = 1) { repository.findAllById(contractAddresses) }

        // Verify the result
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].version).isEqualTo(1)
            that(result[0].contractAddress).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(result[0].isBlacklisted).isEqualTo(true)
            that(result[0].blockId).isEqualTo("0xdead")
            that(result[0].blockNumber).isEqualTo(8L)
            that(result[0].blockTimestamp).isEqualTo(1L)
        }
    }
}
