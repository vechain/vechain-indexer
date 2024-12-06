package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.fixtures.BlockFixtures.BLOCK_VIP180_CONTRACTS
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction
import org.vechain.indexer.thor.model.TxEvent
import org.vechain.indexer.utils.BlockUtils.extractMasterChangeEvents
import strikt.api.expect
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
internal class ContractIndexerTest {

    @MockK lateinit var contractRepository: ContractRepository

    @MockK lateinit var contractService: ContractService

    private lateinit var indexer: ContractIndexer

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        indexer =
            ContractIndexer(
                contractService,
                contractRepository,
                DefaultThorClient("http://localhost:8669"),
                1L,
                1000L,
                true,
                1000L,
            )
    }

    @Test
    fun `findExisting - empty list`() {
        val data = emptyList<Triple<TxEvent, Transaction, Clause>>()
        every { contractService.getExisting(any()) } returns emptyList()

        val result = indexer.findExisting(data)

        expect { that(result).isEmpty() }

        verify(exactly = 1) { contractService.getExisting(emptyList()) }
    }

    @Test
    fun `findExisting - non-empty list`() {
        val data = extractMasterChangeEvents(BLOCK_VIP180_CONTRACTS)

        val response =
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

        every { contractService.getExisting(any()) } returns response

        val result = indexer.findExisting(data)

        expect {
            that(result).hasSize(1)
            that(result.first().address).isEqualTo("0x000000")
        }

        verify(exactly = 1) { contractService.getExisting(data.map { (event) -> event.address }) }
    }

    @Test
    fun `parseRecords - empty list`() {
        val data = emptyList<Triple<TxEvent, Transaction, Clause>>()
        val existing = emptyList<IndexedContract>()

        every { contractService.parseContracts(any(), any(), any()) } returns existing

        val result = indexer.parseRecords(BLOCK_VIP180_CONTRACTS, data, existing)

        expect { that(result).isEmpty() }
    }

    @Test
    fun `parseRecords - non-empty list`() {
        val data = extractMasterChangeEvents(BLOCK_VIP180_CONTRACTS)

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

        every { contractService.parseContracts(any(), any(), any()) } returns existing

        val result = indexer.parseRecords(BLOCK_VIP180_CONTRACTS, data, existing)

        expect {
            that(result).hasSize(1)
            that(result.first().address).isEqualTo("0x000000")
        }

        verify(exactly = 1) {
            contractService.parseContracts(BLOCK_VIP180_CONTRACTS, data, existing)
        }
    }
}
