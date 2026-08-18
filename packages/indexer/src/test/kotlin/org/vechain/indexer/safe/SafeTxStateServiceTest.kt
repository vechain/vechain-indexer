package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.safe.repository.SafeProxyRepository
import org.vechain.indexer.safe.repository.SafeTxStateRepository

@ExtendWith(MockKExtension::class)
internal class SafeTxStateServiceTest {

    @MockK lateinit var repository: SafeTxStateRepository
    @MockK lateinit var safeProxyRepository: SafeProxyRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private val safe = "0x1111111111111111111111111111111111111111"
    private val txHash = "0x" + "a".repeat(64)
    private val ownerA = "0xAAAA111111111111111111111111111111111111"
    private val ownerB = "0xBBBB222222222222222222222222222222222222"

    private lateinit var service: SafeTxStateService

    @BeforeEach
    fun setUp() {
        service =
            SafeTxStateService(
                repository,
                safeProxyRepository,
                mongoTemplate,
                inlineVersioningProperties,
            )
        every { repository.findById(any<String>()) } returns Optional.empty()
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
        every { safeProxyRepository.findAllById(any<Iterable<String>>()) } answers
            {
                @Suppress("UNCHECKED_CAST")
                (firstArg<Iterable<String>>()).map { id ->
                    SafeProxy(
                        id = id,
                        singleton = "0xsingleton",
                        createdBlock = 1L,
                        createdTimestamp = 100L,
                        vechainTxId = "0xtx",
                        blockId = "0xblock",
                        blockNumber = 1L,
                        blockTimestamp = 100L,
                        version = 1,
                    )
                }
            }
    }

    private fun approveHashEvent(
        owner: String,
        blockNumber: Long = 10L,
        blockTimestamp: Long = 1000L,
        blockId: String = "0xblock",
        txId: String = "0xchaintx",
    ) =
        buildIndexedEvent(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            txId = txId,
            eventType = SafeTxStateService.APPROVE_HASH,
            address = safe,
            params =
                AbiEventParameters(
                    returnValues = mapOf("approvedHash" to txHash, "owner" to owner)
                ),
        )

    private fun executionSuccessEvent(
        executor: String? = "0xEXECUTOR",
        blockNumber: Long = 12L,
        blockTimestamp: Long = 1200L,
        blockId: String = "0xblock2",
        txId: String = "0xchainexec",
    ) =
        buildIndexedEvent(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            txId = txId,
            origin = executor ?: "origin",
            eventType = SafeTxStateService.EXECUTION_SUCCESS,
            address = safe,
            params = AbiEventParameters(returnValues = mapOf("txHash" to txHash, "payment" to "0")),
        )

    private fun executionFailureEvent(
        executor: String? = "0xEXECUTOR",
        blockNumber: Long = 12L,
        blockTimestamp: Long = 1200L,
        blockId: String = "0xblock2",
        txId: String = "0xchainfail",
    ) =
        buildIndexedEvent(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            txId = txId,
            origin = executor ?: "origin",
            eventType = SafeTxStateService.EXECUTION_FAILURE,
            address = safe,
            params = AbiEventParameters(returnValues = mapOf("txHash" to txHash, "payment" to "0")),
        )

    @Test
    fun `processBlock with no relevant events returns empty`() {
        val (updated, existing) = service.processBlock(emptyList())
        assertEquals(0, updated.size)
        assertEquals(0, existing.size)
    }

    @Test
    fun `ApproveHash creates a new tx state with one approver`() {
        val (updated, archived) = service.processBlock(listOf(approveHashEvent(ownerA)))

        assertEquals(1, updated.size)
        assertEquals(0, archived.size)
        val state = updated.single()
        assertEquals(safe.lowercase(), state.safe)
        assertEquals(txHash.lowercase(), state.txHash)
        assertEquals(1, state.approvers.size)
        assertEquals(ownerA.lowercase(), state.approvers.single().owner)
        assertFalse(state.executed)
        assertFalse(state.failed)
        assertEquals(1, state.version)
    }

    @Test
    fun `Multiple approvals across blocks accumulate`() {
        val (firstUpdated, _) = service.processBlock(listOf(approveHashEvent(ownerA)))
        val firstState = firstUpdated.single()
        every { repository.findAllById(any<Iterable<String>>()) } returns listOf(firstState)
        every { repository.findById(firstState.id) } returns Optional.of(firstState)

        val (secondUpdated, archived) =
            service.processBlock(
                listOf(approveHashEvent(ownerB, blockNumber = 11L, txId = "0xtx2"))
            )

        assertEquals(1, secondUpdated.size)
        assertEquals(1, archived.size)
        val state = secondUpdated.single()
        assertEquals(2, state.approvers.size)
        assertEquals(2, state.version)
    }

