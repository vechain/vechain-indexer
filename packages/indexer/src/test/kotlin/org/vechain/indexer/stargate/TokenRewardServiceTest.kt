package org.vechain.indexer.stargate.rewards

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardWriteRepository
import org.vechain.indexer.thor.HexUtils.toHex
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationReadRepository
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorReadRepository

class TokenRewardServiceTest {
    private companion object {
        const val STAKER = "0x00000000000000000000000000005374616b6572"
        const val VALIDATOR = "0x00000000000000000000000000000000000000a1"
    }

    private val repository = mockk<TokenRewardWriteRepository>(relaxed = true)
    private val validatorV2Repository = mockk<ValidatorReadRepository>(relaxed = true)
    private val delegationV2Repository = mockk<DelegationReadRepository>(relaxed = true)
    private val thorClient = mockk<ThorClient>(relaxed = true)

    private lateinit var service: TokenRewardService

    private fun blockId(num: Long): String = toHex(num, 64)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service =
            spyk(
                TokenRewardService(
                    repository,
                    validatorV2Repository,
                    delegationV2Repository,
                    thorClient,
                    stakerAddress = STAKER,
                    validatorStartBlock = 0L,
                )
            )
    }

    private fun block(num: Long, signer: String = VALIDATOR) =
        Block(
            id = blockId(num),
            number = num,
            timestamp = 1234567890,
            parentID = blockId(num - 1),
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
            signer = signer,
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
            com = false,
        )

    private fun tokenReward(
        validator: String,
        tokenId: String,
        stake: BigInteger = BigInteger.TEN,
        cycle: Long = 1,
    ) =
        TokenReward(
            id = "$validator-$tokenId",
            blockId = "0xBLOCK",
            blockNumber = 0,
            blockTimestamp = 0,
            tokenId = tokenId,
            cycle = cycle,
            validator = validator,
            rewards = BigInteger.ZERO,
            effectiveStake = stake,
            rewardPeriod = RewardPeriod.ALL,
            dayOfMonth = 1,
            weekOfYear = 1,
            month = 1,
            year = 2025,
        )

    private fun validatorV2(
        address: String,
        cycleLength: Long = 1,
        startBlock: Long = 0,
        completed: Long = 0,
        delegatorStake: BigDecimal = BigDecimal.ONE,
    ): Validator =
        Validator(
            id = address,
            blockId = "0xBLOCK",
            blockNumber = 100,
            blockTimestamp = 0,
            status = Status.ACTIVE,
            cyclePeriodLength = cycleLength,
            startBlock = startBlock,
            completedPeriods = completed,
            delegatorVetStaked = delegatorStake,
        )

    private fun pool(value: Long) =
        InspectionResult(
            data = "0x" + BigInteger.valueOf(value).toString(16).padStart(64, '0'),
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0,
            reverted = false,
            vmError = null,
        )

    /** `getDelegatorsRewards` at [blockId] for the three cycle keys the service reads, in order. */
    private fun poolsAt(blockId: String, vararg pools: Long) {
        coEvery { thorClient.inspectClauses(any(), BlockRevision.Id(blockId)) } returns
            pools.map(::pool)
    }

    private fun reward(num: Long, cycle: Long = 2) = runBlocking {
        service.delegatorBlockReward(block(num), VALIDATOR, cycle)
    }

    @Test
    fun `the block reward is the growth of the signer's on-chain delegator pool`() {
        poolsAt(blockId(9), 0, 600, 0)
        poolsAt(blockId(10), 0, 1000, 0)

        assertThat(reward(10)).isEqualTo(BigInteger.valueOf(400))
    }

    @Test
    fun `the previous read is the next baseline, so the parent is fetched once`() {
        poolsAt(blockId(9), 0, 600, 0)
        poolsAt(blockId(10), 0, 1000, 0)
        poolsAt(blockId(11), 0, 1500, 0)

        reward(10)
        assertThat(reward(11)).isEqualTo(BigInteger.valueOf(500))

        coVerify(exactly = 1) { thorClient.inspectClauses(any(), BlockRevision.Id(blockId(9))) }
        coVerify(exactly = 1) { thorClient.inspectClauses(any(), BlockRevision.Id(blockId(10))) }
    }

    @Test
    fun `invalidateCache drops the baseline and the parent is read again`() {
        poolsAt(blockId(9), 0, 600, 0)
        poolsAt(blockId(10), 0, 1000, 0)
        poolsAt(blockId(11), 0, 1500, 0)

        reward(10)
        service.invalidateCache()
        assertThat(reward(11)).isEqualTo(BigInteger.valueOf(500))

        coVerify(exactly = 2) { thorClient.inspectClauses(any(), BlockRevision.Id(blockId(10))) }
    }

    @Test
    fun `a cycle the chain still keys one behind is credited from that key`() {
        poolsAt(blockId(9), 0, 1000, 0)
        poolsAt(blockId(10), 0, 1000, 0)
        reward(10)
        // Indexer moves to cycle 3, keys 2..4; the chain kept writing to key 2.
        poolsAt(blockId(10), 1000, 0, 0)
        poolsAt(blockId(11), 1040, 0, 0)

        assertThat(reward(11, cycle = 3)).isEqualTo(BigInteger.valueOf(40))
    }

    @Test
    fun `a shrinking pool fails fast instead of writing a negative reward`() {
        poolsAt(blockId(9), 0, 600, 0)
        poolsAt(blockId(10), 0, 500, 0)

        assertThatThrownBy { reward(10) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a signer without delegations writes nothing and the next signer gets only its own growth`() {
        val idle = "0x00000000000000000000000000000000000000b2"
        every { validatorV2Repository.findById(idle) } returns
            validatorV2(idle, completed = 1, delegatorStake = BigDecimal.ZERO)
        every { validatorV2Repository.findById(VALIDATOR) } returns
            validatorV2(VALIDATOR, completed = 1)
        every {
            delegationV2Repository.findByValidatorAndStatusIn(
                VALIDATOR,
                listOf(DelegationStatus.ACTIVE, DelegationStatus.EXITING),
            )
        } returns listOf(delegation(VALIDATOR, "10001"))
        every { repository.findAllById(any<List<String>>()) } returns emptyList()
        poolsAt(blockId(9), 0, 600, 0)
        poolsAt(blockId(10), 0, 1000, 0)

        val idleResult = runBlocking { service.processBlock(block(9, signer = idle)) }
        val result = runBlocking { service.processBlock(block(10)) }

        assertThat(idleResult).isEmpty()
        coVerify(exactly = 0) { thorClient.inspectClauses(any(), BlockRevision.Id(blockId(8))) }
        assertThat(result.single { it.rewardPeriod == RewardPeriod.ALL }.rewards)
            .isEqualTo(BigInteger.valueOf(400))
    }

    private fun delegation(validator: String, tokenId: String) =
        Delegation(
            id = "del-$tokenId",
            validator = validator,
            tokenId = tokenId,
            owner = "0xOWNER",
            status = DelegationStatus.ACTIVE,
            tokenLevel = TokenLevel.Dawn,
            stakedAmount = "10000",
            totalRewardsClaimed = BigInteger.ZERO,
            txId = "0xTX",
            blockId = "0xBLOCK",
            blockNumber = 100,
            blockTimestamp = 0,
        )

    @Test
    fun `updateRewardInfo distributes rewards proportionally`() {
        val validator = "0x00000000000000000000000000000000000000a1"

        service.updateValidatorCycleCache(validatorV2(validator))
        service.validatorCycleCache[validator]!!.totalEffectiveDelegations = BigInteger.ONE

        val tr = tokenReward(validator, "10001", stake = BigInteger.ONE)

        val updated =
            service.updateRewardInfo(
                listOf(tr),
                totalBlockReward = BigInteger.TEN,
                validator = validator,
                blockNumber = 123,
                blockTimestamp = Instant.now().epochSecond,
                blockId = "0xBLOCK",
            )

        val updatedDoc = updated.single()
        assertThat(updatedDoc.rewards).isEqualTo(BigInteger.TEN) // 10 * 1/1 = 10
    }

    @Test
    fun `updateRewardInfo stamps current block metadata on rollover records`() {
        val validator = "0x00000000000000000000000000000000000000a1"

        service.updateValidatorCycleCache(validatorV2(validator))
        service.validatorCycleCache[validator]!!.totalEffectiveDelegations = BigInteger.ONE

        val rewardTracker =
            tokenReward(validator, "10001", stake = BigInteger.ONE)
                .copy(
                    blockId = "0xold",
                    blockNumber = 99L,
                    blockTimestamp = 1234481490L,
                    dayOfMonth = 1,
                    month = 1,
                    year = 2025,
                    dayReward = BigInteger("3"),
                )
        val blockTimestamp = Instant.parse("2025-01-02T00:00:00Z").epochSecond

        val updated =
            service.updateRewardInfo(
                listOf(rewardTracker),
                totalBlockReward = BigInteger.TEN,
                validator = validator,
                blockNumber = 123L,
                blockTimestamp = blockTimestamp,
                blockId = "0xnew",
            )

        val dayRewardRecord = updated.first { it.rewardPeriod == RewardPeriod.DAY }
        assertThat(dayRewardRecord.blockId).isEqualTo("0xnew")
        assertThat(dayRewardRecord.blockNumber).isEqualTo(123L)
        assertThat(dayRewardRecord.blockTimestamp).isEqualTo(blockTimestamp)
    }

    @Test
    fun `getOrFetchRewardsNewCycle creates new docs for missing delegations`() {
        val validator = "0x00000000000000000000000000000000000000a1"

        val delegation = delegation(validator, "10001")

        every {
            delegationV2Repository.findByValidatorAndStatusIn(
                validator,
                listOf(DelegationStatus.ACTIVE, DelegationStatus.EXITING),
            )
        } returns listOf(delegation)

        every { repository.findAllById(any<List<String>>()) } returns emptyList()

        service.updateValidatorCycleCache(validatorV2(validator))

        val result =
            service.getOrFetchRewardsNewCycle(
                validator,
                block(num = 200),
                Instant.now().atZone(ZoneOffset.UTC).toLocalDate(),
            )

        // Dawn level effectiveStake (in VET) converted to wei (× 10^18).
        val expectedStakeWei =
            TokenLevel.Dawn.effectiveStake.multiply(BigDecimal.TEN.pow(18)).toBigInteger()
        assertThat(result).hasSize(1)
        assertThat(result.first().effectiveStake).isEqualTo(expectedStakeWei)
    }
}
