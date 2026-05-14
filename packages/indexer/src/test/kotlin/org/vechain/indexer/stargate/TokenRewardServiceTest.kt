package org.vechain.indexer.stargate.rewards

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.thor.HexUtils.toHex
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.StatusV2
import org.vechain.indexer.validator.ValidatorV2
import org.vechain.indexer.validator.ValidatorV2Repository

class TokenRewardServiceTest {
    private val repository = mockk<TokenRewardRepository>(relaxed = true)
    private val mongoTemplate = mockk<MongoTemplate>(relaxed = true)
    private val inlineVersioningProperties = mockk<InlineVersioningProperties>()
    private val validatorV2Repository = mockk<ValidatorV2Repository>(relaxed = true)
    private val delegationV2Repository = mockk<DelegationRepository>(relaxed = true)
    private val thorClient = mockk<ThorClient>(relaxed = true)

    private lateinit var service: TokenRewardService

    private fun blockId(num: Long): String = toHex(num, 64)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        every { inlineVersioningProperties.minVersions } returns 20
        service =
            spyk(
                TokenRewardService(
                    repository,
                    mongoTemplate,
                    inlineVersioningProperties,
                    validatorV2Repository,
                    delegationV2Repository,
                    thorClient,
                )
            )
    }

    private fun block(num: Long, signer: String = "0xVALIDATOR") =
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
            version = 1,
        )

    private fun validatorV2(
        address: String,
        cycleLength: Long = 1,
        startBlock: Long = 0,
        completed: Long = 0,
        delegatorStake: BigDecimal = BigDecimal.ONE,
    ): ValidatorV2 =
        ValidatorV2(
            id = address,
            blockId = "0xBLOCK",
            blockNumber = 100,
            blockTimestamp = 0,
            status = StatusV2.ACTIVE,
            cyclePeriodLength = cycleLength,
            startBlock = startBlock,
            completedPeriods = completed,
            delegatorVetStaked = delegatorStake,
        )

    /** Reflectively seeds the private `vthoTotalSupply` cache. */
    private fun seedVthoTotalSupply(value: BigInteger) {
        val field = TokenRewardService::class.java.getDeclaredField("vthoTotalSupply")
        field.isAccessible = true
        field.set(service, value)
    }

    @Test
    fun `getDelegatorsBlockReward computes 70 percent share`() {
        val b = block(10)
        // Pre-seed cached supply so the cold-start path doesn't kick in.
        seedVthoTotalSupply(BigInteger.valueOf(500))

        val result = runBlocking { service.getDelegatorsBlockReward(b, BigInteger.valueOf(1000)) }

        // (block=1000, prev=500, delta=500) * 0.7 = 350
        assertThat(result).isEqualTo(BigInteger.valueOf(350))
    }

    @Test
    fun `updateRewardInfo distributes rewards proportionally`() {
        val validator = "0x00000000000000000000000000000000000000a1"

        service.updateValidatorCycleCache(validatorV2(validator))
        service.validatorCycleCache[validator]!!.totalEffectiveDelegations = BigInteger.ONE

        val tr = tokenReward(validator, "10001", stake = BigInteger.ONE)

        val (updated, archive) =
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
        assertThat(archive).containsExactly(tr) // previous doc archived
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

        val (updated, _) =
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

        val delegation =
            Delegation(
                id = "del-1",
                validator = validator,
                tokenId = "10001",
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
