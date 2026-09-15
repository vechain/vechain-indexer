package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.HexUtils.toHex

@ExtendWith(MockKExtension::class)
internal class SafeTxStateServiceTest {

    @MockK lateinit var repository: SafeWriteRepository

    private val safe = "0x1111111111111111111111111111111111111111"
    private val txHash = "0x" + "aa".repeat(32)
    private val ownerA = "0xaaaa111111111111111111111111111111111111"
    private val ownerB = "0xbbbb222222222222222222222222222222222222"
    private val executor = "0xeeee333333333333333333333333333333333333"
    private val chainTx = "0x" + "cc".repeat(32)
    private val known = setOf(safe)

    private lateinit var service: SafeTxStateService

    @BeforeEach
    fun setUp() {
        every { repository.findCurrentTxStates(any()) } returns emptyList()
        service = SafeTxStateService(repository)
    }

    private fun approve(owner: String, blockNumber: Long = 10L, address: String = safe) =
        event(
            SafeTxStateService.APPROVE_HASH,
            blockNumber,
            address,
            mapOf("approvedHash" to txHash, "owner" to owner),
        )

    private fun executed(type: String, blockNumber: Long = 12L) =
        event(type, blockNumber, safe, mapOf("txHash" to txHash, "payment" to "0"))

    private fun event(
        type: String,
        blockNumber: Long,
        address: String,
        params: Map<String, Any>,
    ): IndexedEvent =
        buildIndexedEvent(
            blockId = toHex(blockNumber, 64),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 100,
            txId = chainTx,
            origin = executor,
            eventType = type,
            address = address,
            params = AbiEventParameters(returnValues = params),
        )

    private fun state(approvers: List<SafeTxApproval>, block: Long) =
        SafeTxState(
            id = SafeTxState.buildId(safe, txHash),
            safe = safe,
            txHash = txHash,
            approvers = approvers,
            blockId = toHex(block, 64),
            blockNumber = block,
            blockTimestamp = block * 100,
        )

    @Test
    fun `an approval opens the state and the next one is added to it`() {
        val first = service.processEvents(listOf(approve(ownerA)), known).single()
        assertEquals(listOf(ownerA), first.approvers.map { it.owner })
        assertEquals(10L, first.approvers[0].block)
        assertEquals(chainTx, first.approvers[0].vechainTxId)
        assertFalse(first.executed)

        every { repository.findCurrentTxStates(any()) } returns
            listOf(state(first.approvers, block = 10))
        val second = service.processEvents(listOf(approve(ownerB, 11)), known).single()
        assertEquals(listOf(ownerA, ownerB), second.approvers.map { it.owner })
    }

    @Test
    fun `an owner who approves twice is still one approver`() {
        val rows = service.processEvents(listOf(approve(ownerA), approve(ownerA, 11)), known)

        assertEquals(listOf(1, 1), rows.map { it.approvers.size })
    }

    @Test
    fun `an execution records who ran it, and a failure says so`() {
        val success =
            service
                .processEvents(
                    listOf(approve(ownerA), executed(SafeTxStateService.EXECUTION_SUCCESS)),
                    known,
                )
                .last()
        assertTrue(success.executed)
        assertFalse(success.failed)
        assertEquals(executor, success.executor)
        assertEquals(12L, success.executedBlock)
        assertEquals(chainTx, success.vechainTxId)

        val failure =
            service
                .processEvents(listOf(executed(SafeTxStateService.EXECUTION_FAILURE)), known)
                .single()
        assertTrue(failure.executed)
        assertTrue(failure.failed)
    }

    @Test
    fun `an approval and the execution in one block collapse into one row`() {
        val rows =
            service.processEvents(
                listOf(approve(ownerA, 20), executed(SafeTxStateService.EXECUTION_SUCCESS, 20)),
                known,
            )

        assertEquals(1, rows.size)
        assertEquals(listOf(ownerA), rows[0].approvers.map { it.owner })
        assertTrue(rows[0].executed)
    }

    @Test
    fun `an approval and the execution in later blocks of one entry stay on one state`() {
        val rows =
            service.processEvents(
                listOf(approve(ownerA, 20), executed(SafeTxStateService.EXECUTION_SUCCESS, 21)),
                known,
            )

        assertEquals(listOf(20L, 21L), rows.map { it.blockNumber })
        assertEquals(listOf(ownerA), rows.last().approvers.map { it.owner })
        assertTrue(rows.last().executed)
    }

    @Test
    fun `an event from an address the factory never deployed is dropped`() {
        val stranger = "0x9999999999999999999999999999999999999999"

        assertEquals(
            emptyList<SafeTxState>(),
            service.processEvents(listOf(approve(ownerA, address = stranger)), known),
        )
    }
}
