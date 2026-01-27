package org.vechain.indexer.accounts

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction

@ExtendWith(MockKExtension::class)
internal class AccountOverviewServiceTest {
    @MockK lateinit var repository: AccountOverviewRepository

    @MockK lateinit var pruner: PostgresPruner

    private lateinit var service: TestableService

    private class TestableService(repository: AccountOverviewRepository, pruner: PostgresPruner) :
        AccountOverviewService(repository, pruner) {
        fun callTransactionsSentRule(
            block: Block,
            updatedResult: MutableMap<String, AccountOverview>,
            archiveResult: MutableMap<String, AccountOverview>,
        ) = transactionsSentRule(block, updatedResult, archiveResult)

        fun callVthoBurnedRule(
            block: Block,
            updatedResult: MutableMap<String, AccountOverview>,
            archiveResult: MutableMap<String, AccountOverview>,
        ) = vthoBurnedRule(block, updatedResult, archiveResult)

        fun callVthoDelegatedRule(
            block: Block,
            updatedResult: MutableMap<String, AccountOverview>,
            archiveResult: MutableMap<String, AccountOverview>,
        ) = vthoDelegatedRule(block, updatedResult, archiveResult)

        fun callGasUsedRule(
            block: Block,
            updatedResult: MutableMap<String, AccountOverview>,
            archiveResult: MutableMap<String, AccountOverview>,
        ) = gasUsedRule(block, updatedResult, archiveResult)

        fun callVetSentRule(
            block: Block,
            vetTransferEvents: List<IndexedEvent>,
            updatedResult: MutableMap<String, AccountOverview>,
            archiveResult: MutableMap<String, AccountOverview>,
        ) = vetSentRule(block, vetTransferEvents, updatedResult, archiveResult)

        fun callVetReceivedRule(
            block: Block,
            vetTransferEvents: List<IndexedEvent>,
            updatedResult: MutableMap<String, AccountOverview>,
            archiveResult: MutableMap<String, AccountOverview>,
        ) = vetReceivedRule(block, vetTransferEvents, updatedResult, archiveResult)

        fun callCreateNewAccountOverview(address: String, block: Block): AccountOverview =
            createNewAccountOverview(address, block)

        fun callResolveAccountOverviewForUpdateAndArchive(
            recordId: String,
            block: Block,
            updated: MutableMap<String, AccountOverview>,
            archived: MutableMap<String, AccountOverview>,
        ): AccountOverview =
            resolveAccountOverviewForUpdateAndArchive(recordId, block, updated, archived)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TestableService(repository, pruner)
    }

    private fun block(number: Long = 1L, transactions: List<Transaction> = emptyList()) =
        Block(
            id = "0xBLOCK",
            number = number,
            timestamp = 1234567890,
            parentID = "0xPARENT",
            size = 0,
            gasLimit = 0,
            baseFeePerGas = null,
            beneficiary = "0xBENEFICIARY",
            gasUsed = 0,
            totalScore = 0,
            txsRoot = "0xTXROOT",
            txsFeatures = 0,
            stateRoot = "0xSTATEROOT",
            receiptsRoot = "0xRECEIPTSROOT",
            signer = "0xSIGNER",
            isTrunk = true,
            isFinalized = true,
            transactions = transactions,
            com = false,
        )

    private fun tx(
        id: String,
        origin: String,
        gasPayer: String = origin,
        paid: String = "0x0",
        gasUsed: Long = 0,
        clausesCount: Int = 0,
    ): Transaction =
        Transaction(
            id = id,
            reward = "0x0",
            chainTag = 1,
            blockRef = "0x00",
            expiration = 720,
            clauses = List(clausesCount) { mockk<Clause>(relaxed = true) },
            gasPriceCoef = 0,
            gas = 21000,
            maxFeePerGas = "0x0",
            maxPriorityFeePerGas = "0x0",
            origin = origin,
            delegator = null,
            nonce = "0x1",
            dependsOn = null,
            size = 100,
            gasUsed = gasUsed,
            gasPayer = gasPayer,
            paid = paid,
            outputs = emptyList(),
            reverted = false,
            type = 1,
        )

    private fun vetTransferEvent(from: String, to: String, amount: String): IndexedEvent =
        buildIndexedEvent(
            eventType = "VET_TRANSFER",
            params =
                AbiEventParameters(
                    returnValues = mapOf("from" to from, "to" to to, "amount" to amount)
                ),
        )

    private fun existingAccountOverview(address: String, version: Int = 3) =
        AccountOverview(
            address = address,
            version = version,
            blockId = "0xOLD_BLOCK",
            blockNumber = 10L,
            blockTimestamp = 100L,
            firstSeen = 100L,
            lastSeen = 100L,
            transactionsSent = 1L,
            clausesSent = 2L,
            vthoBurned = BigInteger.ZERO,
            vthoDelegated = BigInteger.ZERO,
            gasUsed = BigInteger.ZERO,
            vetSent = BigInteger.ZERO,
            vetReceived = BigInteger.ZERO,
        )

