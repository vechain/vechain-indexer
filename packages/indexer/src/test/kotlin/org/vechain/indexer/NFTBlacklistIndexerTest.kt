package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_BLACKLIST
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_NFT_MINT_2
import org.vechain.indexer.model.NFTBlacklist
import org.vechain.indexer.model.NFTBlacklistArchive
import org.vechain.indexer.repository.NFTBlacklistRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.NFTBlacklistService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.utils.FileUtils

@ExtendWith(MockKExtension::class)
internal class NFTBlacklistIndexerTest {

    companion object {
        private lateinit var abiManager: AbiManager

        @JvmStatic
        @BeforeAll
        fun setupAbiManager() {
            abiManager = AbiManager()
            abiManager.loadAbis(FileUtils.loadFileStreams("test-abis"))
        }
    }

    // Mock dependencies
    @MockK lateinit var nftBlacklistService: NFTBlacklistService
    @MockK
    lateinit var nftBlacklistArchiveService: ArchiveService<NFTBlacklist, NFTBlacklistArchive>
    @MockK lateinit var nftBlacklistRepository: NFTBlacklistRepository

    private lateinit var indexer: NFTBlacklistIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        indexer =
            NFTBlacklistIndexer(
                nftBlacklistService = nftBlacklistService,
                nftBlacklistArchiveService = nftBlacklistArchiveService,
                repository = nftBlacklistRepository,
                thorClient = DefaultThorClient("http://localhost:8669"),
                abiManager = abiManager,
                startBlock = 0L,
                blacklistContract = "0x14091cc9ae249f26eaf41a5a21207931162a2826",
                prunerRemovalChunkSize = 10000,
                syncLogInterval = 1000L,
                syncBlockBatchSize = 1000L,
            )
    }

    @Test
    fun `processLogs - empty data`() {
        val events = emptyList<EventLog>()

        indexer.processLogs(events, emptyList())

        // Verify no records updated
        verify(exactly = 0) { nftBlacklistService.getExisting(any()) }
        verify(exactly = 0) { nftBlacklistService.parseRecords(any(), emptyList()) }
        verify(exactly = 0) { nftBlacklistService.update(any(), any()) }
    }

    @Test
    fun `processLogs - irrelevant data`() {
        val logs = LOGS_NFT_MINT_2

        indexer.processLogs(logs, emptyList())

        // Verify no records updated
        verify(exactly = 0) { nftBlacklistService.getExisting(any()) }
        verify(exactly = 0) { nftBlacklistService.parseRecords(any(), emptyList()) }
        verify(exactly = 0) { nftBlacklistService.update(any(), any()) }
    }

    @Test
    fun `processLogs - valid blacklist events`() {
        val logs = LOGS_BLACKLIST

        val existing = emptyList<NFTBlacklist>()
        val parsedRecords =
            listOf(
                NFTBlacklist(
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
        every { nftBlacklistService.update(parsedRecords, existing) } just Runs

        indexer.processLogs(logs, emptyList())

        verify(exactly = 1) { nftBlacklistService.getExisting(any()) }
        verify(exactly = 1) { nftBlacklistService.parseRecords(any(), existing) }
        verify(exactly = 1) { nftBlacklistService.update(parsedRecords, existing) }
    }

    @Test
    fun `processLogs - valid events on different contract`() {
        val logs = LOGS_BLACKLIST

        val indexerDiffContract =
            NFTBlacklistIndexer(
                nftBlacklistService = nftBlacklistService,
                nftBlacklistArchiveService = nftBlacklistArchiveService,
                repository = nftBlacklistRepository,
                thorClient = DefaultThorClient("http://localhost:8669"),
                abiManager = abiManager,
                startBlock = 0L,
                blacklistContract = "0xdifferentContract",
                prunerRemovalChunkSize = 10000,
                syncLogInterval = 1000L,
                syncBlockBatchSize = 1000L,
            )

        indexerDiffContract.processLogs(logs, emptyList())

        // Verify no records updated
        verify(exactly = 0) { nftBlacklistService.getExisting(any()) }
        verify(exactly = 0) { nftBlacklistService.parseRecords(any(), any()) }
        verify(exactly = 0) { nftBlacklistService.update(any(), any()) }
    }
}
