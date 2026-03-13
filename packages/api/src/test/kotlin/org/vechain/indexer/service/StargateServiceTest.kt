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
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenRepository
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockRepository
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockRepository
import org.vechain.indexer.validator.Status
import strikt.api.expectThat
import strikt.assertions.containsExactly

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
    fun `getRewards normalizes ALL documents to DAY period`() {
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

        val expectedNormalizedAll =
            reward(
                id = "all-1",
                rewardPeriod = RewardPeriod.DAY,
                blockTimestamp = 200,
                dayOfMonth = 25,
                month = 10,
                year = 2025,
                rewards = BigInteger("456"), // normalized from dayReward
            )

        expectThat(slice.content).containsExactly(dayDoc, expectedNormalizedAll)
    }

    @Test
    fun `getRewards normalizes ALL documents to WEEK period`() {
        val tokenId = "42"
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("blockTimestamp")))

        val allTracker =
            reward(
                id = "all-1",
                rewardPeriod = RewardPeriod.ALL,
                blockTimestamp = 200,
                dayOfMonth = 25,
                weekOfYear = 43,
                month = 10,
                year = 2025,
                rewards = BigInteger("999"),
                weekReward = BigInteger("789"),
            )

        every {
            tokenRewardRepository.findByTokenIdAndRewardPeriodIn(
                tokenId,
                listOf(RewardPeriod.WEEK, RewardPeriod.ALL),
                pageable,
            )
        } returns SliceImpl(listOf(allTracker), pageable, false)

        val slice =
            service.getRewards(
                tokenId = tokenId,
                validator = null,
                period = RewardPeriod.WEEK,
                pageable,
            )

        expectThat(slice.content)
            .containsExactly(
                reward(
                    id = "all-1",
                    rewardPeriod = RewardPeriod.WEEK,
                    blockTimestamp = 200,
                    dayOfMonth = 25,
                    weekOfYear = 43,
                    month = 10,
                    year = 2025,
                    rewards = BigInteger("789"), // normalized from weekReward
                )
            )
    }

    @Test
    fun `getRewards normalizes multiple ALL documents to target period`() {
        val tokenId = "42"
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("blockTimestamp")))

        val allTracker1 =
            reward(
                id = "all-1",
                rewardPeriod = RewardPeriod.ALL,
                blockTimestamp = 100,
                dayOfMonth = 25,
                month = 10,
                year = 2025,
                rewards = BigInteger("999"),
                monthReward = BigInteger("111"),
            )

        val allTracker2 =
            reward(
                id = "all-2",
                rewardPeriod = RewardPeriod.ALL,
                blockTimestamp = 200,
                dayOfMonth = 26,
                month = 10,
                year = 2025,
                rewards = BigInteger("888"),
                monthReward = BigInteger("222"),
            )

        every {
            tokenRewardRepository.findByTokenIdAndRewardPeriodIn(
                tokenId,
                listOf(RewardPeriod.MONTH, RewardPeriod.ALL),
                pageable,
            )
        } returns SliceImpl(listOf(allTracker1, allTracker2), pageable, false)

        val slice =
            service.getRewards(
                tokenId = tokenId,
                validator = null,
                period = RewardPeriod.MONTH,
                pageable,
            )

        expectThat(slice.content)
            .containsExactly(
                reward(
                    id = "all-1",
                    rewardPeriod = RewardPeriod.MONTH,
                    blockTimestamp = 100,
                    dayOfMonth = 25,
                    month = 10,
                    year = 2025,
                    rewards = BigInteger("111"), // normalized from monthReward
                ),
                reward(
                    id = "all-2",
                    rewardPeriod = RewardPeriod.MONTH,
                    blockTimestamp = 200,
                    dayOfMonth = 26,
                    month = 10,
                    year = 2025,
                    rewards = BigInteger("222"), // normalized from monthReward
                ),
            )
    }

    @Test
    fun `getRewards handles null period rewards by using zero`() {
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
                dayReward = null, // null day reward
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

        expectThat(slice.content)
            .containsExactly(
                reward(
                    id = "all-1",
                    rewardPeriod = RewardPeriod.DAY,
                    blockTimestamp = 200,
                    dayOfMonth = 25,
                    month = 10,
                    year = 2025,
                    rewards = BigInteger.ZERO, // null becomes zero
                )
            )
    }

    @Test
    fun `getStargateTokens excludes burned tokens from owner and manager lookups`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("blockNumber")))
        val token = token(tokenId = "15613", owner = "0xowner", manager = "0xmanager")

        every {
            stargateTokenRepository.findActiveByOwnerOrManager(
                "0xowner",
                "0xmanager",
                pageable = pageable,
            )
        } returns SliceImpl(listOf(token), pageable, false)

        val response =
            service.getStargateTokens(
                tokenId = null,
                manager = "0xmanager",
                owner = "0xowner",
                pageable = pageable,
            )

        expectThat(response.data).containsExactly(token)
    }

    @Test
    fun `getStargateTokens excludes burned tokens from manager-only lookups`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("blockNumber")))
        val token = token(tokenId = "17105", owner = "0xowner", manager = "0xmanager")

        every {
            stargateTokenRepository.findActiveByManager("0xmanager", pageable = pageable)
        } returns SliceImpl(listOf(token), pageable, false)

        val response =
            service.getStargateTokens(
                tokenId = null,
                manager = "0xmanager",
                owner = null,
                pageable = pageable,
            )

        expectThat(response.data).containsExactly(token)
    }

    @Test
    fun `getStargateTokens excludes burned tokens from unfiltered lookups`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("blockNumber")))
        val token = token(tokenId = "34813", owner = "0xowner", manager = "0xmanager")

        every { stargateTokenRepository.findAllActive(pageable = pageable) } returns
            SliceImpl(listOf(token), pageable, false)

        val response =
            service.getStargateTokens(
                tokenId = null,
                manager = null,
                owner = null,
                pageable = pageable,
            )

        expectThat(response.data).containsExactly(token)
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

    private fun token(tokenId: String, owner: String, manager: String? = null): StargateToken =
        StargateToken(
            tokenId = tokenId,
            level = TokenLevel.Dawn,
            owner = owner,
            manager = manager,
            delegationStatus = Status.NONE,
            validatorId = null,
            totalRewardsClaimed = BigInteger.ZERO,
            totalBootstrapRewardsClaimed = BigInteger.ZERO,
            vetStaked = BigInteger.TEN,
            migrated = false,
            boosted = false,
            blockNumber = 1,
            blockId = "0xblock",
            blockTimestamp = 1,
            version = 1,
        )
}
