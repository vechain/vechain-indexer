package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_NFT_MINT_2
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.NFTBlacklistService
import org.vechain.indexer.service.NFTService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.utils.FileUtils

@ExtendWith(MockKExtension::class)
internal class NFTEventIndexerTest {

    companion object {
        private lateinit var abiManager: AbiManager

        @JvmStatic
        @BeforeAll
        fun setupAbiManager() {
            abiManager = AbiManager()
            abiManager.loadAbis(FileUtils.loadFileStreams("test-abis"))
        }
    }

    @MockK lateinit var nftRepository: NFTRepository

    @MockK lateinit var archiveService: ArchiveService<IndexedNFT, NFTArchive>

    @MockK lateinit var nftService: NFTService

    @MockK lateinit var nftBlacklistService: NFTBlacklistService

    private lateinit var indexer: TestableNFTEventIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        indexer =
            TestableNFTEventIndexer(
                nftService = nftService,
                nftArchiveService = archiveService,
                nftBlacklistService = nftBlacklistService,
                thorClient = DefaultThorClient("http://localhost:8669"),
                nftRepository = nftRepository,
                abiManager = abiManager,
                startBlock = 0L,
                prunerRemovalChunkSize = 10000,
                blacklistInterval = 10,
                syncLogInterval = 1000L,
                syncBlockBatchSize = 1000L,
            )
    }

    @Test
    fun `processLogs - empty data`() {
        val logs = LOGS_NFT_MINT_2

        every { nftService.getExisting(any()) } returns emptyList()
        every { nftService.parseRecords(any(), emptyList()) } returns emptyList()
        every { nftService.update(any(), any()) } just Runs

        indexer.processLogs(logs, emptyList())

        verify(exactly = 0) { nftService.update(any(), any()) }
    }

    @Test
    fun `processLogs - update called if existing and updated records`() {
        val logs = LOGS_NFT_MINT_2

        val existing =
            listOf(
                IndexedNFT(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 1,
                    owner = "0x0001",
                    contractAddress = "0x0002",
                    tokenId = "0",
                    txId = "0x0003",
                    blockId = "0x0004",
                    blockNumber = 1L,
                    blockTimestamp = 3,
                ),
            )

        val updated =
            listOf(
                IndexedNFT(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 2,
                    owner = "0x0005",
                    contractAddress = "0x0006",
                    tokenId = "0",
                    txId = "0x0007",
                    blockId = "0x0008",
                    blockNumber = 2L,
                    blockTimestamp = 4,
                ),
            )

        every { nftService.getExisting(any()) } returns existing
        every { nftService.parseRecords(any(), existing) } returns updated
        every { nftService.update(updated, existing) } just Runs

        indexer.processLogs(logs, emptyList())

        verify(exactly = 1) { nftService.update(updated, existing) }
    }

    @Test
    fun `processLogs - update called if no existing records`() {
        val logs = LOGS_NFT_MINT_2

        val existing = emptyList<IndexedNFT>()

        val updated =
            listOf(
                IndexedNFT(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 2,
                    owner = "0x0005",
                    contractAddress = "0x0006",
                    tokenId = "0",
                    txId = "0x0007",
                    blockId = "0x0008",
                    blockNumber = 2L,
                    blockTimestamp = 4,
                ),
            )

        every { nftService.getExisting(any()) } returns existing
        every { nftService.parseRecords(any(), existing) } returns updated
        every { nftService.update(updated, existing) } just Runs

        indexer.processLogs(logs, emptyList())

        verify(exactly = 1) { nftService.update(updated, existing) }
    }

    @Test
    fun `processLogs - update called is only existing records and not updated`() {
        val logs = LOGS_NFT_MINT_2

        val existing =
            listOf(
                IndexedNFT(
                    id = "93bed94c547a207d9a8ff1daee175feded00304b",
                    version = 1,
                    owner = "0x0001",
                    contractAddress = "0x0002",
                    tokenId = "0",
                    txId = "0x0003",
                    blockId = "0x0004",
                    blockNumber = 1L,
                    blockTimestamp = 3,
                ),
            )

        val updated = emptyList<IndexedNFT>()

        every { nftService.getExisting(any()) } returns existing
        every { nftService.parseRecords(any(), existing) } returns updated
        every { nftService.update(updated, existing) } just Runs

        indexer.processLogs(logs, emptyList())

        verify(exactly = 1) { nftService.update(updated, existing) }
    }

    // Ensure that the syncBlacklistedNFTs method is called at the correct interval
    @Test
    fun `processLogs - syncBlacklistedNFTs called at interval`() {
        val logs = emptyList<EventLog>()

        every { nftBlacklistService.syncBlacklistedNFTs() } just Runs

        indexer.setStatusOverride(Status.FULLY_SYNCED)
        indexer.setCurrentBlockNumberOverride(10L) // Multiple of the blacklist interval

        indexer.processLogs(logs, emptyList())

        verify(exactly = 1) { nftBlacklistService.syncBlacklistedNFTs() }
    }

    // Ensure that the syncBlacklistedNFTs method is not called if the interval is not met
    @Test
    fun `processLogs - syncBlacklistedNFTs not called if interval not met`() {
        val logs = emptyList<EventLog>()

        indexer.setStatusOverride(Status.FULLY_SYNCED)
        indexer.setCurrentBlockNumberOverride(1L) // Not a multiple of the blacklist interval

        indexer.processLogs(logs, emptyList())

        verify(exactly = 0) { nftBlacklistService.syncBlacklistedNFTs() }
    }

    @Test
    fun `processLogs - syncBlacklistedNFTs not called if status is not FULLY_SYNCED`() {
        val logs = emptyList<EventLog>()

        indexer.setStatusOverride(Status.SYNCING)
        indexer.setCurrentBlockNumberOverride(10L) // Multiple of the blacklist interval

        indexer.processLogs(logs, emptyList())

        verify(exactly = 0) { nftBlacklistService.syncBlacklistedNFTs() }
    }
}

class TestableNFTEventIndexer(
    nftService: NFTService,
    nftArchiveService: ArchiveService<IndexedNFT, NFTArchive>,
    nftBlacklistService: NFTBlacklistService,
    thorClient: DefaultThorClient,
    nftRepository: NFTRepository,
    abiManager: AbiManager,
    startBlock: Long,
    prunerRemovalChunkSize: Int,
    blacklistInterval: Int,
    syncLogInterval: Long,
    syncBlockBatchSize: Long,
) :
    NFTEventIndexer(
        nftService,
        nftArchiveService,
        nftBlacklistService,
        thorClient,
        nftRepository,
        abiManager,
        startBlock,
        prunerRemovalChunkSize,
        blacklistInterval,
        syncLogInterval,
        syncBlockBatchSize,
    ) {
    fun setStatusOverride(s: Status) {
        this.status = s
    }

    fun setCurrentBlockNumberOverride(n: Long) {
        this.currentBlockNumber = n
    }
}
