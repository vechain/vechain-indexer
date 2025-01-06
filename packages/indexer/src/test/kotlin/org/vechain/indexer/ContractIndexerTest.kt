package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_VIP180_CONTRACTS
import org.vechain.indexer.model.ContractArchive
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.thor.client.DefaultThorClient

@ExtendWith(MockKExtension::class)
internal class ContractIndexerTest {

    @MockK lateinit var contractRepository: ContractRepository

    @MockK lateinit var archiveService: ArchiveService<IndexedContract, ContractArchive>

    @MockK lateinit var contractService: ContractService

    private lateinit var indexer: ContractIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        indexer =
            ContractIndexer(
                contractService,
                archiveService,
                contractRepository,
                DefaultThorClient("http://localhost:8669"),
                1L,
                1000,
                1000L,
            )
    }

    @Test
    fun `processBlock - empty data`() {
        val block = BLOCK_VIP180_CONTRACTS

        every { contractService.getExisting(any()) } returns emptyList()
        every { contractService.parseContracts(any(), any(), emptyList()) } returns emptyList()

        indexer.processBlock(block)

        verify(exactly = 0) { contractService.update(any(), any()) }
    }

    @Test
    fun `processBlock - update called if existing and updated records`() {
        val block = BLOCK_VIP180_CONTRACTS

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
                )
            )

        every { contractService.getExisting(any()) } returns existing
        every { contractService.parseContracts(any(), any(), existing) } returns existing
        every { contractService.update(any(), any()) } just Runs

        indexer.processBlock(block)

        verify(exactly = 1) { contractService.update(existing, existing) }
    }

    @Test
    fun `processBlock - update called if no existing records`() {
        val block = BLOCK_VIP180_CONTRACTS

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
                )
            )

        every { contractService.getExisting(any()) } returns emptyList()
        every { contractService.parseContracts(any(), any(), emptyList()) } returns updated
        every { contractService.update(any(), any()) } just Runs

        indexer.processBlock(block)

        verify(exactly = 1) { contractService.update(updated, emptyList()) }
    }

    @Test
    fun `processBlock - update called is only existing records and not updated`() {
        val block = BLOCK_VIP180_CONTRACTS

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
                )
            )

        every { contractService.getExisting(any()) } returns existing
        every { contractService.parseContracts(any(), any(), existing) } returns emptyList()
        every { contractService.update(any(), any()) } just Runs

        indexer.processBlock(block)

        verify(exactly = 1) { contractService.update(emptyList(), existing) }
    }
}
