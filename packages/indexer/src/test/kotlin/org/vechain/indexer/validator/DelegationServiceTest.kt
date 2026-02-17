package org.vechain.indexer.validator

import io.mockk.*
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class DelegationServiceTest {
    private val repository = mockk<DelegationRepository>()
    private val archiveService =
        mockk<ArchiveService<Delegation, DelegationArchive>>(relaxed = true)
    private val delegationPruner =
        mockk<TargetedPruner<Delegation, DelegationArchive>>(relaxed = true)

    private val validatorDelegationService = mockk<ValidatorDelegationService>()

    private lateinit var service: DelegationService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service =
            spyk(
                DelegationService(
                    repository,
                    archiveService,
                    delegationPruner,
                    validatorDelegationService,
                    stakerSC = "0xSTAKER",
                )
            )

        every { repository.findValidatorIdsByStatusNot(any()) } returns emptyList()
        every { repository.findByValidatorIn(any()) } returns emptyList()
        every { repository.findByTokenIdIn(any()) } returns emptyList()
        every { repository.findByValidatorNextCycleInAndStatusIn(any(), any()) } returns emptyList()
        every { repository.findById(any<String>()) } returns java.util.Optional.empty()

        every { validatorDelegationService.decodeValidatorSnapshots(any()) } returns emptyMap()
        every { validatorDelegationService.nextStatus(any()) } answers
            {
                val status = firstArg<Status>()
                if (status == Status.QUEUED) Status.ACTIVE else Status.EXITED
            }
    }

    private fun block(num: Long) =
        Block(
            id = "b$num",
            number = num,
            timestamp = 12345,
            parentID = "p",
            size = 1,
            gasLimit = 1,
            baseFeePerGas = "0x",
            beneficiary = "b",
            gasUsed = 1,
            totalScore = 1,
            txsRoot = "r",
            txsFeatures = 0,
            stateRoot = "s",
            receiptsRoot = "r2",
            com = false,
            signer = "s",
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
        )

    private fun event(type: String, params: Map<String, Any>, address: String = "0xcontract") =
        IndexedEvent(
            id = "evt1",
            blockId = "b1",
            blockNumber = 1,
            blockTimestamp = 100,
            txId = "tx1",
            origin = "0xOWNER",
            paid = null,
            gasUsed = null,
            gasPayer = null,
            raw = null,
            params = AbiEventParameters(returnValues = params),
            address = address,
            eventType = type,
            clauseIndex = 0,
            signature = null,
        )

    // --- processBlock orchestration ---

    @Test
    fun `processBlock returns due delegations`(): Unit = runBlocking {
        val due =
            Delegation(
                id = "d1",
                validator = "v1",
                status = Status.QUEUED,
                validatorNextCycle = 10,
                blockId = "b",
                blockNumber = 9,
                blockTimestamp = 99,
                totalRewardsClaimed = BigInteger.ZERO,
                version = 1,
                tokenId = "t",
                owner = "o",
                tokenLevel = TokenLevel.All,
                stakedAmount = "100",
                notify = false,
                txId = "tx1",
                force = false,
                validatorCycleLength = 0,
            )

        every { repository.findByValidatorNextCycleInAndStatusIn(any(), any()) } returns listOf(due)

        val (updates, archive) =
            service.processBlock(
                block(10),
                emptyList(),
                listOf(InspectionResult("0x", emptyList(), emptyList(), 0, false, "")),
            )

        assertThat(updates).hasSize(1)
        assertThat(updates.first().status).isEqualTo(Status.ACTIVE)
        assertThat(archive).isNotEmpty()
    }

    @Test
    fun `DelegationInitiated creates new delegation`(): Unit = runBlocking {
        // stub validator cycle resolution
        coEvery { validatorDelegationService.resolveCycleInfo(any(), any(), any()) } returns
            (5L to 10L)

        val ev =
            event(
                "DelegationInitiated",
                mapOf(
                    "delegationId" to "d1",
                    "validator" to "0xv1",
                    "tokenId" to "t1",
                    "levelId" to "2",
                    "amount" to "100",
                ),
            )

        val (updates, _) = service.processBlock(block(5), listOf(ev), emptyList())

        assertThat(updates.map { it.id }).contains("d1")
        assertThat(updates.first().status).isEqualTo(Status.QUEUED)
    }

    @Test
    fun `DelegationExitRequested moves to EXITING`(): Unit = runBlocking {
        val existing =
            Delegation(
                id = "d2",
                validator = "v1",
                status = Status.ACTIVE,
                validatorNextCycle = 5,
                validatorCycleLength = 5,
                blockId = "b",
                blockNumber = 1,
                blockTimestamp = 1,
                totalRewardsClaimed = BigInteger.ZERO,
                version = 1,
                tokenId = "t",
                owner = "o",
                tokenLevel = TokenLevel.All,
                stakedAmount = "100",
                notify = false,
                txId = "tx1",
                force = false,
            )

        every { repository.findByTokenIdIn(any()) } returns listOf(existing)
        every { validatorDelegationService.resolveNextCycleBlock(any(), any(), any()) } returns 10L

        val ev = event("DelegationExitRequested", mapOf("delegationId" to "d2", "tokenId" to "t"))

        val (updates, archive) = service.processBlock(block(10), listOf(ev), emptyList())

        val updated = updates.first { it.id == "d2" }
        assertThat(updated.status).isEqualTo(Status.EXITING)
        assertThat(archive.map { it.id }).contains("d2")
    }

    @Test
    fun `DelegationWithdrawn marks delegation EXITED`(): Unit = runBlocking {
        val existing =
            Delegation(
                id = "d3",
                validator = "0xV1",
                tokenId = "t1",
                tokenLevel = TokenLevel.Dawn,
                status = Status.ACTIVE,
                stakedAmount = "200",
                totalRewardsClaimed = BigInteger.ZERO,
                owner = "0xOWNER",
                blockId = "b0",
                blockNumber = 1,
                blockTimestamp = 100,
                version = 0,
                validatorNextCycle = 5,
                validatorCycleLength = 5,
                txId = "tx0",
            )

        every { repository.findByTokenIdIn(any()) } returns listOf(existing)

        val ev = event("DelegationWithdrawn", mapOf("delegationId" to "d3", "tokenId" to "t1"))

        val (updates, archive) = service.processBlock(block(2), listOf(ev), emptyList())

        expectThat(archive.map { it.id }).contains("d3")
        expectThat(updates.first().status).isEqualTo(Status.EXITED)
    }

    @Test
    fun `DelegationRewardsClaimed adds to total rewards`(): Unit = runBlocking {
        val existing =
            Delegation(
                id = "d4",
                validator = "0xV1",
                tokenId = "t1",
                tokenLevel = TokenLevel.Dawn,
                status = Status.ACTIVE,
                stakedAmount = "200",
                totalRewardsClaimed = BigInteger("50"),
                owner = "0xOWNER",
                blockId = "b0",
                blockNumber = 1,
                blockTimestamp = 100,
                version = 0,
                validatorNextCycle = 5,
                validatorCycleLength = 5,
                txId = "tx0",
            )

        every { repository.findByTokenIdIn(any()) } returns listOf(existing)

        val ev =
            event(
                "DelegationRewardsClaimed",
                mapOf("delegationId" to "d4", "amount" to "25", "tokenId" to "t1"),
            )

        val (updates, archive) = service.processBlock(block(2), listOf(ev), emptyList())

        expectThat(archive.map { it.id }).contains("d4")
        expectThat(updates.first().totalRewardsClaimed).isEqualTo(BigInteger("75"))
    }

    @Test
    fun `ValidatorExitRequested sets delegations to EXITING`(): Unit = runBlocking {
        val existing =
            Delegation(
                id = "d5",
                validator = "0xVEXIT",
                tokenId = "t1",
                tokenLevel = TokenLevel.Dawn,
                status = Status.ACTIVE,
                stakedAmount = "200",
                totalRewardsClaimed = BigInteger.ZERO,
                owner = "0xOWNER",
                blockId = "b0",
                blockNumber = 1,
                blockTimestamp = 100,
                version = 0,
                validatorNextCycle = 5,
                validatorCycleLength = 5,
                txId = "tx0",
            )

        every { repository.findByValidatorIn(any()) } returns listOf(existing)
        coEvery { validatorDelegationService.getValidatorExitBlock("0xVEXIT", any()) } returns 20L

        val ev =
            event("ValidatorExitRequested", mapOf("validator" to "0xVEXIT"), address = "0xSTAKER")

        val (updates, archive) = service.processBlock(block(2), listOf(ev), emptyList())

        expectThat(archive.map { it.id }).contains("d5")
        expectThat(updates.first().status).isEqualTo(Status.EXITING)
        expectThat(updates.first().validatorNextCycle).isEqualTo(20L)
    }
}
