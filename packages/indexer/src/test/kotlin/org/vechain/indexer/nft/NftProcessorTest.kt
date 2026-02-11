package org.vechain.indexer.nft

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.fixtures.IndexedEventsFixtures.INDEXED_EVENTS_NFT_MINT

@ExtendWith(MockKExtension::class)
internal class NftProcessorTest {

    @MockK lateinit var nftRepository: NftRepository

    @MockK lateinit var archiveService: ArchiveService<IndexedNft>

    @MockK lateinit var nftService: NftService

    @MockK lateinit var checkpointService: CheckpointService

    private lateinit var processor: NftProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        processor =
            NftProcessor(
                nftService = nftService,
                nftArchiveService = archiveService,
                repository = nftRepository,
                checkpointService = checkpointService,
            )
    }

    @Test
    fun `process - empty data`() {
        val events = INDEXED_EVENTS_NFT_MINT

        every { nftService.getExisting(any()) } returns emptyList()
        coEvery { nftService.parseRecords(any(), emptyList()) } returns emptyList()
        every { nftService.save(any(), any()) } just Runs

        runBlocking {
            processor.process(
                IndexingResult.EventsOnly(
                    events.maxBy { it.blockNumber }.blockNumber,
                    events,
                    Status.SYNCING,
                )
            )
        }

        verify(exactly = 0) { nftService.save(any(), any()) }
    }

    @Test
    fun `process - update called if existing and updated records`() {
        val events = INDEXED_EVENTS_NFT_MINT

        val existing =
            listOf(
                IndexedNft(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 1,
                    owner = "0x0001",
                    contractAddress = "0x0002",
                    tokenId = "0",
                    txId = "0x0003",
                    blockId = "0x0004",
                    blockNumber = 1L,
                    blockTimestamp = 3,
                )
            )

        val updated =
            listOf(
                IndexedNft(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 2,
                    owner = "0x0005",
                    contractAddress = "0x0006",
                    tokenId = "0",
                    txId = "0x0007",
                    blockId = "0x0008",
                    blockNumber = 2L,
                    blockTimestamp = 4,
                )
            )

        every { nftService.getExisting(any()) } returns existing
        coEvery { nftService.parseRecords(any(), existing) } returns updated
        every { nftService.save(updated, existing) } just Runs

        runBlocking {
            processor.process(
                IndexingResult.EventsOnly(
                    events.maxBy { it.blockNumber }.blockNumber,
                    events,
                    Status.SYNCING,
                )
            )
        }

        verify(exactly = 1) { nftService.save(updated, existing) }
    }

    @Test
    fun `processLogs - update called if no existing records`() {
        val events = INDEXED_EVENTS_NFT_MINT

        val existing = emptyList<IndexedNft>()

        val updated =
            listOf(
                IndexedNft(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 2,
                    owner = "0x0005",
                    contractAddress = "0x0006",
                    tokenId = "0",
                    txId = "0x0007",
                    blockId = "0x0008",
                    blockNumber = 2L,
                    blockTimestamp = 4,
                )
            )

        every { nftService.getExisting(any()) } returns existing
        coEvery { nftService.parseRecords(any(), existing) } returns updated
        every { nftService.save(updated, existing) } just Runs

        runBlocking {
            processor.process(
                IndexingResult.EventsOnly(
                    events.maxBy { it.blockNumber }.blockNumber,
                    events,
                    Status.SYNCING,
                )
            )
        }

        verify(exactly = 1) { nftService.save(updated, existing) }
    }

    @Test
    fun `processLogs - update called is only existing records and not updated`() {
        val events = INDEXED_EVENTS_NFT_MINT

        val existing =
            listOf(
                IndexedNft(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 1,
                    owner = "0x0001",
                    contractAddress = "0x0002",
                    tokenId = "0",
                    txId = "0x0003",
                    blockId = "0x0004",
                    blockNumber = 1L,
                    blockTimestamp = 3,
                )
            )

        val updated = emptyList<IndexedNft>()

        every { nftService.getExisting(any()) } returns existing
        coEvery { nftService.parseRecords(any(), existing) } returns updated
        every { nftService.save(updated, existing) } just Runs

        runBlocking {
            processor.process(
                IndexingResult.EventsOnly(
                    events.maxBy { it.blockNumber }.blockNumber,
                    events,
                    Status.SYNCING,
                )
            )
        }

        verify(exactly = 1) { nftService.save(updated, existing) }
    }
}
