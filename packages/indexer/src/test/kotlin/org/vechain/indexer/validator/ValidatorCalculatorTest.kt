package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.RoundingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
}
