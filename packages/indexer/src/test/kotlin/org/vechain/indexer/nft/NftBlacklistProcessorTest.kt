package org.vechain.indexer.nft

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST

@ExtendWith(MockKExtension::class)
internal class NftBlacklistProcessorTest {

    // Mock dependencies
    @MockK lateinit var nftBlacklistService: NftBlacklistService
    @MockK
    lateinit var nftBlacklistArchiveService: ArchiveService<NftBlacklist, NftBlacklistArchive>
    @MockK lateinit var nftBlacklistRepository: NftBlacklistRepository

    private lateinit var processor: NftBlacklistProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        processor =
            NftBlacklistProcessor(
                nftBlacklistService = nftBlacklistService,
                nftBlacklistArchiveService = nftBlacklistArchiveService,
                repository = nftBlacklistRepository,
            )
    }

    @Test
    fun `processLogs - empty data`() {
        processor.process(IndexingResult.EventsOnly(100, emptyList()))

        // Verify no records updated
        verify(exactly = 0) { nftBlacklistService.getExisting(any()) }
        verify(exactly = 0) { nftBlacklistService.parseRecords(any(), emptyList()) }
        verify(exactly = 0) { nftBlacklistService.save(any(), any()) }
    }

    @Test
    fun `processLogs - valid blacklist events`() {
        val events = INDEXED_EVENTS_BLACKLIST

        val existing = emptyList<NftBlacklist>()
        val parsedRecords =
            listOf(
                NftBlacklist(
                    version = 1,
                    contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
                    isBlacklisted = true,
                    blockId = "blockId",
                    blockNumber = 1L,
                    blockTimestamp = 1L,
                )
            )

        every { nftBlacklistService.getExisting(any()) } returns existing
        every { nftBlacklistService.parseRecords(any(), existing) } returns parsedRecords
        every { nftBlacklistService.save(parsedRecords, existing) } just Runs

        processor.process(
            IndexingResult.EventsOnly(events.maxBy { it.blockNumber }.blockNumber, events)
        )

        verify(exactly = 1) { nftBlacklistService.getExisting(any()) }
        verify(exactly = 1) { nftBlacklistService.parseRecords(any(), existing) }
        verify(exactly = 1) { nftBlacklistService.save(parsedRecords, existing) }
    }
}