    @Test
    fun `Duplicate ApproveHash from same owner is ignored`() {
        val (firstUpdated, _) = service.processBlock(listOf(approveHashEvent(ownerA)))
        val firstState = firstUpdated.single()
        every { repository.findAllById(any<Iterable<String>>()) } returns listOf(firstState)
        every { repository.findById(firstState.id) } returns Optional.of(firstState)

        val (secondUpdated, secondArchived) =
            service.processBlock(
                listOf(approveHashEvent(ownerA, blockNumber = 11L, txId = "0xtxdup"))
            )

        assertEquals(0, secondUpdated.size)
        assertEquals(0, secondArchived.size)
    }

    @Test
    fun `ExecutionSuccess marks the tx as executed and records executor`() {
        val (updated, _) = service.processBlock(listOf(executionSuccessEvent()))
        val state = updated.single()
        assertTrue(state.executed)
        assertFalse(state.failed)
        assertEquals(12L, state.executedBlock)
        assertNotNull(state.executor)
        assertEquals("0xexecutor", state.executor)
        assertEquals("0xchainexec", state.vechainTxId)
    }

    @Test
    fun `ExecutionFailure marks the tx as executed and failed`() {
        val (updated, _) = service.processBlock(listOf(executionFailureEvent()))
        val state = updated.single()
        assertTrue(state.executed)
        assertTrue(state.failed)
        assertEquals("0xchainfail", state.vechainTxId)
    }

    @Test
    fun `Approval and execution in the same block collapse into one current state`() {
        val approval = approveHashEvent(ownerA, blockNumber = 10L, blockId = "0xblock")
        val exec = executionSuccessEvent(blockNumber = 10L, blockId = "0xblock")
        val (updated, _) = service.processBlock(listOf(approval, exec))
        assertEquals(1, updated.size)
        val state = updated.single()
        assertEquals(1, state.approvers.size)
        assertTrue(state.executed)
    }

    @Test
    fun `tx hash on ExecutionSuccess uses txHash param not approvedHash`() {
        val (updated, _) = service.processBlock(listOf(executionSuccessEvent()))
        assertEquals(txHash.lowercase(), updated.single().txHash)
    }

    @Test
    fun `Events without a Safe address are skipped`() {
        val event =
            buildIndexedEvent(
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                eventType = SafeTxStateService.APPROVE_HASH,
                address = null,
                params =
                    AbiEventParameters(
                        returnValues = mapOf("approvedHash" to txHash, "owner" to ownerA)
                    ),
            )
        val (updated, _) = service.processBlock(listOf(event))
        assertEquals(0, updated.size)
    }

    @Test
    fun `Unknown event type is ignored`() {
        val event =
            buildIndexedEvent(
                blockId = "0xblock",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                eventType = "SomeOtherEvent",
                address = safe,
                params = AbiEventParameters(returnValues = mapOf("txHash" to txHash)),
            )
        val (updated, _) = service.processBlock(listOf(event))
        assertEquals(0, updated.size)
    }

    @Test
    fun `Events from addresses not registered in the SafeProxy collection are dropped`() {
        every { safeProxyRepository.findAllById(any<Iterable<String>>()) } answers
            {
                @Suppress("UNCHECKED_CAST")
                (firstArg<Iterable<String>>())
                    .filter { it.equals(safe.lowercase(), ignoreCase = true) }
                    .map { id ->
                        SafeProxy(
                            id = id,
                            singleton = "0xsingleton",
                            createdBlock = 1L,
                            createdTimestamp = 100L,
                            vechainTxId = "0xtx",
                            blockId = "0xblock",
                            blockNumber = 1L,
                            blockTimestamp = 100L,
                            version = 1,
                        )
                    }
            }

        val realSafeEvent = approveHashEvent(ownerA)
        val nonSafeEvent =
            buildIndexedEvent(
                blockId = "0xblock",
                blockNumber = 11L,
                blockTimestamp = 1100L,
                eventType = SafeTxStateService.APPROVE_HASH,
                address = "0xC0FFEE0000000000000000000000000000000000",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("approvedHash" to txHash, "owner" to ownerA)
                    ),
            )

        val (updated, _) = service.processBlock(listOf(realSafeEvent, nonSafeEvent))

        assertEquals(1, updated.size)
        assertEquals(safe.lowercase(), updated.single().safe)
    }
}
