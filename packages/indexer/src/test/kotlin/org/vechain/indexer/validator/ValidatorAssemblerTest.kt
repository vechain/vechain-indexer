package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeRows
import org.vechain.indexer.validator.logic.ValidatorAssembler
import org.vechain.indexer.validator.models.DecodedValidatorRow

class ValidatorAssemblerTest {
    private fun buildDecoded(): Map<String, Any?> =
        mapOf(
            "masters" to listOf("0xVAL1"),
            "endorsors" to listOf("0xEND1"),
            "statuses" to listOf(BigInteger.TWO),
            "onlines" to listOf(true),
            "offlineBlocks" to listOf(BigInteger.ZERO),
            "stakingPeriodLengths" to listOf(10),
            "startBlocks" to listOf(BigInteger.TEN),
            "exitBlocks" to listOf(BigInteger.valueOf(4294967295)),
            "completedPeriods" to listOf(BigInteger.valueOf(5)),
            "validatorLockedStakes" to listOf(BigInteger("1000000000000000000")),
            "validatorLockedWeights" to listOf(BigInteger.valueOf(100)),
            "delegatorsStake" to listOf(BigInteger("500000000000000000")),
            "totalQueuedStakes" to listOf(BigInteger.ZERO),
            "totalExitingStakes" to listOf(BigInteger.ZERO),
            "validatorQueuedStakes" to listOf(BigInteger.ZERO),
            "totalNextPeriodWeights" to listOf(BigInteger.valueOf(100)),
            "nextPeriodDelegationStakes" to listOf(BigInteger.ZERO),
        )

    private fun buildRow(
        id: String,
        status: BigInteger,
        startBlock: BigInteger = BigInteger.ZERO,
        exitBlock: BigInteger = BigInteger.valueOf(4294967295),
    ): DecodedValidatorRow =
        DecodedValidatorRow(
            id = id,
            endorser = "0xEND",
            status = status,
            online = true,
            offlineBlock = BigInteger.ZERO,
            stakingPeriodLength = 10,
            startBlock = startBlock,
            exitBlock = exitBlock,
            completedPeriods = BigInteger.ZERO,
            validatorLockedVET = BigInteger("1000000000000000000"),
            validatorLockedWeight = BigInteger.valueOf(100),
            delegatorsStake = BigInteger.ZERO,
            validatorQueuedStake = BigInteger.ZERO,
            totalQueuedStake = BigInteger.ZERO,
            totalExitingStake = BigInteger.ZERO,
            totalNextPeriodWeight = BigInteger.valueOf(100),
            nextPeriodDelegationStake = BigInteger.ZERO,
        )

