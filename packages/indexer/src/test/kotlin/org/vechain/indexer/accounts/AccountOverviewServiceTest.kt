package org.vechain.indexer.accounts

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.DetectedNetwork
import org.vechain.indexer.config.ForkConfig
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ExecuteAccountResponse
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.BlockUnexpanded
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction

@ExtendWith(MockKExtension::class)
internal class AccountOverviewServiceTest {
    @MockK lateinit var repository: AccountOverviewRepository

    @MockK lateinit var archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>

    @MockK lateinit var pruner: TargetedPruner<AccountOverview, AccountOverviewArchive>

    @MockK lateinit var forkConfig: ForkConfig

    @MockK lateinit var networkDetectionService: NetworkDetectionService

    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: TestableService

    private class TestableService(
        repository: AccountOverviewRepository,
        archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>,
        pruner: TargetedPruner<AccountOverview, AccountOverviewArchive>,
        forkConfig: ForkConfig,
        networkDetectionService: NetworkDetectionService,
        thorClient: ThorClient,
    ) :
        AccountOverviewService(
            repository,
            archiveService,
            pruner,
            forkConfig,
            networkDetectionService,
            thorClient,
        ) {
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

        suspend fun callVthoBlockRewardsRule(
            block: Block,
            events: List<IndexedEvent>,
            updatedResult: MutableMap<String, AccountOverview>,
            archiveResult: MutableMap<String, AccountOverview>,
        ) = vthoBlockRewardsRule(block, events, updatedResult, archiveResult)

        fun callCalculatePassiveVthoForBlock(
            vetBalance: BigInteger,
            blockNumber: Long,
            beneficiaryAccount: AccountOverview?,
        ) = calculatePassiveVthoForBlock(vetBalance, blockNumber, beneficiaryAccount)

        fun callCalculatePassiveVtho(vetBalance: BigInteger, durationSeconds: BigInteger) =
            calculatePassiveVtho(vetBalance, durationSeconds)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            TestableService(
                repository,
                archiveService,
                pruner,
                forkConfig,
                networkDetectionService,
                thorClient,
            )
    }

    private fun block(number: Long = 1L, transactions: List<Transaction> = emptyList()) =
        Block(
            id = "0x" + "0".repeat(63) + "1",
            number = number,
            timestamp = 1234567890,
            parentID = "0x" + "0".repeat(63) + "0",
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
            blockId = "0xOLD_BLOCK",
            blockNumber = 10L,
            blockTimestamp = 100L,
            version = version,
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
        assertEquals(0, created.version)
        assertEquals(b.timestamp, created.firstSeen)
        assertEquals(b.timestamp, created.lastSeen)
        assertEquals(0L, created.transactionsSent)
        assertEquals(0L, created.clausesSent)
        assertEquals(BigInteger.ZERO, created.vthoBurned)
        assertEquals(BigInteger.ZERO, created.vthoDelegated)
        assertEquals(BigInteger.ZERO, created.gasUsed)
        assertEquals(BigInteger.ZERO, created.vetSent)
        assertEquals(BigInteger.ZERO, created.vetReceived)
        assertEquals(BigInteger.ZERO, created.vthoBlockRewards)
        assertEquals(BigInteger.ZERO, created.vthoPassiveGeneration)
    }

