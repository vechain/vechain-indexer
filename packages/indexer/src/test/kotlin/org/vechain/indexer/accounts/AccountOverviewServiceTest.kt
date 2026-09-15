package org.vechain.indexer.accounts

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.config.DetectedNetwork
import org.vechain.indexer.config.ForkConfig
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ExecuteAccountResponse
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction

@ExtendWith(MockKExtension::class)
internal class AccountOverviewServiceTest {
    @MockK lateinit var repository: AccountsWriteRepository
    @MockK lateinit var forkConfig: ForkConfig
    @MockK lateinit var networkDetectionService: NetworkDetectionService
    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: AccountOverviewService

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)
    private val carol = "0x" + "c".repeat(40)
    private val beneficiary = "0x" + "e".repeat(40)
    private val hayabusa = 1000L
    private val timestamp = 1_700_000_000L

    @BeforeEach
    fun setUp() {
        service =
            AccountOverviewService(repository, forkConfig, networkDetectionService, thorClient)
        every { repository.findCurrentOverviews(any()) } returns emptyList()
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(network = VeChainNetwork.MAINNET, genesisBlock = mockk(relaxed = true))
        every { forkConfig.getHayabusaBlock(VeChainNetwork.MAINNET) } returns hayabusa
        coEvery { thorClient.getAccountState(any(), any()) } returns
            ExecuteAccountResponse("0x0", "0x0", false)
    }

    private fun block(number: Long = 100L, transactions: List<Transaction> = emptyList()) =
        Block(
            id = "0x" + number.toString(16).padStart(64, '0'),
            number = number,
            timestamp = timestamp,
            parentID = "0x" + (number - 1).toString(16).padStart(64, '0'),
            size = 0,
            gasLimit = 0,
            baseFeePerGas = null,
            beneficiary = beneficiary,
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
        origin: String,
        gasPayer: String = origin,
        paid: String = "0x0",
        gasUsed: Long = 0,
        clauses: Int = 1,
    ): Transaction =
        Transaction(
            id = "0x1",
            reward = "0x0",
            chainTag = 1,
            blockRef = "0x00",
            expiration = 720,
            clauses = List(clauses) { mockk<Clause>(relaxed = true) },
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

    private fun vetTransfer(from: String, to: String, amount: BigInteger): IndexedEvent =
        buildIndexedEvent(
            eventType = "VET_TRANSFER",
            params =
                AbiEventParameters(
                    returnValues = mapOf("from" to from, "to" to to, "amount" to amount.toString())
                ),
        )

    private fun vthoTransfer(from: String, to: String, value: String): IndexedEvent =
        buildIndexedEvent(
            eventType = "Transfer",
            address = VTHO_CONTRACT_ADDRESS,
            params =
                AbiEventParameters(
                    returnValues = mapOf("from" to from, "to" to to, "value" to value)
                ),
        )

    private fun stored(
        address: String,
        vetBalance: BigInteger = BigInteger.ZERO,
        lastVthoSettlement: Long? = null,
    ) =
        AccountOverview(
            address = address,
            blockId = "0x" + "1".repeat(64),
            blockNumber = 10L,
            blockTimestamp = 100L,
            firstSeen = 100L,
            lastSeen = 100L,
            transactionsSent = 5L,
            clausesSent = 7L,
            vetBalance = vetBalance,
            lastVthoSettlement = lastVthoSettlement,
        )

    private fun vet(amount: Long): BigInteger = BigInteger.valueOf(amount) * BigInteger.TEN.pow(18)

    private fun energy(block: Block, before: String, after: String, vetBefore: String = "0x0") {
        coEvery {
            thorClient.getAccountState(beneficiary, BlockRevision.Id(block.parentID))
        } returns ExecuteAccountResponse(vetBefore, before, false)
        coEvery { thorClient.getAccountState(beneficiary, BlockRevision.Id(block.id)) } returns
            ExecuteAccountResponse("0x0", after, false)
    }

    private fun process(block: Block, events: List<IndexedEvent> = emptyList()) = runBlocking {
        service.processBlock(block, events)
    }

    private fun AccountOverviewService.Update.of(address: String) = overviews.single {
        it.address == address
    }

    @Test
    fun `a new account starts from the block that first names it`() {
        val block =
            block(transactions = listOf(tx(alice, paid = "0x64", gasUsed = 21, clauses = 3)))

        val update = process(block)

        val account = update.of(alice)
        assertEquals(block.id, account.blockId)
        assertEquals(block.number, account.blockNumber)
        assertEquals(timestamp, account.firstSeen)
        assertEquals(timestamp, account.lastSeen)
        assertEquals(1L, account.transactionsSent)
        assertEquals(3L, account.clausesSent)
        assertEquals(BigInteger.valueOf(21), account.gasUsed)
        assertEquals(BigInteger.valueOf(100), account.vthoBurned)
        assertEquals(BigInteger.ZERO, account.vthoDelegated)
        assertNull(account.lastVthoSettlement)
        assertTrue(update.balances.isEmpty())
    }

    @Test
    fun `transaction rules accumulate onto the stored row and stamp the block`() {
        val existing = stored(alice)
        every { repository.findCurrentOverviews(setOf(alice, beneficiary)) } returns
            listOf(existing)
        val block = block(transactions = listOf(tx(alice, clauses = 2), tx(alice, clauses = 1)))

        val account = process(block).of(alice)

        assertEquals(7L, account.transactionsSent)
        assertEquals(10L, account.clausesSent)
        assertEquals(100L, account.firstSeen)
        assertEquals(timestamp, account.lastSeen)
        assertEquals(block.number, account.blockNumber)
        assertEquals(5L, existing.transactionsSent)
    }

    @Test
    fun `gas paid by a delegator counts as burned and delegated`() {
        val update = process(block(transactions = listOf(tx(alice, gasPayer = bob, paid = "0x64"))))

        assertEquals(BigInteger.valueOf(100), update.of(bob).vthoBurned)
        assertEquals(BigInteger.valueOf(100), update.of(bob).vthoDelegated)
        assertEquals(BigInteger.ZERO, update.of(alice).vthoBurned)
        assertEquals(0L, update.of(bob).transactionsSent)
    }

    @Test
    fun `VET transfers move balances and record one for each address that moved`() {
        every { repository.findCurrentOverviews(any()) } returns
            listOf(stored(alice, vetBalance = vet(1000)))
        val block = block()

        val update =
            process(
                block,
                listOf(
                    vetTransfer(alice, bob, vet(300)),
                    vetTransfer(alice, bob, vet(1)),
                    vetTransfer(carol, carol, vet(5)),
                ),
            )

        assertEquals(vet(301), update.of(alice).vetSent)
        assertEquals(vet(699), update.of(alice).vetBalance)
        assertEquals(vet(301), update.of(bob).vetReceived)
        assertEquals(vet(301), update.of(bob).vetBalance)
        assertEquals(vet(5), update.of(carol).vetSent)
        assertEquals(vet(5), update.of(carol).vetReceived)
        assertEquals(
            listOf(
                VetBalance(alice, block.id, block.number, timestamp, vet(699)),
                VetBalance(bob, block.id, block.number, timestamp, vet(301)),
            ),
            update.balances,
        )
    }

    @Test
    fun `passive VTHO settles on the balance before the transfer, until Hayabusa`() {
        every { repository.findCurrentOverviews(any()) } returns
            listOf(stored(alice, vetBalance = vet(1000), lastVthoSettlement = timestamp - 200))
        val transfer = listOf(vetTransfer(alice, bob, vet(999)))

        val before = process(block(number = hayabusa - 1), transfer)
        assertEquals(
            vet(1000) * BigInteger.valueOf(200 * 5) / BigInteger.TEN.pow(9),
            before.of(alice).vthoPassiveGeneration,
        )
        assertEquals(timestamp, before.of(alice).lastVthoSettlement)
        assertEquals(BigInteger.ZERO, before.of(bob).vthoPassiveGeneration)
        assertEquals(timestamp, before.of(bob).lastVthoSettlement)

        val fork = process(block(number = hayabusa), transfer)
        assertEquals(before.of(alice).vthoPassiveGeneration, fork.of(alice).vthoPassiveGeneration)
        assertEquals(timestamp, fork.of(alice).lastVthoSettlement)
        assertNull(fork.of(bob).lastVthoSettlement)

        val after = process(block(number = hayabusa + 1), transfer)
        assertEquals(BigInteger.ZERO, after.of(alice).vthoPassiveGeneration)
        assertEquals(timestamp - 200, after.of(alice).lastVthoSettlement)
        assertNull(after.of(bob).lastVthoSettlement)
    }

    @Test
    fun `the beneficiary earns the VTHO its balance grew by`() {
        energy(block(), before = "0x3e8", after = "0x5dc")

        val update = process(block())

        assertEquals(BigInteger.valueOf(500), update.of(beneficiary).vthoBlockRewards)
        assertEquals(listOf(beneficiary), update.overviews.map { it.address })
    }

    @Test
    fun `VTHO transferred to or from the beneficiary is no reward`() {
        energy(block(), before = "0x3e8", after = "0x5dc")

        val update =
            process(
                block(),
                listOf(
                    vthoTransfer(alice, beneficiary, "200"),
                    vthoTransfer(beneficiary, bob, "50"),
                ),
            )

        assertEquals(BigInteger.valueOf(350), update.of(beneficiary).vthoBlockRewards)
    }

    @Test
    fun `gas the beneficiary paid is added back before measuring the reward`() {
        energy(block(), before = "0x3e8", after = "0x5dc")

        val update =
            process(block(transactions = listOf(tx(alice, gasPayer = beneficiary, paid = "0x64"))))

        assertEquals(BigInteger.valueOf(600), update.of(beneficiary).vthoBlockRewards)
        assertEquals(BigInteger.valueOf(100), update.of(beneficiary).vthoBurned)
    }

    @Test
    fun `a beneficiary already generating passive VTHO is not rewarded for it`() {
        every { repository.findCurrentOverviews(any()) } returns
            listOf(stored(beneficiary, vetBalance = vet(1000), lastVthoSettlement = timestamp - 10))
        val passive = vet(1000) * BigInteger.valueOf(10 * 5) / BigInteger.TEN.pow(9)
        for (block in listOf(block(), block(number = hayabusa + 1))) {
            energy(
                block,
                before = "0x3e8",
                after = "0x" + (passive + BigInteger.valueOf(1500)).toString(16),
                vetBefore = "0x" + vet(1000).toString(16),
            )
        }

        assertEquals(BigInteger.valueOf(500), process(block()).of(beneficiary).vthoBlockRewards)
        assertEquals(
            BigInteger.valueOf(500) + passive,
            process(block(number = hayabusa + 1)).of(beneficiary).vthoBlockRewards,
        )
    }

    @Test
    fun `a beneficiary whose balance did not grow is left untouched`() {
        energy(block(), before = "0x3e8", after = "0x3e8")

        assertTrue(process(block()).overviews.isEmpty())
    }

    @Test
    fun `the genesis block has no beneficiary to reward`() {
        assertTrue(process(block(number = 0L)).overviews.isEmpty())
        coVerify(exactly = 0) { thorClient.getAccountState(any(), any()) }
    }

    @Test
    fun `passive VTHO is 0_000432 per VET per day`() {
        assertEquals(BigInteger("50000000000"), service.passiveVtho(vet(1), 10))
        assertEquals(BigInteger("432000000000000"), service.passiveVtho(vet(1), 86_400))
        assertEquals(BigInteger.ZERO, service.passiveVtho(BigInteger.ZERO, 86_400))
        assertEquals(BigInteger.ZERO, service.passiveVthoForBlock(vet(1), hayabusa - 1, null))
        assertEquals(
            BigInteger.ZERO,
            service.passiveVthoForBlock(vet(1), hayabusa, stored(alice, lastVthoSettlement = 1L)),
        )
    }

    @Test
    fun `the fork block settles every holder it names, whether or not VET moved`() {
        every { repository.findCurrentOverviews(any()) } returns
            listOf(
                stored(alice, vetBalance = vet(1000), lastVthoSettlement = timestamp - 200),
                stored(bob, vetBalance = vet(1000)),
            )
        assertTrue(service.isHayabusaBlock(hayabusa))
        assertTrue(!service.isHayabusaBlock(hayabusa + 1))

        val update = process(block(number = hayabusa, transactions = listOf(tx(alice), tx(bob))))

        assertEquals(
            vet(1000) * BigInteger.valueOf(200 * 5) / BigInteger.TEN.pow(9),
            update.of(alice).vthoPassiveGeneration,
        )
        assertEquals(timestamp, update.of(alice).lastVthoSettlement)
        assertEquals(BigInteger.ZERO, update.of(bob).vthoPassiveGeneration)
        assertNull(update.of(bob).lastVthoSettlement)
    }
}
