package org.vechain.indexer.service

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.vechain.indexer.stargate.StargateService
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockRepository
import org.vechain.indexer.stargate.token.StargateTokenRepository
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockRepository
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockRepository
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty

class StargateServiceTest {
    private val vthoClaimedByBlockRepository: VthoClaimedByBlockRepository = mockk()
    private val vthoClaimedByAccountRepository: VthoClaimedByAccountRepository = mockk()
    private val nftHoldersByBlockRepository: NftHoldersByBlockRepository = mockk()
    private val vetStakedByBlockRepository: VetStakedByBlockRepository = mockk()
    private val vthoGeneratedByBlockRepository: VthoGeneratedByBlockRepository = mockk()
    private val vetDelegatedByBlockRepository: VetDelegatedByBlockRepository = mockk()
    private val stargateTokenRepository: StargateTokenRepository = mockk()
    private val tokenRewardRepository: TokenRewardRepository = mockk()

    private val service =
        StargateService(
            vthoClaimedByBlockRepository = vthoClaimedByBlockRepository,
            vthoClaimedByAccountRepository = vthoClaimedByAccountRepository,
            nftHoldersByBlockRepository = nftHoldersByBlockRepository,
            vetStakedByBlockRepository = vetStakedByBlockRepository,
            vthoGeneratedByBlockRepository = vthoGeneratedByBlockRepository,
            vetDelegatedByBlockRepository = vetDelegatedByBlockRepository,
            stargateTokenRepository = stargateTokenRepository,
            tokenRewardRepository = tokenRewardRepository,
        )

    @Test
    fun `getRewards drops ALL when it duplicates finalized bucket`() {
        val tokenId = "42"
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("blockTimestamp")))

        val dayDoc =
            reward(
                id = "day-1",
                rewardPeriod = RewardPeriod.DAY,
                blockTimestamp = 100,
                dayOfMonth = 25,
                month = 10,
                year = 2025,
                rewards = BigInteger("123"),
            )

        val allTracker =
            reward(
                id = "all-1",
                rewardPeriod = RewardPeriod.ALL,
                blockTimestamp = 200,
                dayOfMonth = 25,
                month = 10,
                year = 2025,
                rewards = BigInteger("999"),
                dayReward = BigInteger("456"),
            )

        every {
            tokenRewardRepository.findByTokenIdAndRewardPeriodIn(
                tokenId,
                listOf(RewardPeriod.DAY, RewardPeriod.ALL),
                pageable,
            )
        } returns SliceImpl(listOf(dayDoc, allTracker), pageable, false)

        val slice =
            service.getRewards(
                tokenId = tokenId,
                validator = null,
                period = RewardPeriod.DAY,
                pageable,
            )

        expectThat(slice.content).containsExactly(dayDoc)
    }

    @Test
    fun `getRewards drops ALL when normalized value is zero`() {
        val tokenId = "42"
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("blockTimestamp")))

        val allTracker =
            reward(
                id = "all-1",
                rewardPeriod = RewardPeriod.ALL,
                blockTimestamp = 200,
                dayOfMonth = 25,
                month = 10,
                year = 2025,
                rewards = BigInteger("999"),
                dayReward = BigInteger.ZERO,
            )

        every {
            tokenRewardRepository.findByTokenIdAndRewardPeriodIn(
                tokenId,
                listOf(RewardPeriod.DAY, RewardPeriod.ALL),
                pageable,
            )
        } returns SliceImpl(listOf(allTracker), pageable, false)

        val slice =
            service.getRewards(
                tokenId = tokenId,
                validator = null,
                period = RewardPeriod.DAY,
                pageable,
            )

        expectThat(slice.content).isEmpty()
    }

    private fun reward(
        id: String,
        rewardPeriod: RewardPeriod,
        blockTimestamp: Long,
        dayOfMonth: Long,
        weekOfYear: Long = 43,
        month: Long,
        year: Long,
        rewards: BigInteger,
        validator: String = "0xvalidator",
        tokenId: String = "42",
        dayReward: BigInteger? = null,
        weekReward: BigInteger? = null,
        monthReward: BigInteger? = null,
        yearReward: BigInteger? = null,
        cycleReward: BigInteger? = null,
    ): TokenReward =
        TokenReward(
            id = id,
            blockId = "0xblock",
            blockNumber = 1,
            blockTimestamp = blockTimestamp,
            tokenId = tokenId,
            cycle = 1,
            validator = validator,
            rewards = rewards,
            effectiveStake = null,
            rewardPeriod = rewardPeriod,
            dayOfMonth = dayOfMonth,
            weekOfYear = weekOfYear,
            month = month,
            year = year,
            dayReward = dayReward,
            weekReward = weekReward,
            monthReward = monthReward,
            yearReward = yearReward,
            cycleReward = cycleReward,
            version = 0,
        )
}
