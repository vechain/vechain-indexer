package org.vechain.indexer.stargate.rewards

import io.mockk.*
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.thor.HexUtils.toHex
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.models.DecodedValidatorInfo

class TokenRewardServiceTest {
    private val repository = mockk<TokenRewardRepository>(relaxed = true)
    private val archiveService = mockk<ArchiveService<TokenReward>>(relaxed = true)
    private val delegationRepository = mockk<DelegationRepository>(relaxed = true)
    private val thorClient = mockk<ThorClient>(relaxed = true)

    private lateinit var service: TokenRewardService

    private fun blockId(num: Long): String = toHex(num, 64)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service =
            spyk(TokenRewardService(repository, archiveService, delegationRepository, thorClient))
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
            version = 0,
        )

    @Test
    fun `getDelegatorsBlockReward computes 70 percent share`() {
        val b = block(10)
        val decoded =
            DecodedValidatorInfo(
                vthoTotalSupply = BigInteger.valueOf(1000),
                vthoBurned = BigInteger.valueOf(100),
                vetPriceUsd = BigInteger.TEN,
                vthoPriceUsd = BigInteger.TEN,
                totalWeight = BigInteger.ZERO,
                decodedValidators = emptyMap(),
            )

        // return dummy responses – actual decoding mocked
        coEvery { thorClient.inspectClauses(any(), any()) } returns
            listOf(
                InspectionResult(
                    data = "0x1",
                    events = emptyList(),
                    transfers = emptyList(),
                    0,
                    reverted = false,
                    vmError = null,
                ),
                InspectionResult(
                    data = "0x2",
                    events = emptyList(),
                    transfers = emptyList(),
                    0,
                    reverted = false,
                    vmError = null,
                ),
            )

        mockkObject(org.vechain.indexer.validator.domain.ValidatorDecoder)
        every {
            org.vechain.indexer.validator.domain.ValidatorDecoder.decodeVTHOIssued(any())
        } returns BigInteger.valueOf(500)

        val result = runBlocking { service.getDelegatorsBlockReward(b, decoded) }

        // (total=1000, prev=500, delta=500) * 0.7 = 350
        assertThat(result).isEqualTo(BigInteger.valueOf(350))
    }

    @Test
    fun `updateRewardInfo distributes rewards proportionally`() {
        val validator = "0x00000000000000000000000000000000000000a1"

        // set up cycle cache with real decodedValidators map
        service.updateValidatorCycleCache(
            validator,
            DecodedValidatorInfo(
                vthoTotalSupply = BigInteger.ZERO,
                vthoBurned = BigInteger.ZERO,
                vetPriceUsd = BigInteger.TEN,
                vthoPriceUsd = BigInteger.TEN,
                totalWeight = BigInteger.ZERO,
                decodedValidators =
                    mapOf(
                        "masters" to listOf(validator),
                        "stakingPeriodLengths" to listOf(BigInteger.ONE),
                        "startBlocks" to listOf(BigInteger.ZERO),
                        "completedPeriods" to listOf(BigInteger.ZERO),
                        "delegatorsStake" to listOf(BigInteger.ONE),
                    ),
            ),
        )

        // manually set totalEffectiveDelegations in cache
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
    fun `getOrFetchRewardsNewCycle creates new docs for missing delegations`() {
        val validator = "0x00000000000000000000000000000000000000a1"

        // Mock delegation with tokenLevel that has effectiveStake
        val mockDelegation =
            mockk<org.vechain.indexer.validator.Delegation> {
                every { tokenId } returns "10001"
                every { tokenLevel } returns org.vechain.indexer.stargate.token.TokenLevel.Dawn
            }

        every {
            delegationRepository.findByValidatorAndStatusIn(
                validator,
                listOf(Status.ACTIVE, Status.EXITING),
            )
        } returns listOf(mockDelegation)

        every { repository.findAllById(any<List<String>>()) } returns emptyList()

        service.updateValidatorCycleCache(
            validator,
            DecodedValidatorInfo(
                vthoTotalSupply = BigInteger.ZERO,
                vthoBurned = BigInteger.ZERO,
                vetPriceUsd = BigInteger.TEN,
                vthoPriceUsd = BigInteger.TEN,
                totalWeight = BigInteger.ZERO,
                decodedValidators =
                    mapOf(
                        "masters" to listOf(validator),
                        "stakingPeriodLengths" to listOf(BigInteger.ONE),
                        "startBlocks" to listOf(BigInteger.ZERO),
                        "completedPeriods" to listOf(BigInteger.ZERO),
                        "delegatorsStake" to listOf(BigInteger.ONE),
                    ),
            ),
        )

        val result =
            service.getOrFetchRewardsNewCycle(
                validator,
                block(num = 200),
                Instant.now().atZone(ZoneOffset.UTC).toLocalDate(),
            )

        // Dawn level has effectiveStake of 10000 (staked) * 1 (rewardMultiplier) = 10000
        // Converted to wei: 10000 * 10^18
        val expectedStakeWei =
            java.math.BigDecimal("10000").multiply(java.math.BigDecimal.TEN.pow(18)).toBigInteger()
        assertThat(result).hasSize(1)
        assertThat(result.first().effectiveStake).isEqualTo(expectedStakeWei)
    }
}
