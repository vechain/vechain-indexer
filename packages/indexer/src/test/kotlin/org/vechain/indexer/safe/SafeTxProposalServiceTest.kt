package org.vechain.indexer.safe

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.safe.repository.SafeProxyRepository
import org.vechain.indexer.safe.repository.SafeTxProposalRepository

@ExtendWith(MockKExtension::class)
internal class SafeTxProposalServiceTest {

    @MockK lateinit var repository: SafeTxProposalRepository
    @MockK lateinit var safeProxyRepository: SafeProxyRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    private val emitter = "0xEEEE000000000000000000000000000000000000"
    private val safe = "0x1111111111111111111111111111111111111111"
    private val proposer = "0xAAAA111111111111111111111111111111111111"
    private val to = "0x2222222222222222222222222222222222222222"
    private val txHash = "0x" + "a".repeat(64)

    private lateinit var service: SafeTxProposalService

    @BeforeEach
    fun setUp() {
        service =
            SafeTxProposalService(
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

    private fun proposedEvent(
        blockNumber: Long = 10L,
        blockTimestamp: Long = 1000L,
        blockId: String = "0xblock",
        txId: String = "0xchaintx",
        description: String = "test",
    ) =
        buildIndexedEvent(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            txId = txId,
            address = emitter,
            eventType = SafeTxProposalService.SAFE_TX_PROPOSED,
            params =
                AbiEventParameters(
                    returnValues =
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
                        )
                ),
        )

    private fun hashFieldsEvent(
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
            address = emitter,
            eventType = SafeTxProposalService.SAFE_TX_HASH_FIELDS,
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "safe" to safe,
                            "txHash" to txHash,
                            "safeTxGas" to BigInteger("21000"),
                            "baseGas" to BigInteger("0"),
                            "gasPrice" to BigInteger("0"),
                            "gasToken" to "0x0000000000000000000000000000000000000000",
                            "refundReceiver" to "0x0000000000000000000000000000000000000000",
                        )
                ),
        )

    private fun batchEvent(
        targets: List<String> = listOf(to),
        values: List<BigInteger> = listOf(BigInteger.ZERO),
        datas: List<String> = listOf("0x"),
        operations: List<Int> = listOf(0),
        labels: List<String> = listOf("0x" + "0".repeat(64)),
        blockNumber: Long = 10L,
        blockId: String = "0xblock",
        txId: String = "0xchaintx",
    ) =
        buildIndexedEvent(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = 1000L,
            txId = txId,
            address = emitter,
            eventType = SafeTxProposalService.SAFE_BATCH_TX_PROPOSED,
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "safe" to safe,
                            "txHash" to txHash,
                            "targets" to targets,
                            "values" to values,
                            "datas" to datas,
                            "operations" to operations,
                            "labels" to labels,
                        )
                ),
        )

    @Test
    fun `processBlock with no relevant events returns empty`() {
        val (updated, existing) = service.processBlock(emptyList())
        assertEquals(0, updated.size)
        assertEquals(0, existing.size)
    }

    @Test
    fun `Proposed event records envelope and identity from indexed params not event address`() {
        val (updated, _) = service.processBlock(listOf(proposedEvent()))

        assertEquals(1, updated.size)
        val doc = updated.single()
        assertEquals(safe.lowercase(), doc.safe)
        assertEquals(txHash.lowercase(), doc.txHash)
        assertEquals(proposer.lowercase(), doc.proposer)
        assertEquals(to.lowercase(), doc.to)
        assertEquals(BigInteger("1000000000000000000"), doc.value)
        assertEquals(0, doc.operation)
        assertEquals(BigInteger("3"), doc.nonce)
        assertEquals("test", doc.description)
        assertTrue(doc.envelopeRecorded)
        assertFalse(doc.hashFieldsRecorded)
        assertNull(doc.subcalls)
        assertEquals(SafeTxProposal.buildId(safe, txHash), doc.id)
    }

    @Test
    fun `All three events for same proposal merge into one document`() {
        val (updated, _) =
            service.processBlock(listOf(proposedEvent(), hashFieldsEvent(), batchEvent()))

        assertEquals(1, updated.size)
        val doc = updated.single()
        assertTrue(doc.envelopeRecorded)
        assertTrue(doc.hashFieldsRecorded)
        assertEquals(BigInteger("21000"), doc.safeTxGas)
        assertNotNull(doc.subcalls)
        assertEquals(1, doc.subcalls!!.size)
    }

    @Test
    fun `HashFields-only event creates partial doc with hash fields recorded`() {
        val (updated, _) = service.processBlock(listOf(hashFieldsEvent()))

        val doc = updated.single()
        assertFalse(doc.envelopeRecorded)
        assertTrue(doc.hashFieldsRecorded)
        assertNull(doc.proposer)
        assertEquals(BigInteger("21000"), doc.safeTxGas)
    }

    @Test
    fun `Subsequent block backfills envelope on prior hashFields-only doc`() {
        val (firstUpdated, _) = service.processBlock(listOf(hashFieldsEvent(blockNumber = 9L)))
        val first = firstUpdated.single()
        every { repository.findAllById(any<Iterable<String>>()) } returns listOf(first)
        every { repository.findById(first.id) } returns Optional.of(first)

        val (secondUpdated, archived) =
            service.processBlock(listOf(proposedEvent(blockNumber = 10L)))

        assertEquals(1, secondUpdated.size)
        assertEquals(1, archived.size)
        val merged = secondUpdated.single()
        assertTrue(merged.envelopeRecorded)
        assertTrue(merged.hashFieldsRecorded)
        assertEquals(2, merged.version)
    }

    @Test
    fun `Description longer than max is truncated`() {
        val long = "x".repeat(SafeTxProposal.DESCRIPTION_MAX_LENGTH + 100)
        val (updated, _) = service.processBlock(listOf(proposedEvent(description = long)))

        assertEquals(SafeTxProposal.DESCRIPTION_MAX_LENGTH, updated.single().description!!.length)
    }

    @Test
    fun `Batch event with mismatched array lengths is ignored`() {
        val event = batchEvent(targets = listOf(to, to), values = listOf(BigInteger.ZERO))

        val (updated, _) = service.processBlock(listOf(event))

        assertNull(updated.single().subcalls)
    }

    @Test
    fun `Event without safe param is skipped`() {
        val event =
            buildIndexedEvent(
                address = emitter,
                eventType = SafeTxProposalService.SAFE_TX_PROPOSED,
                params = AbiEventParameters(returnValues = mapOf("txHash" to txHash)),
            )
        val (updated, _) = service.processBlock(listOf(event))
        assertEquals(0, updated.size)
    }

    @Test
    fun `Proposal whose safe param is not in the SafeProxy collection is dropped`() {
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
        val fakeSafe = "0xC0FFEE0000000000000000000000000000000000"
        val realProposal = proposedEvent()
        val fakeProposal =
            buildIndexedEvent(
                blockId = "0xblock",
                blockNumber = 11L,
                blockTimestamp = 1100L,
                address = emitter,
                eventType = SafeTxProposalService.SAFE_TX_PROPOSED,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "safe" to fakeSafe,
                                "proposer" to proposer,
                                "txHash" to ("0x" + "b".repeat(64)),
                                "to" to to,
                                "value" to BigInteger.ZERO,
                                "data" to "0x",
                                "operation" to 0,
                                "nonce" to BigInteger.ZERO,
                                "description" to "fake",
                            )
                    ),
            )

        val (updated, _) = service.processBlock(listOf(realProposal, fakeProposal))

        assertEquals(1, updated.size)
        assertEquals(safe.lowercase(), updated.single().safe)
    }
}
