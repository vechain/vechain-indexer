package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.HexUtils.toHex

@ExtendWith(MockKExtension::class)
internal class SafeTxProposalServiceTest {

    @MockK lateinit var repository: SafeWriteRepository

    private val emitter = "0xeeee000000000000000000000000000000000000"
    private val safe = "0x1111111111111111111111111111111111111111"
    private val proposer = "0xaaaa111111111111111111111111111111111111"
    private val to = "0x2222222222222222222222222222222222222222"
    private val txHash = "0x" + "aa".repeat(32)
    private val chainTx = "0x" + "cc".repeat(32)
    private val known = setOf(safe)

    private lateinit var service: SafeTxProposalService

    @BeforeEach
    fun setUp() {
        every { repository.findCurrentProposals(any()) } returns emptyList()
        service = SafeTxProposalService(repository, emitter)
    }

    private fun event(
        type: String,
        blockNumber: Long,
        params: Map<String, Any>,
        address: String = emitter,
    ): IndexedEvent =
        buildIndexedEvent(
            blockId = toHex(blockNumber, 64),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 100,
            txId = chainTx,
            address = address,
            eventType = type,
            params = AbiEventParameters(returnValues = params),
        )

    private fun proposed(
        blockNumber: Long = 10L,
        description: String = "test",
        safe: String = this.safe,
        address: String = emitter,
    ) =
        event(
            SafeTxProposalService.SAFE_TX_PROPOSED,
            blockNumber,
            mapOf(
                "safe" to safe,
                "proposer" to proposer,
                "txHash" to txHash,
                "to" to to,
                "value" to BigInteger("1000000000000000000"),
                "data" to "0xdeadbeef",
                "operation" to 0,
                "nonce" to BigInteger("3"),
                "description" to description,
            ),
            address,
        )

    private fun hashFields(blockNumber: Long = 10L) =
        event(
            SafeTxProposalService.SAFE_TX_HASH_FIELDS,
            blockNumber,
            mapOf(
                "safe" to safe,
                "txHash" to txHash,
                "safeTxGas" to BigInteger("21000"),
                "baseGas" to BigInteger.ZERO,
                "gasPrice" to BigInteger.ZERO,
                "gasToken" to "0x0000000000000000000000000000000000000000",
                "refundReceiver" to "0x0000000000000000000000000000000000000000",
            ),
        )

    private fun batch(
        blockNumber: Long = 10L,
        targets: List<String> = listOf(to),
        values: List<BigInteger> = listOf(BigInteger.ZERO),
        datas: List<String> = listOf("0x"),
        operations: List<Int> = listOf(0),
        labels: List<String> = listOf("send"),
    ) =
        event(
            SafeTxProposalService.SAFE_BATCH_TX_PROPOSED,
            blockNumber,
            mapOf(
                "safe" to safe,
                "txHash" to txHash,
                "targets" to targets,
                "values" to values,
                "datas" to datas,
                "operations" to operations,
                "labels" to labels,
            ),
        )

    private fun proposal(block: Long, safeTxGas: BigInteger? = null) =
        SafeTxProposal(
            id = SafeTxProposal.buildId(safe, txHash),
            safe = safe,
            txHash = txHash,
            safeTxGas = safeTxGas,
            blockId = toHex(block, 64),
            blockNumber = block,
            blockTimestamp = block * 100,
        )

    @Test
    fun `the three events fold into one row identified by the indexed safe`() {
        val row = service.processEvents(listOf(proposed(), hashFields(), batch()), known).single()

        assertEquals(SafeTxProposal.buildId(safe, txHash), row.id)
        assertEquals(safe, row.safe)
        assertEquals(proposer, row.proposer)
        assertEquals(10L, row.proposedBlock)
        assertEquals(chainTx, row.proposedVechainTxId)
        assertEquals(to, row.to)
        assertEquals(BigInteger("1000000000000000000"), row.value)
        assertEquals(BigInteger("21000"), row.safeTxGas)
        assertEquals(listOf(to), row.subcalls?.map { it.target })
        assertEquals("send", row.subcalls?.single()?.label)
    }

    @Test
    fun `a later block fills in the envelope of a row that only had the gas fields`() {
        every { repository.findCurrentProposals(any()) } returns
            listOf(proposal(block = 10, safeTxGas = BigInteger("21000")))

        val row = service.processEvents(listOf(proposed(blockNumber = 11)), known).single()

        assertEquals(11L, row.blockNumber)
        assertEquals(BigInteger("21000"), row.safeTxGas)
        assertEquals(proposer, row.proposer)
    }

    @Test
    fun `a description longer than the column is truncated`() {
        val row =
            service.processEvents(listOf(proposed(description = "x".repeat(600))), known).single()

        assertEquals(SafeTxProposal.DESCRIPTION_MAX_LENGTH, row.description?.length)
    }

    @Test
    fun `a batch whose arrays disagree leaves the subcalls alone`() {
        val row =
            service
                .processEvents(
                    listOf(proposed(), batch(labels = listOf("a", "b"))),
                    known,
                )
                .single()

        assertNull(row.subcalls)
    }

    @Test
    fun `the gas fields of an earlier block survive the envelope in a later one`() {
        val rows = service.processEvents(listOf(hashFields(10), proposed(blockNumber = 11)), known)

        assertEquals(listOf(10L, 11L), rows.map { it.blockNumber })
        assertEquals(BigInteger("21000"), rows.last().safeTxGas)
        assertEquals(proposer, rows.last().proposer)
    }

    @Test
    fun `a proposal from a contract that is not the emitter is dropped`() {
        val stranger = "0x8888888888888888888888888888888888888888"

        assertEquals(
            emptyList<SafeTxProposal>(),
            service.processEvents(listOf(proposed(address = stranger)), known),
        )
    }

    @Test
    fun `a proposal naming an address the factory never deployed is dropped`() {
        val stranger = "0x9999999999999999999999999999999999999999"

        assertEquals(
            emptyList<SafeTxProposal>(),
            service.processEvents(listOf(proposed(safe = stranger)), known),
        )
        assertEquals(emptyList<SafeTxProposal>(), service.processEvents(emptyList(), known))
    }
}