    @Test
    fun `createNewAccountOverview sets initial values from block`() {
        val recordId = "0xNEW"
        val b = block(number = 42L)

        val created = service.callCreateNewAccountOverview(recordId, b)

        assertEquals(recordId, created.address)
        assertEquals(b.id, created.blockId)
        assertEquals(b.number, created.blockNumber)
        assertEquals(b.timestamp, created.blockTimestamp)
        assertEquals(1, created.version)
        assertEquals(b.timestamp, created.firstSeen)
        assertEquals(b.timestamp, created.lastSeen)
        assertEquals(0L, created.transactionsSent)
        assertEquals(0L, created.clausesSent)
        assertEquals(BigInteger.ZERO, created.vthoBurned)
        assertEquals(BigInteger.ZERO, created.vthoDelegated)
        assertEquals(BigInteger.ZERO, created.gasUsed)
        assertEquals(BigInteger.ZERO, created.vetSent)
        assertEquals(BigInteger.ZERO, created.vetReceived)
    }

    @Test
    fun `transactionsSentRule increments transactions and clauses per origin`() {
        val originA = "0xA"
        val originB = "0xB"
        val existingA =
            existingAccountOverview(originA, version = 3)
                .copy(transactionsSent = 5L, clausesSent = 7L)

        every { repository.findByAddress(originA) } returns existingA
        every { repository.findByAddress(originB) } returns null

        val b =
            block(
                number = 42L,
                transactions =
                    listOf(
                        tx(id = "0x1", origin = originA, clausesCount = 2),
                        tx(id = "0x2", origin = originA, clausesCount = 1),
                        tx(id = "0x3", origin = originB, clausesCount = 3),
                    ),
            )

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callTransactionsSentRule(b, updated, archived)

        val updatedA = updated[originA]!!
        assertEquals(4, updatedA.version)
        assertEquals(7L, updatedA.transactionsSent)
        assertEquals(10L, updatedA.clausesSent)
        assertEquals(b.timestamp, updatedA.lastSeen)
        assertSame(existingA, archived[originA])

        val updatedB = updated[originB]!!
        assertEquals(1, updatedB.version)
        assertEquals(1L, updatedB.transactionsSent)
        assertEquals(3L, updatedB.clausesSent)
        assertTrue(archived[originB] == null)
    }

    @Test
    fun `vthoBurnedRule sums tx paid per gas payer`() {
        val payerA = "0xPAYER_A"
        val payerB = "0xPAYER_B"
        val existingA =
            existingAccountOverview(payerA, version = 1).copy(vthoBurned = BigInteger("100"))

        every { repository.findByAddress(payerA) } returns existingA
        every { repository.findByAddress(payerB) } returns null

        val b =
            block(
                number = 42L,
                transactions =
                    listOf(
                        tx(id = "0x1", origin = "0xO1", gasPayer = payerA, paid = "0x10"),
                        tx(id = "0x2", origin = "0xO2", gasPayer = payerA, paid = "0x05"),
                        tx(id = "0x3", origin = "0xO3", gasPayer = payerB, paid = "0x02"),
                    ),
            )

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVthoBurnedRule(b, updated, archived)

        val updatedA = updated[payerA]!!
        assertEquals(BigInteger("121"), updatedA.vthoBurned) // 100 + 16 + 5
        assertSame(existingA, archived[payerA])

        val updatedB = updated[payerB]!!
        assertEquals(BigInteger("2"), updatedB.vthoBurned)
        assertTrue(archived[payerB] == null)
    }

    @Test
    fun `vthoDelegatedRule counts paid only when origin differs from gas payer`() {
        val payer = "0xPAYER"
        val existing =
            existingAccountOverview(payer, version = 1).copy(vthoDelegated = BigInteger("7"))
        every { repository.findByAddress(payer) } returns existing

        val b =
            block(
                number = 42L,
                transactions =
                    listOf(
                        tx(
                            id = "0x1",
                            origin = "0xO1",
                            gasPayer = payer,
                            paid = "0x10",
                        ), // delegated
                        tx(
                            id = "0x2",
                            origin = payer,
                            gasPayer = payer,
                            paid = "0x20",
                        ), // not delegated
                        tx(
                            id = "0x3",
                            origin = "0xO2",
                            gasPayer = payer,
                            paid = "0x01",
                        ), // delegated
                    ),
            )

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVthoDelegatedRule(b, updated, archived)

        val updatedRecord = updated[payer]!!
        assertEquals(BigInteger("24"), updatedRecord.vthoDelegated) // 7 + 16 + 1
        assertSame(existing, archived[payer])
    }