    @Test
    fun `unpackValidators returns validator with correct basic fields`() {
        val validators =
            ValidatorAssembler.unpackValidators(
                rows = decodeRows(buildDecoded()),
                persistedDocs = emptyMap(),
                totalWeight = BigInteger.valueOf(100),
                vetPriceUsd = BigInteger("1000000000000"),
                vthoPriceUsd = BigInteger("1000000000000"),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        assertThat(validators).hasSize(1)
        val validator = validators.first()
        assertThat(validator.id).isEqualTo("0xVAL1")
        assertThat(validator.endorser).isEqualTo("0xEND1")
        assertThat(validator.status).isEqualTo(Status.ACTIVE)
        assertThat(validator.vetStaked!!.bigDecimalValue().setScale(6))
            .isEqualTo(BigDecimal("1.500000"))
    }

    @Test
    fun `unpackValidators uses carried docs for beneficiary and exiting stake derived fields`() {
        val exitingWei = BigInteger("1000000000000000000")
        val decoded =
            buildDecoded().toMutableMap().apply { put("totalExitingStakes", listOf(exitingWei)) }

        val persisted =
            Validator(
                id = "0xVAL1",
                blockId = "oldBlock",
                blockNumber = 19,
                blockTimestamp = 123,
                beneficiary = "0xOLD",
                status = Status.ACTIVE,
                version = 1,
            )
        val carried =
            persisted.copy(beneficiary = "0xNEW", exitingValidatorVetStaked = BigDecimal.ONE)

        val validators =
            ValidatorAssembler.unpackValidators(
                rows = decodeRows(decoded),
                persistedDocs = mapOf("0xVAL1" to persisted),
                carriedDocs = mapOf("0xVAL1" to carried),
                totalWeight = BigInteger.valueOf(100),
                vetPriceUsd = BigInteger("1000000000000"),
                vthoPriceUsd = BigInteger("1000000000000"),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        val validator = validators.first()
        assertThat(validator.beneficiary).isEqualTo("0xNEW")
        assertThat(validator.validatorExitingVetStaked!!.bigDecimalValue().setScale(6))
            .isEqualTo(BigDecimal("1.000000"))
        assertThat(validator.delegatorExitingVetStaked!!.bigDecimalValue().setScale(6))
            .isEqualTo(BigDecimal.ZERO.setScale(6))
    }

    @Test
    fun `disappeared validator is marked exited from persisted docs`() {
        val persisted =
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
                rows = decodeRows(buildDecoded()),
                persistedDocs = mapOf("0xOLD" to persisted),
                totalWeight = BigInteger.ONE,
                vetPriceUsd = BigInteger("1000000000000"),
                vthoPriceUsd = BigInteger.ONE,
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        val disappeared = validators.firstOrNull { it.id == "0xOLD" }
        assertThat(disappeared).isNotNull()
        assertThat(disappeared!!.status).isEqualTo(Status.EXITED)
        assertThat(disappeared.queuePosition).isNull()
        assertThat(disappeared.availableStartBlock).isNull()
    }

    @Test
    fun `unpackValidators emits overlay-only changes by comparing against persisted docs`() {
        val baseline =
            ValidatorAssembler.unpackValidators(
                    rows = decodeRows(buildDecoded()),
                    persistedDocs = emptyMap(),
                    totalWeight = BigInteger.valueOf(100),
                    vetPriceUsd = BigInteger("1000000000000"),
                    vthoPriceUsd = BigInteger("1000000000000"),
                    blockId = "0xBLOCK",
                    blockNumber = 20,
                    blockTimestamp = 1234567890,
                )
                .first()

        val persisted = baseline.copy(beneficiary = "0xOLD", version = 5)
        val carried =
            baseline.copy(
                beneficiary = "0xNEW",
                blockId = "0xOLD_BLOCK",
                blockNumber = 19,
                blockTimestamp = 123,
                version = 5,
            )

        val validators =
            ValidatorAssembler.unpackValidators(
                rows = decodeRows(buildDecoded()),
                persistedDocs = mapOf("0xVAL1" to persisted),
                carriedDocs = mapOf("0xVAL1" to carried),
                totalWeight = BigInteger.valueOf(100),
                vetPriceUsd = BigInteger("1000000000000"),
                vthoPriceUsd = BigInteger("1000000000000"),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        assertThat(validators)
            .singleElement()
            .extracting<String?> { it.beneficiary }
            .isEqualTo("0xNEW")
    }

    @Test
    fun `calculateQueueInfo returns empty map when no queued validators exist`() {
        val result =
            ValidatorAssembler.calculateQueueInfo(
                listOf(
                    buildRow("0xVAL1", BigInteger.TWO),
                    buildRow("0xVAL2", BigInteger.valueOf(4)),
                )
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `calculateQueueInfo uses queued row order from decoded data`() {
        val rows =
            listOf(
                buildRow("0xQ2", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xACTIVE", BigInteger.TWO),
                buildRow("0xQ1", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xQ3", BigInteger.ONE, startBlock = BigInteger.ZERO),
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows)

        assertThat(result["0xQ2"]!!.position).isEqualTo(1)
        assertThat(result["0xQ1"]!!.position).isEqualTo(2)
        assertThat(result["0xQ3"]!!.position).isEqualTo(3)
    }

    @Test
    fun `calculateQueueInfo matches queued validators to exiting validators by sorted exit block`() {
        val rows =
            listOf(
                buildRow("0xQ1", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xQ2", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow(
                    "0xEXIT_LATER",
                    BigInteger.valueOf(4),
                    exitBlock = BigInteger.valueOf(500),
                ),
                buildRow(
                    "0xEXIT_SOONER",
                    BigInteger.valueOf(4),
                    exitBlock = BigInteger.valueOf(100),
                ),
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows)

        assertThat(result["0xQ1"]!!.availableStartBlock).isEqualTo(100)
        assertThat(result["0xQ2"]!!.availableStartBlock).isEqualTo(500)
    }

    @Test
    fun `queued validators get queue position from decoded order`() {
        val decoded =
            buildDecoded().toMutableMap().apply {
                put("masters", listOf("0xQ1", "0xQ2"))
                put("endorsors", listOf("0xEND1", "0xEND2"))
                put("statuses", listOf(BigInteger.ONE, BigInteger.ONE))
                put("onlines", listOf(true, true))
                put("offlineBlocks", listOf(BigInteger.ZERO, BigInteger.ZERO))
                put("stakingPeriodLengths", listOf(10, 10))
                put("startBlocks", listOf(BigInteger.valueOf(100), BigInteger.ZERO))
                put(
                    "exitBlocks",
                    listOf(BigInteger.valueOf(4294967295), BigInteger.valueOf(4294967295)),
                )
                put("completedPeriods", listOf(BigInteger.ZERO, BigInteger.ZERO))
                put(
                    "validatorLockedStakes",
                    listOf(BigInteger("1000000000000000000"), BigInteger("1000000000000000000")),
                )
                put(
                    "validatorLockedWeights",
                    listOf(BigInteger.valueOf(100), BigInteger.valueOf(100)),
                )
                put("delegatorsStake", listOf(BigInteger.ZERO, BigInteger.ZERO))
                put("totalQueuedStakes", listOf(BigInteger.ZERO, BigInteger.ZERO))
                put("totalExitingStakes", listOf(BigInteger.ZERO, BigInteger.ZERO))
                put("validatorQueuedStakes", listOf(BigInteger.ZERO, BigInteger.ZERO))
                put(
                    "totalNextPeriodWeights",
                    listOf(BigInteger.valueOf(100), BigInteger.valueOf(100)),
                )
                put("nextPeriodDelegationStakes", listOf(BigInteger.ZERO, BigInteger.ZERO))
            }

        val validators =
            ValidatorAssembler.unpackValidators(
                rows = decodeRows(decoded),
                persistedDocs = emptyMap(),
                totalWeight = BigInteger.valueOf(200),
                vetPriceUsd = BigInteger("1000000000000"),
                vthoPriceUsd = BigInteger("1000000000000"),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        assertThat(validators[0].queuePosition).isEqualTo(1)
        assertThat(validators[0].availableStartBlock).isEqualTo(100)
        assertThat(validators[1].queuePosition).isEqualTo(2)
        assertThat(validators[1].availableStartBlock).isEqualTo(0)
    }

    @Test
    fun `active validator has null queue fields`() {
        val validators =
            ValidatorAssembler.unpackValidators(
                rows = decodeRows(buildDecoded()),
                persistedDocs = emptyMap(),
                totalWeight = BigInteger.valueOf(100),
                vetPriceUsd = BigInteger("1000000000000"),
                vthoPriceUsd = BigInteger("1000000000000"),
                blockId = "0xBLOCK",
                blockNumber = 20,
                blockTimestamp = 1234567890,
            )

        assertThat(validators.first().queuePosition).isNull()
        assertThat(validators.first().availableStartBlock).isNull()
    }
}
