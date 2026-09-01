package org.vechain.indexer.blocks

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.blocks.repository.BlockRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction

@ExtendWith(MockKExtension::class)
class BlocksServiceTest {

    @MockK(relaxed = true) lateinit var repository: BlockRepository

    private lateinit var service: BlocksService

    @BeforeEach
    fun setUp() {
        service = BlocksService(repository)
    }

    private fun transaction(id: String, clauses: Int = 0, paid: String = "0x1") =
        Transaction(
            id = id,
            type = 0L,
            chainTag = 1,
            blockRef = "0x00",
            expiration = 720,
            clauses = List(clauses) { Clause(to = "0xto", value = "0x0", data = "0x") },
            gasPriceCoef = 0,
            gas = 21000,
            origin = "0xorigin",
            nonce = "0x1",
            size = 100,
            gasUsed = 21000,
            gasPayer = "0xpayer",
            paid = paid,
            reward = "0x1",
            reverted = false,
            outputs = emptyList(),
        )

    private fun block(
        number: Long = 100,
        baseFeePerGas: String? = "0x9184e72a000",
        transactions: List<Transaction> = emptyList(),
    ) =
        Block(
            number = number,
            id = "0xblock-$number",
            size = 361,
            parentID = "0xblock-${number - 1}",
            timestamp = 1_700_000_000,
            gasLimit = 40_000_000,
            baseFeePerGas = baseFeePerGas,
            beneficiary = "0xbeneficiary",
            gasUsed = 21_000,
            totalScore = 12_345,
            txsRoot = "0xtxsRoot",
            txsFeatures = 1,
            stateRoot = "0xstateRoot",
            receiptsRoot = "0xreceiptsRoot",
            com = true,
            signer = "0xsigner",
            isTrunk = true,
            isFinalized = false,
            transactions = transactions,
        )

    @Test
    fun `processBlock projects every header field`() {
        val source = block()

        val indexed = service.processBlock(source)

        assertEquals(source.number, indexed.blockNumber)
        assertEquals(source.id, indexed.blockId)
        assertEquals(source.timestamp, indexed.blockTimestamp)
        assertEquals(source.size, indexed.size)
        assertEquals(source.parentID, indexed.parentID)
        assertEquals(source.gasLimit, indexed.gasLimit)
        assertEquals(source.gasUsed, indexed.gasUsed)
        assertEquals(source.beneficiary, indexed.beneficiary)
        assertEquals(source.totalScore, indexed.totalScore)
        assertEquals(source.txsRoot, indexed.txsRoot)
        assertEquals(source.txsFeatures, indexed.txsFeatures)
        assertEquals(source.stateRoot, indexed.stateRoot)
        assertEquals(source.receiptsRoot, indexed.receiptsRoot)
        assertEquals(source.com, indexed.com)
        assertEquals(source.signer, indexed.signer)
        assertEquals(source.baseFeePerGas, indexed.baseFeePerGas)
        assertEquals("100", indexed.id)
    }

    @Test
    fun `processBlock reduces transactions to their ids`() {
        val indexed =
            service.processBlock(
                block(transactions = listOf(transaction("0xtx1"), transaction("0xtx2")))
            )

        assertEquals(listOf("0xtx1", "0xtx2"), indexed.transactions)
    }

    @Test
    fun `processBlock leaves transactions empty for a block with no transactions`() {
        assertEquals(emptyList<String>(), service.processBlock(block()).transactions)
    }

    @Test
    fun `processBlock sums clauses and paid across the block's transactions`() {
        val indexed =
            service.processBlock(
                block(
                    transactions =
                        listOf(
                            transaction("0xtx1", clauses = 2, paid = "0x0de0b6b3a7640000"),
                            transaction("0xtx2", clauses = 3, paid = "0x0de0b6b3a7640000"),
                        )
                )
            )

        assertEquals(5, indexed.clauseCount)
        assertEquals("0x1bc16d674ec80000", indexed.totalVthoPaid)
    }

    @Test
    fun `processBlock zeroes the totals for a block with no transactions`() {
        val indexed = service.processBlock(block())

        assertEquals(0, indexed.clauseCount)
        assertEquals("0x0", indexed.totalVthoPaid)
    }

    @Test
    fun `processBlock leaves baseFeePerGas null for a pre-GALACTICA block`() {
        assertNull(service.processBlock(block(baseFeePerGas = null)).baseFeePerGas)
    }

    @Test
    fun `save delegates to the repository`() {
        val indexed = service.processBlock(block())
        every { repository.save(indexed) } returns indexed

        service.save(indexed)

        verify(exactly = 1) { repository.save(indexed) }
    }
}
