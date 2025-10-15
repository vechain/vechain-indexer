package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.BigInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator

class ValidatorAssemblerTest {
    private fun buildDecoded(): Map<String, Any?> =
        mapOf(
            "masters" to listOf("0xVAL1"),
            "endorsors" to listOf("0xEND1"),
            "statuses" to listOf(BigInteger.ONE),
            "onlines" to listOf(true),
            "offlineBlocks" to listOf(BigInteger.ZERO),
            "stakingPeriodLengths" to listOf(10),
            "startBlocks" to listOf(BigInteger.TEN),
            "exitBlocks" to listOf(BigInteger.valueOf(4294967295)),
            "completedPeriods" to listOf(BigInteger.valueOf(5)),
            "validatorLockedStakes" to listOf(BigInteger("1000000000000000000")), // 1 VET
            "validatorLockedWeights" to listOf(BigInteger.valueOf(100)),
            "delegatorsStake" to listOf(BigInteger("500000000000000000")), // 0.5 VET
            "totalQueuedStakes" to listOf(BigInteger.ZERO),
            "totalExitingStakes" to listOf(BigInteger.ZERO),
            "totalNextPeriodWeights" to listOf(BigInteger.valueOf(100)),
        )

    @Test
    fun `unpackValidators should return validator with correct basic fields`() {
        val decoded = buildDecoded()

        val validators =
            ValidatorAssembler.unpackValidators(
                decoded = decoded,
                existingDocs = emptyMap(),
                totalWeight = BigInteger.valueOf(100),
                totalVTHOSupply = BigInteger.valueOf(1_000),
                totalVTHOBurned = BigInteger.valueOf(1_000),
                vetPriceUsd = BigInteger("1000000000000"),
                vthoPriceUsd = BigInteger("1000000000000"),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        assertThat(validators).hasSize(1)
        val v = validators.first()
        assertThat(v.id).isEqualTo("0xVAL1")
        assertThat(v.endorser).isEqualTo("0xEND1")
        assertThat(v.status).isEqualTo(Status.fromCode(1))
        assertThat(v.vetStaked!!.bigDecimalValue().setScale(6)).isEqualTo(BigDecimal("1.500000"))
    }

    @Test
    fun `should increment offline blocks if validator is offline`() {
        val decoded =
            buildDecoded().toMutableMap().apply {
                put("onlines", listOf(false))
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
            ValidatorAssembler.unpackValidators(
                decoded,
                mapOf("0xVAL1" to existing),
                BigInteger.valueOf(100),
                BigInteger.valueOf(2000),
                BigInteger.valueOf(100),
                BigInteger("1000000000000"),
                BigInteger("1000000000000"),
                "0xBLOCK",
                20,
                1234567890,
            )

        assertThat(validators.first().offlineBlocks).isEqualTo(2L)
    }

    @Test
    fun `should mark validator as exiting if exitBlock not MAX_UINT32`() {
        val decoded =
            buildDecoded().toMutableMap().apply {
                put("exitBlocks", listOf(BigInteger.valueOf(1000)))
            }

        val validators =
            ValidatorAssembler.unpackValidators(
                decoded,
                emptyMap(),
                BigInteger.TEN,
                BigInteger.TEN,
                BigInteger.ZERO,
                BigInteger("1000000000000"),
                BigInteger("1000000000000"),
                "0xBLOCK",
                20,
                1234567890,
            )

        assertThat(validators.first().status).isEqualTo(Status.fromCode(4))
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
            ValidatorAssembler.unpackValidators(
                decoded,
                mapOf("0xOLD" to existing),
                BigInteger.ONE,
                BigInteger.ONE,
                BigInteger.ZERO,
                BigInteger("1000000000000"),
                BigInteger("1000000000000"),
                "0xBLOCK",
                20,
                1234567890,
            )

        val disappeared = validators.firstOrNull { it.id == "0xOLD" }
        assertThat(disappeared!!.status).isEqualTo(Status.EXITED)
    }
}