    @Test
    fun `transactionsSentRule increments transactions and clauses per origin`() {
        val originA = "0xA"
        val originB = "0xB"
        val existingA =
            existingAccountOverview(originA, version = 3)
                .copy(transactionsSent = 5L, clausesSent = 7L)

        every { repository.findByIdOrNull(originA) } returns existingA
        every { repository.findByIdOrNull(originB) } returns null

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
        assertEquals(0, updatedB.version)
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

        every { repository.findByIdOrNull(payerA) } returns existingA
        every { repository.findByIdOrNull(payerB) } returns null

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
        every { repository.findByIdOrNull(payer) } returns existing

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

        every { repository.findByIdOrNull(originA) } returns existingA
        every { repository.findByIdOrNull(originB) } returns null

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

        every { repository.findByIdOrNull(fromA) } returns existingA
        every { repository.findByIdOrNull(fromB) } returns null

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

        every { repository.findByIdOrNull(toA) } returns existingA
        every { repository.findByIdOrNull(toB) } returns null

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
        every { repository.findByIdOrNull(recordId) } returns existing

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
        every { repository.findByIdOrNull(recordId) } returns null

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        val resolved =
            service.callResolveAccountOverviewForUpdateAndArchive(recordId, b, updated, archived)

        assertEquals(recordId, resolved.address)
        assertEquals(0, resolved.version)
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
        every { repository.findByIdOrNull(recordId) } returns existing

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

    // Helper for creating VTHO Transfer events
    private fun vthoTransferEvent(from: String, to: String, value: String): IndexedEvent =
        buildIndexedEvent(
            eventType = "Transfer",
            address = VTHO_CONTRACT_ADDRESS,
            params =
                AbiEventParameters(
                    returnValues = mapOf("from" to from, "to" to to, "value" to value)
                ),
        )

    // Helper for creating parent block mock
    private fun parentBlockUnexpanded(timestamp: Long = 1234567880L): BlockUnexpanded =
        BlockUnexpanded(
            id = "0x" + "0".repeat(63) + "0",
            number = 99L,
            timestamp = timestamp,
            parentID = "0x" + "0".repeat(64),
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
            transactions = emptyList(),
            com = false,
        )

    // Helper for mocking network detection and fork config
    private fun mockNetworkDetection(hayabusaBlock: Long = 1000L) {
        val mockBlock = mockk<Block>(relaxed = true)
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(network = VeChainNetwork.MAINNET, genesisBlock = mockBlock)
        every { forkConfig.getHayabusaBlock(VeChainNetwork.MAINNET) } returns hayabusaBlock
    }

    @Test
    fun `vthoBlockRewardsRule calculates reward from balance difference`() = runBlocking {
        val beneficiary = "0xBENEFICIARY"
        val b = block(number = 100L)
        val parentRevision = BlockRevision.Id(b.parentID)
        val blockRevision = BlockRevision.Id(b.id)

        mockNetworkDetection(hayabusaBlock = 1000L)
        every { repository.findByIdOrNull(beneficiary) } returns null
        coEvery { thorClient.getBlockUnexpanded(parentRevision) } returns parentBlockUnexpanded()

        // Mock VTHO balances: 1000 at block n-1, 1500 at block n
        // No AccountOverview, so no passive generation
        // Reward should be 500
        coEvery { thorClient.getAccountState(beneficiary, parentRevision) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x3e8", hasCode = false) // 1000
        coEvery { thorClient.getAccountState(beneficiary, blockRevision) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x5dc", hasCode = false) // 1500

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVthoBlockRewardsRule(b, emptyList(), updated, archived)

        val record = updated[beneficiary]!!
        assertEquals(BigInteger("500"), record.vthoBlockRewards)
    }

    @Test
    fun `vthoBlockRewardsRule applies zero reward when no balance change`() = runBlocking {
        val beneficiary = "0xBENEFICIARY"
        val b = block(number = 100L)
        val parentRevision = BlockRevision.Id(b.parentID)
        val blockRevision = BlockRevision.Id(b.id)

        mockNetworkDetection(hayabusaBlock = 1000L)
        every { repository.findByIdOrNull(beneficiary) } returns null
        coEvery { thorClient.getBlockUnexpanded(parentRevision) } returns parentBlockUnexpanded()

        // Same balance at both blocks -> no reward
        coEvery { thorClient.getAccountState(beneficiary, parentRevision) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x3e8", hasCode = false) // 1000
        coEvery { thorClient.getAccountState(beneficiary, blockRevision) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x3e8", hasCode = false) // 1000

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVthoBlockRewardsRule(b, emptyList(), updated, archived)

        assertTrue(updated.isEmpty())
    }

    @Test
    fun `vthoBlockRewardsRule subtracts VTHO transfers to beneficiary`() = runBlocking {
        val beneficiary = "0xBENEFICIARY"
        val b = block(number = 100L)
        val parentRevision = BlockRevision.Id(b.parentID)
        val blockRevision = BlockRevision.Id(b.id)

        mockNetworkDetection(hayabusaBlock = 1000L)
        every { repository.findByIdOrNull(beneficiary) } returns null
        coEvery { thorClient.getBlockUnexpanded(parentRevision) } returns parentBlockUnexpanded()

        // Balance at n-1: 1000, Balance at n: 1500
        // Beneficiary received 300 VTHO in transfer
        // Adjusted balance = 1500 - 300 = 1200
        // Reward = 1200 - 1000 = 200
        coEvery { thorClient.getAccountState(beneficiary, parentRevision) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x3e8", hasCode = false) // 1000
        coEvery { thorClient.getAccountState(beneficiary, blockRevision) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x5dc", hasCode = false) // 1500

        val events = listOf(vthoTransferEvent(from = "0xOTHER", to = beneficiary, value = "300"))

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVthoBlockRewardsRule(b, events, updated, archived)

        val record = updated[beneficiary]!!
        assertEquals(BigInteger("200"), record.vthoBlockRewards)
    }

    @Test
    fun `vthoBlockRewardsRule adds VTHO transfers from beneficiary`() = runBlocking {
        val beneficiary = "0xBENEFICIARY"
        val b = block(number = 100L)
        val parentRevision = BlockRevision.Id(b.parentID)
        val blockRevision = BlockRevision.Id(b.id)

        mockNetworkDetection(hayabusaBlock = 1000L)
        every { repository.findByIdOrNull(beneficiary) } returns null
        coEvery { thorClient.getBlockUnexpanded(parentRevision) } returns parentBlockUnexpanded()

        // Balance at n-1: 1000, Balance at n: 800
        // Beneficiary sent 500 VTHO in transfer
        // Adjusted balance = 800 - (-500) = 1300
        // Reward = 1300 - 1000 = 300
        coEvery { thorClient.getAccountState(beneficiary, parentRevision) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x3e8", hasCode = false) // 1000
        coEvery { thorClient.getAccountState(beneficiary, blockRevision) } returns
            ExecuteAccountResponse(balance = "0x0", energy = "0x320", hasCode = false) // 800

        val events = listOf(vthoTransferEvent(from = beneficiary, to = "0xOTHER", value = "500"))

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVthoBlockRewardsRule(b, events, updated, archived)

        val record = updated[beneficiary]!!
        assertEquals(BigInteger("300"), record.vthoBlockRewards)
    }

    @Test
    fun `vthoBlockRewardsRule accumulates rewards to existing account without passive generation`() =
        runBlocking {
            val beneficiary = "0xBENEFICIARY"
            val existingRewards = BigInteger("1000")
            // Account exists but lastVthoSettlement is null -> no passive generation
            val existingAccount =
                existingAccountOverview(beneficiary, version = 3)
                    .copy(vthoBlockRewards = existingRewards, lastVthoSettlement = null)
            val b = block(number = 100L)
            val parentRevision = BlockRevision.Id(b.parentID)
            val blockRevision = BlockRevision.Id(b.id)

            mockNetworkDetection(hayabusaBlock = 1000L)
            every { repository.findByIdOrNull(beneficiary) } returns existingAccount
            coEvery { thorClient.getBlockUnexpanded(parentRevision) } returns
                parentBlockUnexpanded()

            // Mock VTHO balances: 500 at block n-1, 700 at block n
            // No passive generation (lastVthoSettlement is null)
            // Reward = 200
            coEvery { thorClient.getAccountState(beneficiary, parentRevision) } returns
                ExecuteAccountResponse(balance = "0x0", energy = "0x1f4", hasCode = false) // 500
            coEvery { thorClient.getAccountState(beneficiary, blockRevision) } returns
                ExecuteAccountResponse(balance = "0x0", energy = "0x2bc", hasCode = false) // 700

            val updated = mutableMapOf<String, AccountOverview>()
            val archived = mutableMapOf<String, AccountOverview>()

            service.callVthoBlockRewardsRule(b, emptyList(), updated, archived)

            val record = updated[beneficiary]!!
            assertEquals(BigInteger("1200"), record.vthoBlockRewards) // 1000 + 200
            assertSame(existingAccount, archived[beneficiary])
        }

    @Test
    fun `vthoBlockRewardsRule includes passive generation for pre-Hayabusa with lastVthoSettlement`() =
        runBlocking {
            val beneficiary = "0xBENEFICIARY"
            // Account with lastVthoSettlement set -> passive generation applies
            val existingAccount =
                existingAccountOverview(beneficiary, version = 3)
                    .copy(lastVthoSettlement = 1234567800L)
            val b = block(number = 100L) // timestamp = 1234567890
            val parentRevision = BlockRevision.Id(b.parentID)
            val blockRevision = BlockRevision.Id(b.id)

            mockNetworkDetection(hayabusaBlock = 1000L) // Pre-Hayabusa
            every { repository.findByIdOrNull(beneficiary) } returns existingAccount
            // Parent block timestamp = 1234567880 (10 seconds before block n)
            coEvery { thorClient.getBlockUnexpanded(parentRevision) } returns
                parentBlockUnexpanded(timestamp = 1234567880L)

            // VET balance at n-1: 1_000_000_000_000_000_000 (1e18 = 1 VET)
            // VTHO balance at n-1: 1000, VTHO balance at n: 1600
            // Passive VTHO = 1e18 * 10 * 5 / 1e9 = 50_000_000_000 (50e9)
            // Btrue = 1000 + 50_000_000_000 = 50_000_001_000
            // Reward = 1600 - Btrue = 1600 - 50_000_001_000 = -49_999_999_400 (negative -> no reward)
            coEvery { thorClient.getAccountState(beneficiary, parentRevision) } returns
                ExecuteAccountResponse(
                    balance = "0xde0b6b3a7640000", // 1e18 (1 VET)
                    energy = "0x3e8", // 1000
                    hasCode = false,
                )
            coEvery { thorClient.getAccountState(beneficiary, blockRevision) } returns
                ExecuteAccountResponse(
                    balance = "0x0",
                    energy = "0x640", // 1600
                    hasCode = false,
                )

            val updated = mutableMapOf<String, AccountOverview>()
            val archived = mutableMapOf<String, AccountOverview>()

            service.callVthoBlockRewardsRule(b, emptyList(), updated, archived)

            // Reward should be negative (passive generation > balance increase), so no update
            assertTrue(updated.isEmpty())
        }

    @Test
    fun `vthoBlockRewardsRule calculates correct reward with passive generation`() = runBlocking {
        val beneficiary = "0xBENEFICIARY"
        // Account with lastVthoSettlement set -> passive generation applies
        val existingAccount =
            existingAccountOverview(beneficiary, version = 3).copy(lastVthoSettlement = 1234567800L)
        val b = block(number = 100L) // timestamp = 1234567890
        val parentRevision = BlockRevision.Id(b.parentID)
        val blockRevision = BlockRevision.Id(b.id)

        mockNetworkDetection(hayabusaBlock = 1000L) // Pre-Hayabusa
        every { repository.findByIdOrNull(beneficiary) } returns existingAccount
        // Parent block timestamp = 1234567880 (10 seconds before block n)
        coEvery { thorClient.getBlockUnexpanded(parentRevision) } returns
            parentBlockUnexpanded(timestamp = 1234567880L)

        // VET balance at n-1: 200_000_000_000 (200e9 = 0.0000002 VET, small to keep passive small)
        // VTHO balance at n-1: 1000, VTHO balance at n: 1510
        // Passive VTHO = 200e9 * 10 * 5 / 1e9 = 10_000 (10e3)
        // Btrue = 1000 + 10_000 = 11_000
        // Badj = 1510 - 0 (no transfers) = 1510
        // Reward = 1510 - 11000 = negative -> no reward... let's adjust

        // Let's use bigger numbers to get a positive reward:
        // VET balance at n-1: 200_000_000_000 (200e9)
        // VTHO balance at n-1: 1000, VTHO balance at n: 12000
        // Passive VTHO = 200e9 * 10 * 5 / 1e9 = 10_000
        // Btrue = 1000 + 10_000 = 11_000
        // Badj = 12000
        // Reward = 12000 - 11000 = 1000
        coEvery { thorClient.getAccountState(beneficiary, parentRevision) } returns
            ExecuteAccountResponse(
                balance = "0x2e90edd000", // 200_000_000_000 (200e9)
                energy = "0x3e8", // 1000
                hasCode = false,
            )
        coEvery { thorClient.getAccountState(beneficiary, blockRevision) } returns
            ExecuteAccountResponse(
                balance = "0x0",
                energy = "0x2ee0", // 12000
                hasCode = false,
            )

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVthoBlockRewardsRule(b, emptyList(), updated, archived)

        val record = updated[beneficiary]!!
        assertEquals(BigInteger("1000"), record.vthoBlockRewards)
    }

    @Test
    fun `vthoBlockRewardsRule skips passive generation post-Hayabusa`() = runBlocking {
        val beneficiary = "0xBENEFICIARY"
        // Account with lastVthoSettlement set, but post-Hayabusa -> no passive generation
        val existingAccount =
            existingAccountOverview(beneficiary, version = 3).copy(lastVthoSettlement = 1234567800L)
        val b = block(number = 1500L) // Post-Hayabusa (hayabusaBlock = 1000)
        val parentRevision = BlockRevision.Id(b.parentID)
        val blockRevision = BlockRevision.Id(b.id)

        mockNetworkDetection(hayabusaBlock = 1000L) // Post-Hayabusa (block 1500 >= 1000)
        every { repository.findByIdOrNull(beneficiary) } returns existingAccount
        coEvery { thorClient.getBlockUnexpanded(parentRevision) } returns
            parentBlockUnexpanded(timestamp = 1234567880L)

        // Same setup as passive generation test, but post-Hayabusa
        // VET balance at n-1: 200_000_000_000 (200e9)
        // VTHO balance at n-1: 1000, VTHO balance at n: 1600
        // Post-Hayabusa: no passive generation
        // Btrue = 1000
        // Reward = 1600 - 1000 = 600
        coEvery { thorClient.getAccountState(beneficiary, parentRevision) } returns
            ExecuteAccountResponse(
                balance = "0x2e90edd000", // 200_000_000_000 (200e9)
                energy = "0x3e8", // 1000
                hasCode = false,
            )
        coEvery { thorClient.getAccountState(beneficiary, blockRevision) } returns
            ExecuteAccountResponse(
                balance = "0x0",
                energy = "0x640", // 1600
                hasCode = false,
            )

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        service.callVthoBlockRewardsRule(b, emptyList(), updated, archived)

        val record = updated[beneficiary]!!
        assertEquals(BigInteger("600"), record.vthoBlockRewards)
    }

    // Tests for calculatePassiveVthoForBlock

    @Test
    fun `calculatePassiveVthoForBlock returns correct value pre-Hayabusa with lastVthoSettlement`() {
        mockNetworkDetection(hayabusaBlock = 1000L)

        val account =
            existingAccountOverview("0xTEST", version = 1).copy(lastVthoSettlement = 1234567800L)

        // VET balance: 1_000_000_000_000_000_000 (1e18 = 1 VET)
        // Passive = 1e18 * 10 * 5 / 1e9 = 50_000_000_000 (50e9)
        val vetBalance = BigInteger("1000000000000000000")
        val result = service.callCalculatePassiveVthoForBlock(vetBalance, 100L, account)

        assertEquals(BigInteger("50000000000"), result)
    }

    @Test
    fun `calculatePassiveVthoForBlock returns zero pre-Hayabusa with null lastVthoSettlement`() {
        mockNetworkDetection(hayabusaBlock = 1000L)

        val account = existingAccountOverview("0xTEST", version = 1).copy(lastVthoSettlement = null)

        val vetBalance = BigInteger("1000000000000000000")
        val result = service.callCalculatePassiveVthoForBlock(vetBalance, 100L, account)

        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `calculatePassiveVthoForBlock returns zero pre-Hayabusa with no AccountOverview`() {
        mockNetworkDetection(hayabusaBlock = 1000L)

        val vetBalance = BigInteger("1000000000000000000")
        val result = service.callCalculatePassiveVthoForBlock(vetBalance, 100L, null)

        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `calculatePassiveVthoForBlock returns zero post-Hayabusa with lastVthoSettlement`() {
        mockNetworkDetection(hayabusaBlock = 1000L)

        val account =
            existingAccountOverview("0xTEST", version = 1).copy(lastVthoSettlement = 1234567800L)

        val vetBalance = BigInteger("1000000000000000000")
        // Block 1500 >= Hayabusa block 1000, so post-Hayabusa
        val result = service.callCalculatePassiveVthoForBlock(vetBalance, 1500L, account)

        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `calculatePassiveVthoForBlock returns zero post-Hayabusa with no AccountOverview`() {
        mockNetworkDetection(hayabusaBlock = 1000L)

        val vetBalance = BigInteger("1000000000000000000")
        val result = service.callCalculatePassiveVthoForBlock(vetBalance, 1500L, null)

        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `calculatePassiveVthoForBlock scales correctly with VET balance`() {
        mockNetworkDetection(hayabusaBlock = 1000L)

        val account =
            existingAccountOverview("0xTEST", version = 1).copy(lastVthoSettlement = 1234567800L)

        // VET balance: 200_000_000_000 (200e9)
        // Passive = 200e9 * 10 * 5 / 1e9 = 10_000
        val vetBalance = BigInteger("200000000000")
        val result = service.callCalculatePassiveVthoForBlock(vetBalance, 100L, account)

        assertEquals(BigInteger("10000"), result)
    }

    @Test
    fun `calculatePassiveVthoForBlock returns zero for zero VET balance`() {
        mockNetworkDetection(hayabusaBlock = 1000L)

        val account =
            existingAccountOverview("0xTEST", version = 1).copy(lastVthoSettlement = 1234567800L)

        val result = service.callCalculatePassiveVthoForBlock(BigInteger.ZERO, 100L, account)

        assertEquals(BigInteger.ZERO, result)
    }

    // Tests for calculatePassiveVtho (core calculation function)

    @Test
    fun `calculatePassiveVtho returns correct value for 1 VET over 10 seconds`() {
        // 1 VET = 1e18 wei
        // Passive = 1e18 * 10 * 5 / 1e9 = 50_000_000_000 (50e9)
        val vetBalance = BigInteger("1000000000000000000")
        val durationSeconds = BigInteger.TEN

        val result = service.callCalculatePassiveVtho(vetBalance, durationSeconds)

        assertEquals(BigInteger("50000000000"), result)
    }

    @Test
    fun `calculatePassiveVtho returns correct value for 1 day`() {
        // 1 VET = 1e18 wei
        // 1 day = 86400 seconds
        // Passive = 1e18 * 86400 * 5 / 1e9 = 432_000_000_000_000 (0.000432 VTHO as expected)
        val vetBalance = BigInteger("1000000000000000000")
        val durationSeconds = BigInteger.valueOf(86400)

        val result = service.callCalculatePassiveVtho(vetBalance, durationSeconds)

        assertEquals(BigInteger("432000000000000"), result)
    }

    @Test
    fun `calculatePassiveVtho returns zero for zero VET balance`() {
        val result = service.callCalculatePassiveVtho(BigInteger.ZERO, BigInteger.TEN)

        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `calculatePassiveVtho returns zero for zero duration`() {
        val vetBalance = BigInteger("1000000000000000000")

        val result = service.callCalculatePassiveVtho(vetBalance, BigInteger.ZERO)

        assertEquals(BigInteger.ZERO, result)
    }

    @Test
    fun `calculatePassiveVtho scales linearly with VET balance`() {
        val durationSeconds = BigInteger.TEN

        // 1 VET
        val result1Vet =
            service.callCalculatePassiveVtho(BigInteger("1000000000000000000"), durationSeconds)
        // 10 VET
        val result10Vet =
            service.callCalculatePassiveVtho(BigInteger("10000000000000000000"), durationSeconds)

        assertEquals(result1Vet.multiply(BigInteger.TEN), result10Vet)
    }

    @Test
    fun `calculatePassiveVtho scales linearly with duration`() {
        val vetBalance = BigInteger("1000000000000000000")

        // 10 seconds
        val result10Sec = service.callCalculatePassiveVtho(vetBalance, BigInteger.TEN)
        // 100 seconds
        val result100Sec = service.callCalculatePassiveVtho(vetBalance, BigInteger.valueOf(100))

        assertEquals(result10Sec.multiply(BigInteger.TEN), result100Sec)
    }
}