    @Test
    fun `gasUsedRule sums gasUsed for transaction origins`() {
        val originA = "0xA"
        val originB = "0xB"
        val existingA = existingAccountOverview(originA, version = 1).copy(gasUsed = BigInteger.TEN)

        every { repository.findByAddress(originA) } returns existingA
        every { repository.findByAddress(originB) } returns null

        val b =
            block(
                number = 42L,
                transactions =
                    listOf(
                        tx(id = "0x1", origin = originA, gasUsed = 100),
                        tx(id = "0x2", origin = originA, gasUsed = 5),
                        tx(id = "0x3", origin = originB, gasUsed = 7),
                    ),
            )

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callGasUsedRule(b, updated, archived)

        assertEquals(BigInteger("115"), updated[originA]!!.gasUsed) // 10 + 100 + 5
        assertEquals(BigInteger("7"), updated[originB]!!.gasUsed)
    }

    @Test
    fun `vetSentRule sums value per from account`() {
        val fromA = "0xFROM_A"
        val fromB = "0xFROM_B"
        val existingA =
            existingAccountOverview(fromA, version = 1).copy(vetSent = BigInteger("100"))

        every { repository.findByAddress(fromA) } returns existingA
        every { repository.findByAddress(fromB) } returns null

        val events =
            listOf(
                vetTransferEvent(from = fromA, to = "0xTO_1", amount = "10"),
                vetTransferEvent(from = fromA, to = "0xTO_2", amount = "5"),
                vetTransferEvent(from = fromB, to = "0xTO_3", amount = "7"),
            )

        val b = block(number = 42L)
        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVetSentRule(b, events, updated, archived)

        assertEquals(BigInteger("115"), updated[fromA]!!.vetSent)
        assertEquals(BigInteger("7"), updated[fromB]!!.vetSent)
    }

    @Test
    fun `vetReceivedRule sums value per to account`() {
        val toA = "0xTO_A"
        val toB = "0xTO_B"
        val existingA =
            existingAccountOverview(toA, version = 1).copy(vetReceived = BigInteger("100"))

        every { repository.findByAddress(toA) } returns existingA
        every { repository.findByAddress(toB) } returns null

        val events =
            listOf(
                vetTransferEvent(from = "0xFROM_1", to = toA, amount = "10"),
                vetTransferEvent(from = "0xFROM_2", to = toA, amount = "5"),
                vetTransferEvent(from = "0xFROM_3", to = toB, amount = "7"),
            )

        val b = block(number = 42L)
        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVetReceivedRule(b, events, updated, archived)

        assertEquals(BigInteger("115"), updated[toA]!!.vetReceived)
        assertEquals(BigInteger("7"), updated[toB]!!.vetReceived)
    }

    @Test
    fun `returns cached record when already updated`() {
        val recordId = "0xACC"
        val cached = existingAccountOverview(recordId, version = 7)

        val updated = mutableMapOf(recordId to cached)
        val archived = mutableMapOf<String, AccountOverview>()

        val resolved =
            service.callResolveAccountOverviewForUpdateAndArchive(
                recordId,
                block(),
                updated,
                archived,
            )

        assertSame(cached, resolved)
        assertSame(cached, updated[recordId])
        assertTrue(archived.isEmpty())
    }

    @Test
    fun `archives existing record and returns version bumped copy`() {
        val recordId = "0xACC"
        val existing = existingAccountOverview(recordId, version = 3)
        every { repository.findByAddress(recordId) } returns existing

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()
        val b = block(number = 42L)

        val resolved =
            service.callResolveAccountOverviewForUpdateAndArchive(recordId, b, updated, archived)

        assertEquals(4, resolved.version)
        assertEquals(b.timestamp, resolved.lastSeen)
        assertSame(resolved, updated[recordId])
        assertSame(existing, archived[recordId])
        assertEquals(existing.lastSeen, archived[recordId]?.lastSeen)
    }

    @Test
    fun `creates new record when none exists`() {
        val recordId = "0xNEW"
        val b = block(number = 42L)
        every { repository.findByAddress(recordId) } returns null

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        val resolved =
            service.callResolveAccountOverviewForUpdateAndArchive(recordId, b, updated, archived)

        assertEquals(recordId, resolved.address)
        assertEquals(1, resolved.version)
        assertEquals(b.id, resolved.blockId)
        assertEquals(b.number, resolved.blockNumber)
        assertEquals(b.timestamp, resolved.blockTimestamp)
        assertEquals(b.timestamp, resolved.firstSeen)
        assertEquals(b.timestamp, resolved.lastSeen)

        assertSame(resolved, updated[recordId])
        assertTrue(archived.isEmpty())
    }

    @Test
    fun `does not bump version twice within same block`() {
        val recordId = "0xACC"
        val existing = existingAccountOverview(recordId, version = 3)
        every { repository.findByAddress(recordId) } returns existing

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()
        val b = block(number = 42L)

        val first =
            service.callResolveAccountOverviewForUpdateAndArchive(recordId, b, updated, archived)
        val second =
            service.callResolveAccountOverviewForUpdateAndArchive(recordId, b, updated, archived)

        assertSame(first, second)
        assertEquals(4, second.version)
        assertEquals(b.timestamp, second.lastSeen)
        assertSame(existing, archived[recordId])
    }
}
