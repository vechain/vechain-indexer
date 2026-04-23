package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.RoundingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.validator.Status

class ValidatorCalculatorTest {
    @Test
    fun `should return zeros when total TVL is zero`() {
        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            ValidatorCalculator.calculateValidatorYield(
                validatorTvl = BigDecimal.ZERO,
                delegatorTvl = BigDecimal.ZERO,
                hasDelegations = false,
                blocksPerYear = BigDecimal.TEN,
                vthoIssued = BigDecimal("100"),
                vthoPrice = BigDecimal.ONE,
            )

        assertThat(validatorYield).isEqualTo(BigDecimal.ZERO)
        assertThat(tvlBasedYield).isEqualTo(BigDecimal.ZERO)
        assertThat(avgDelegatorYield).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `validator without delegations gets full issuance yield`() {
        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            ValidatorCalculator.calculateValidatorYield(
                validatorTvl = BigDecimal("100"),
                delegatorTvl = BigDecimal.ZERO,
                hasDelegations = false,
                blocksPerYear = BigDecimal("100"),
                vthoIssued = BigDecimal("100"),
                vthoPrice = BigDecimal.ONE,
            )

        assertThat(validatorYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("10000"))
        assertThat(tvlBasedYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("10000"))
        assertThat(avgDelegatorYield).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `validator with delegations splits yield between validator and delegators`() {
        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            ValidatorCalculator.calculateValidatorYield(
                validatorTvl = BigDecimal("100"),
                delegatorTvl = BigDecimal("100"),
                hasDelegations = true,
                blocksPerYear = BigDecimal("10"),
                vthoIssued = BigDecimal("100"),
                vthoPrice = BigDecimal.ONE,
            )

        assertThat(validatorYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("300"))
        assertThat(tvlBasedYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("500"))
        assertThat(avgDelegatorYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("700"))
    }

    // --- calculateNftLevelYieldsIfDelegatedNextCycle tests ---

    private val allNftLevels = TokenLevel.entries.filter { it != TokenLevel.All }

    private fun callProjectedNftYields(nextCycleStake: BigDecimal, status: Status = Status.ACTIVE) =
        ValidatorCalculator.calculateNftLevelYieldsIfDelegatedNextCycle(
            nextPeriodWeight = BigDecimal("1000000"),
            nextPeriodVET = BigDecimal("100000000"),
            nextCycleEffectiveDelegationStake = BigDecimal("10000000"),
            totalNextPeriodWeight = BigDecimal("10000000"),
            vthoPriceUsd = BigDecimal("0.01"),
            vetPriceUsd = BigDecimal("0.03"),
            status = status,
            nextCycleStake = nextCycleStake,
        )

    @Test
    fun `nftYieldsIfDelegatedNextCycle is empty when validator is at max capacity 600M`() {
        val result = callProjectedNftYields(nextCycleStake = BigDecimal("600000000"))
        assertThat(result).isEmpty()
    }

    @Test
    fun `nftYieldsIfDelegatedNextCycle near capacity includes small NFTs but excludes large ones`() {
        val result = callProjectedNftYields(nextCycleStake = BigDecimal("599000000"))

        // Small NFTs (staked <= 1M) should be included
        assertThat(result).containsKey(TokenLevel.Dawn) // 10K
        assertThat(result).containsKey(TokenLevel.Lightning) // 50K
        assertThat(result).containsKey(TokenLevel.Flash) // 200K
        assertThat(result).containsKey(TokenLevel.VeThorX) // 600K
        assertThat(result).containsKey(TokenLevel.Strength) // 1M

        // Large NFTs (staked > 1M) would exceed 600M
        assertThat(result).doesNotContainKey(TokenLevel.StrengthX) // 1.6M
        assertThat(result).doesNotContainKey(TokenLevel.Thunder) // 5M
        assertThat(result).doesNotContainKey(TokenLevel.ThunderX) // 5.6M
        assertThat(result).doesNotContainKey(TokenLevel.Mjolnir) // 15M
        assertThat(result).doesNotContainKey(TokenLevel.MjolnirX) // 15.6M
    }

    @Test
    fun `nftYieldsIfDelegatedNextCycle with plenty of capacity includes all levels`() {
        val result = callProjectedNftYields(nextCycleStake = BigDecimal("100000000"))
        assertThat(result.keys).containsExactlyInAnyOrderElementsOf(allNftLevels)
    }

    @Test
    fun `nftYieldsIfDelegatedNextCycle is empty for exiting validator`() {
        val result =
            callProjectedNftYields(
                nextCycleStake = BigDecimal("100000000"),
                status = Status.EXITING,
            )
        assertThat(result).isEmpty()
    }

    @Test
    fun `nftYieldsIfDelegatedNextCycle exact boundary includes Dawn at 600M`() {
        // 599,990,000 + 10,000 (Dawn) = 600,000,000 exactly — should be included (strict >)
        val result = callProjectedNftYields(nextCycleStake = BigDecimal("599990000"))
        assertThat(result).containsKey(TokenLevel.Dawn)
    }

    @Test
    fun `nftYields is empty when validator has no delegated levels`() {
        val result =
            ValidatorCalculator.calculateDelegatedNftLevelYieldsCurrentCycle(
                currentDelegatedLevels = emptyMap(),
                blocksPerYear = BigDecimal("100"),
                vthoIssued = BigDecimal("100"),
                vthoPriceUsd = BigDecimal.ONE,
                vetPriceUsd = BigDecimal.ONE,
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `nftYields only includes delegated levels`() {
        val result =
            ValidatorCalculator.calculateDelegatedNftLevelYieldsCurrentCycle(
                currentDelegatedLevels = mapOf(TokenLevel.Dawn to 2L, TokenLevel.VeThorX to 1L),
                blocksPerYear = BigDecimal("100"),
                vthoIssued = BigDecimal("100"),
                vthoPriceUsd = BigDecimal.ONE,
                vetPriceUsd = BigDecimal.ONE,
            )

        assertThat(result).containsKeys(TokenLevel.Dawn, TokenLevel.VeThorX)
        assertThat(result).doesNotContainKeys(TokenLevel.Strength, TokenLevel.Mjolnir)
        assertThat(result[TokenLevel.VeThorX]).isGreaterThan(result[TokenLevel.Dawn])
    }
}
