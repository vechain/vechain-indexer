package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.fixtures.LogsFixtures.LOGS_VIP180_CONTRACTS
import org.vechain.indexer.model.ContractArchive
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.utils.FileUtils

@ExtendWith(MockKExtension::class)
internal class ContractIndexerTest {
    @MockK lateinit var contractRepository: ContractRepository

    @MockK lateinit var archiveService: ArchiveService<IndexedContract, ContractArchive>

    @MockK lateinit var contractService: ContractService

    private lateinit var indexer: ContractIndexer

    @BeforeEach
    fun setUp() {
        val abiFileStreams = FileUtils.loadFileStreams("test-abis")
        val abiManager = AbiManager()
        abiManager.loadAbis(abiFileStreams)

        MockKAnnotations.init(this)
        indexer =
            ContractIndexer(
                contractService,
                archiveService,
                contractRepository,
                DefaultThorClient("http://localhost:8669"),
                abiManager,
                1L,
                1000,
                1000L,
                1000L,
            )
    }

    @Test
    fun `processLogs - empty data`() {
        val logs = LOGS_VIP180_CONTRACTS

        every { contractService.getExisting(any()) } returns emptyList()
        every { contractService.parseContracts(any(), emptyList()) } returns emptyList()

        indexer.processLogs(logs, emptyList())

        verify(exactly = 0) { contractService.update(any(), any()) }
    }

    @Test
    fun `processLogs - update called if existing and updated records`() {
        val logs = LOGS_VIP180_CONTRACTS

        val existing =
            listOf(
                IndexedContract(
                    address = "0x000000",
                    version = 1,
                    blockId = "0x000001",
                    blockNumber = 2L,
                    blockTimestamp = 232,
                    txId = "0x000002",
                    creator = "0x000003",
                    master = "0x000004",
                    rawData = "raw data",
                    isVip180 = true,
                    isVip181 = false,
                    isVip210 = false,
                    isErc20 = false,
                    isErc721 = false,
                    isErc1155 = false,
                    previousMasters = mutableSetOf(),
                ),
            )

        every { contractService.getExisting(any()) } returns existing
        every { contractService.parseContracts(any(), existing) } returns existing
        every { contractService.update(any(), any()) } just Runs

        indexer.processLogs(logs, emptyList())

        verify(exactly = 1) { contractService.update(existing, existing) }
    }

    @Test
    fun `processLogs - update called if no existing records`() {
        val logs = LOGS_VIP180_CONTRACTS

        val updated =
            listOf(
                IndexedContract(
                    address = "0x000000",
                    version = 1,
                    blockId = "0x000001",
                    blockNumber = 2L,
                    blockTimestamp = 232,
                    txId = "0x000002",
                    creator = "0x000003",
                    master = "0x000004",
                    rawData = "raw data",
                    isVip180 = true,
                    isVip181 = false,
                    isVip210 = false,
                    isErc20 = false,
                    isErc721 = false,
                    isErc1155 = false,
                    previousMasters = mutableSetOf(),
                ),
            )

        every { contractService.getExisting(any()) } returns emptyList()
        every { contractService.parseContracts(any(), emptyList()) } returns updated
        every { contractService.update(any(), any()) } just Runs

        indexer.processLogs(logs, emptyList())

        verify(exactly = 1) { contractService.update(updated, emptyList()) }
    }

    @Test
    fun `processLogs - update called is only existing records and not updated`() {
        val logs = LOGS_VIP180_CONTRACTS

        val existing =
            listOf(
                IndexedContract(
                    address = "0x000000",
                    version = 1,
                    blockId = "0x000001",
                    blockNumber = 2L,
                    blockTimestamp = 232,
                    txId = "0x000002",
                    creator = "0x000003",
                    master = "0x000004",
                    rawData = "raw data",
                    isVip180 = true,
                    isVip181 = false,
                    isVip210 = false,
                    isErc20 = false,
                    isErc721 = false,
                    isErc1155 = false,
                    previousMasters = mutableSetOf(),
                ),
            )

        every { contractService.getExisting(any()) } returns existing
        every { contractService.parseContracts(any(), existing) } returns emptyList()
        every { contractService.update(any(), any()) } just Runs

        indexer.processLogs(logs, emptyList())

        verify(exactly = 1) { contractService.update(emptyList(), existing) }
    }
}
