package org.vechain.indexer.service

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST_DUPLICATE
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST_MISSING_ISBLACKLISTED_PARAM
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST_MISSING_NFT_PARAM
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_DEBLACKLIST
import org.vechain.indexer.model.NFTBlacklist
import org.vechain.indexer.model.NFTBlacklistArchive
import org.vechain.indexer.repository.NFTBlacklistRepository
import strikt.api.expect
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class NFTBlacklistServiceTest {

    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var repository: NFTBlacklistRepository
    @MockK
    lateinit var nftBlacklistArchiveService: ArchiveService<NFTBlacklist, NFTBlacklistArchive>

    private lateinit var nftBlacklistService: NFTBlacklistService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        nftBlacklistService =
            NFTBlacklistService(
                mongoTemplate = mongoTemplate,
                repository = repository,
                nftBlacklistArchiveService = nftBlacklistArchiveService,
            )
    }

    // Update

    @Test
    fun `update - should save updated and existing records`() {
        val updated = listOf(NFTBlacklist("contract1", 1, true, "block1", 1, 1))
        val existing = listOf(NFTBlacklist("contract2", 1, false, "block2", 2, 2))

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.update(updated, existing)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 1) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll if updated is empty`() {
        val updated = emptyList<NFTBlacklist>()
        val existing = listOf(NFTBlacklist("contract2", 1, false, "block2", 2, 2))

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.update(updated, existing)

        verify(exactly = 0) { repository.saveAll(updated) }
        verify(exactly = 1) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll if existing is empty`() {
        val updated = listOf(NFTBlacklist("contract1", 1, true, "block1", 1, 1))
        val existing = emptyList<NFTBlacklist>()

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.update(updated, existing)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 0) { nftBlacklistArchiveService.saveAll(existing) }
    }

    @Test
    fun `update - shouldn't call saveAll with empty lists`() {
        val updated = emptyList<NFTBlacklist>()
        val existing = emptyList<NFTBlacklist>()

        every { repository.saveAll(updated) } returns updated
        every { nftBlacklistArchiveService.saveAll(existing) } just Runs

        nftBlacklistService.update(updated, existing)

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
            that(result[0].contractAddress).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(result[0].isBlacklisted).isEqualTo(true)
            that(result[0].blockId)
                .isEqualTo("0x0144302e0a842eedb085f5cb0eaa722f65048ded9614e4c6afec4d6c941c6484")
            that(result[0].blockNumber).isEqualTo(21245998L)
            that(result[0].blockTimestamp).isEqualTo(1742989050L)

            that(result[1].version).isEqualTo(1)
            that(result[1].contractAddress).isEqualTo("0x884a36ca0b582c54255aac68a2664cd0ca8c592d")
            that(result[1].isBlacklisted).isEqualTo(true)
            that(result[1].blockId)
                .isEqualTo("0x0144302f01d216b50110ad8d9ff03d37f3559a03c559a9cc872f6ba8b9594f56")
            that(result[1].blockNumber).isEqualTo(21245999L)
            that(result[1].blockTimestamp).isEqualTo(1742989060L)
        }
    }

    @Test
    fun `parseRecords - should parse valid de-blacklist record`() {
        val events = INDEXED_EVENTS_DEBLACKLIST

        val result = nftBlacklistService.parseRecords(events, emptyList())

        // Verify the result
        expect {
            that(result.size).isEqualTo(1)
            that(result[0].version).isEqualTo(1)
            that(result[0].contractAddress).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(result[0].isBlacklisted).isEqualTo(false)
            that(result[0].blockId)
                .isEqualTo("0x0144302e0a842eedb085f5cb0eaa722f65048ded9614e4c6afec4d6c941c6483")
            that(result[0].blockNumber).isEqualTo(21246000L)
            that(result[0].blockTimestamp).isEqualTo(1742989070L)
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
                NFTBlacklist(
                    version = 1,
                    contractAddress = "0x4d2b488dd3638459f75040bd7bdf77b17cef7712",
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
            that(result[0].contractAddress).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(result[0].isBlacklisted).isEqualTo(true)
            that(result[0].blockId)
                .isEqualTo("0x0144302e0a842eedb085f5cb0eaa722f65048ded9614e4c6afec4d6c941c6484")
            that(result[0].blockNumber).isEqualTo(21245998L)
            that(result[0].blockTimestamp).isEqualTo(1742989050L)

            that(result[1].version).isEqualTo(1)
            that(result[1].contractAddress).isEqualTo("0x884a36ca0b582c54255aac68a2664cd0ca8c592d")
            that(result[1].isBlacklisted).isEqualTo(true)
            that(result[1].blockId)
                .isEqualTo("0x0144302f01d216b50110ad8d9ff03d37f3559a03c559a9cc872f6ba8b9594f56")
            that(result[1].blockNumber).isEqualTo(21245999L)
            that(result[1].blockTimestamp).isEqualTo(1742989060L)
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
            that(result[0].contractAddress).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(result[0].isBlacklisted).isEqualTo(false)
            that(result[0].blockId)
                .isEqualTo("0x0144302e0a842eedb085f5cb0eaa722f65048ded9614e4c6afec4d6c941c6485")
            that(result[0].blockNumber).isEqualTo(21246000L)
            that(result[0].blockTimestamp).isEqualTo(1742989070L)
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
            that(result[0].contractAddress).isEqualTo("0x4d2b488dd3638459f75040bd7bdf77b17cef7712")
            that(result[0].isBlacklisted).isEqualTo(false)
            that(result[0].blockId)
                .isEqualTo("0x0144302e0a842eedb085f5cb0eaa722f65048ded9614e4c6afec4d6c941c6485")
            that(result[0].blockNumber).isEqualTo(21246000L)
            that(result[0].blockTimestamp).isEqualTo(1742989070L)
        }
    }

    @Test
    fun `parseRecords - should throw an exception if the nft param is not available on the indexed event`() {
        val events = INDEXED_EVENTS_BLACKLIST_MISSING_NFT_PARAM

        // Check that an appropriate exception is thrown
        val exception =
            assertThrows<IllegalArgumentException> {
                nftBlacklistService.parseRecords(events, emptyList())
            }

        expect {
            that(exception.message)
                .isEqualTo(
                    "Missing 'nft' param in event: 0x349ce728b59250ad7c5f2b77c03fa9bfd4a0e4a4639b8f658f7394f0d92417c7-0-0--832607567"
                )
        }
    }

    @Test
    fun `parseRecords - should throw an exception if the isBlacklisted param is not available on the indexed event`() {
        val events = INDEXED_EVENTS_BLACKLIST_MISSING_ISBLACKLISTED_PARAM

        // Check that an appropriate exception is thrown
        val exception =
            assertThrows<IllegalArgumentException> {
                nftBlacklistService.parseRecords(events, emptyList())
            }

        expect {
            that(exception.message)
                .isEqualTo(
                    "Missing 'isBlacklisted' param in event: 0x349ce728b59250ad7c5f2b77c03fa9bfd4a0e4a4639b8f658f7394f0d92417c7-0-0--832607566"
                )
        }
    }

    // getExisting
    @Test
    fun `getExisting - should return existing records`() {
        val events = INDEXED_EVENTS_BLACKLIST

        val existing =
            listOf(
                NFTBlacklist(
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
                "0x4d2b488dd3638459f75040bd7bdf77b17cef7712",
                "0x884a36ca0b582c54255aac68a2664cd0ca8c592d",
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

    @Test
    fun `getExisting - should fail with an exception if the nft param is not available on the indexed event`() {
        val events = INDEXED_EVENTS_BLACKLIST_MISSING_NFT_PARAM

        // Check that an appropriate exception is thrown

        val exception =
            assertThrows<IllegalArgumentException> { nftBlacklistService.getExisting(events) }

        expect {
            that(exception.message)
                .isEqualTo(
                    "Missing 'nft' param in event: 0x349ce728b59250ad7c5f2b77c03fa9bfd4a0e4a4639b8f658f7394f0d92417c7-0-0--832607567"
                )
        }
    }
}
