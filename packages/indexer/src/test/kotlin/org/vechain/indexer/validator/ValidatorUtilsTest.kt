package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import strikt.assertions.isEqualTo

class ValidatorUtilsTest {
    private fun buildDecoded(): Map<String, Any?> =
        mapOf(
            "masters" to listOf("0xVAL1"),
            "endorsors" to listOf("0xEND1"),
            "statuses" to listOf(BigInteger.ONE),
            "onlines" to listOf(true),
            "offlineBlocks" to listOf(BigInteger.ZERO),
            "stakingPeriodLengths" to listOf(10),
            "startBlocks" to listOf(BigInteger.TEN),
            "exitBlocks" to listOf(BigInteger.valueOf(4294967295)), // MAX_UINT32
            "completedPeriods" to listOf(BigInteger.valueOf(5)),
            "validatorLockedStakes" to
                listOf(BigInteger.valueOf(1000_000_000_000_000_000)), // 1 VET
            "validatorLockedWeights" to listOf(BigInteger.valueOf(100)),
            "delegatorsStake" to listOf(BigInteger.valueOf(500_000_000_000_000_000)), // 0.5 VET
            "totalQueuedStakes" to listOf(BigInteger.ZERO),
            "totalExitingStakes" to listOf(BigInteger.ZERO),
        )

    @Test
    fun `unpackValidators should return validator with correct basic fields`() {
        val decoded = buildDecoded()

        val validators =
            ValidatorUtils.unpackValidators(
                decoded = decoded,
                existingDocs = emptyMap(),
                totalWeight = BigInteger.valueOf(100),
                totalVTHOSupply = BigInteger.valueOf(1_000_000_000_000),
                totalVTHOBurned = BigInteger.valueOf(1_000_000_000_000),
                vetPriceUsd = BigInteger.valueOf(1_000_000_000_000), // 1 USD
                vthoPriceUsd = BigInteger.valueOf(1_000_000_000_000), // 1 USD
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        assertThat(validators).hasSize(1)
        val v = validators.first()
        assertThat(v.id).isEqualTo("0xVAL1")
        assertThat(v.endorser).isEqualTo("0xEND1")
        assertThat(v.status).isEqualTo(Status.Companion.fromCode(1))
        assertThat(v.vetStaked!!.bigDecimalValue().setScale(6)).isEqualTo(BigDecimal("1.500000"))
        assertThat(v.delegatorVetStaked!!.bigDecimalValue().setScale(6))
            .isEqualTo(BigDecimal("0.500000"))
        assertThat(v.validatorVetStaked!!.bigDecimalValue().setScale(6))
            .isEqualTo(BigDecimal("1.000000"))
        assertThat(v.version).isEqualTo(1)
    }

    @Test
    fun `should increment offline blocks if validator is offline`() {
        val decoded =
            buildDecoded().toMutableMap().apply {
                put("onlines", listOf(false)) // offline
                put("offlineBlocks", listOf(BigInteger.valueOf(15)))
            }

        val existing =
            Validator(
                id = "0xVAL1",
                blockId = "oldBlock",
                blockNumber = 19,
                blockTimestamp = 123,
                offlineBlocks = 1L,
                status = Status.ACTIVE,
                version = 1,
            )

        val validators =
            ValidatorUtils.unpackValidators(
                decoded = decoded,
                existingDocs = mapOf("0xVAL1" to existing),
                totalWeight = BigInteger.valueOf(100),
                totalVTHOSupply = BigInteger.valueOf(2000),
                totalVTHOBurned = BigInteger.valueOf(100),
                vetPriceUsd = BigInteger.valueOf(1_000_000_000_000),
                vthoPriceUsd = BigInteger.valueOf(1_000_000_000_000),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        val v = validators.first()
        assertThat(v.offlineBlocks).isEqualTo(2L) // incremented
    }

    @Test
    fun `should mark validator as exiting if exitBlock not MAX_UINT32`() {
        val decoded =
            buildDecoded().toMutableMap().apply {
                put("exitBlocks", listOf(BigInteger.valueOf(1000))) // not MAX_UINT32
            }

        val validators =
            ValidatorUtils.unpackValidators(
                decoded,
                emptyMap(),
                totalWeight = BigInteger.TEN,
                totalVTHOSupply = BigInteger.TEN,
                totalVTHOBurned = BigInteger.ZERO,
                vetPriceUsd = BigInteger.valueOf(1_000_000_000_000),
                vthoPriceUsd = BigInteger.valueOf(1_000_000_000_000),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        assertThat(validators.first().status).isEqualTo(Status.Companion.fromCode(4)) // Exiting
    }

    @Test
    fun `disappeared validator should be marked as exited`() {
        val decoded = buildDecoded()
        val existing =
            Validator(
                id = "0xOLD",
                blockId = "oldBlock",
                blockNumber = 19,
                blockTimestamp = 123,
                status = Status.ACTIVE,
                version = 1,
            )

        val validators =
            ValidatorUtils.unpackValidators(
                decoded,
                existingDocs = mapOf("0xOLD" to existing),
                totalWeight = BigInteger.ONE,
                totalVTHOSupply = BigInteger.ONE,
                totalVTHOBurned = BigInteger.ZERO,
                vetPriceUsd = BigInteger.valueOf(1_000_000_000_000),
                vthoPriceUsd = BigInteger.valueOf(1_000_000_000_000),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        val disappeared = validators.firstOrNull { it.id == "0xOLD" }
        assertThat(disappeared).isNotNull
        assertThat(disappeared!!.status).isEqualTo(Status.EXITED)
    }

    @Test
    fun `should return zeros when total TVL is zero`() {
        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            ValidatorUtils.calculateValidatorYield(
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
            ValidatorUtils.calculateValidatorYield(
                validatorTvl = BigDecimal("100"),
                delegatorTvl = BigDecimal.ZERO,
                hasDelegations = false,
                blocksPerYear = BigDecimal("100"),
                vthoIssued = BigDecimal("100"),
                vthoPrice = BigDecimal.ONE,
            )

        // issuance = 100 * 1 = 100
        // annualIssuanceUsd = 100 * 100 = 10000
        // yield = (10000 / 100) * 100 = 10000%
        assertThat(validatorYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("10000"))
        assertThat(tvlBasedYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("10000"))
        assertThat(avgDelegatorYield).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `validator with delegations splits yield between validator and delegators`() {
        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            ValidatorUtils.calculateValidatorYield(
                validatorTvl = BigDecimal("100"),
                delegatorTvl = BigDecimal("100"),
                hasDelegations = true,
                blocksPerYear = BigDecimal("10"),
                vthoIssued = BigDecimal("100"),
                vthoPrice = BigDecimal.ONE,
            )

        // issuance = 100 * 1 = 100
        // annualIssuanceUsd = 10 * 100 = 1000
        // totalTvl = 200
        // validatorPct = 100/200 = 0.5
        //
        // validatorYield = (1000/100) * 0.3 * 100 = 300%
        // tvlBasedYield = (1000/100) * 0.5 * 100 = 500%
        // avgDelegatorYield = (1000/100) * 0.7 * 100 = 700%
        assertThat(validatorYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("300"))
        assertThat(tvlBasedYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("500"))
        assertThat(avgDelegatorYield.setScale(0, RoundingMode.HALF_UP)).isEqualTo(BigDecimal("700"))
    }
}
