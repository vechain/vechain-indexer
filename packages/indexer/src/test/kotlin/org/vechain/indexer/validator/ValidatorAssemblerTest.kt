package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
            "validatorLockedStakes" to listOf(BigInteger("1000000000000000000")), // 1 VET
            "validatorLockedWeights" to listOf(BigInteger.valueOf(100)),
            "delegatorsStake" to listOf(BigInteger("500000000000000000")), // 0.5 VET
            "totalQueuedStakes" to listOf(BigInteger.ZERO),
            "totalExitingStakes" to listOf(BigInteger.ZERO),
            "validatorQueuedStakes" to listOf(BigInteger.ZERO),
            "totalNextPeriodWeights" to listOf(BigInteger.valueOf(100)),
            "nextPeriodDelegationStakes" to listOf(BigInteger.ZERO),
        )

    @Test
    fun `unpackValidators should return validator with correct basic fields`() {
        val decoded = buildDecoded()

        val validators =
            ValidatorAssembler.unpackValidators(
                decoded = decoded,
                existingDocs = emptyMap(),
                totalWeight = BigInteger.valueOf(100),
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
        assertThat(v.status).isEqualTo(Status.fromCode(2))
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
                BigInteger("1000000000000"),
                BigInteger.TEN,
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
                BigInteger("1000000000000"),
                BigInteger.ONE,
                "0xBLOCK",
                20,
                1234567890,
            )

        val disappeared = validators.firstOrNull { it.id == "0xOLD" }
        assertThat(disappeared!!.status).isEqualTo(Status.EXITED)
    }

    // ----- Queue Position Tests -----

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
    fun `calculateQueueInfo returns empty map when no QUEUED validators`() {
        val rows =
            listOf(
                buildRow("0xVAL1", BigInteger.TWO), // ACTIVE
                buildRow("0xVAL2", BigInteger.valueOf(4)), // EXITING
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, emptyMap())

        assertThat(result).isEmpty()
    }

    @Test
    fun `calculateQueueInfo returns queue info for QUEUED validators`() {
        val rows =
            listOf(
                buildRow("0xVAL1", BigInteger.ONE, startBlock = BigInteger.valueOf(100)), // QUEUED
                buildRow("0xVAL2", BigInteger.TWO), // ACTIVE
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, emptyMap())

        assertThat(result).hasSize(1)
        assertThat(result["0xVAL1"]!!.position).isEqualTo(1)
        assertThat(result["0xVAL1"]!!.availableStartBlock).isEqualTo(100)
    }

    @Test
    fun `calculateQueueInfo uses exiting validator exitBlock when queued startBlock is 0`() {
        val rows =
            listOf(
                buildRow(
                    "0xQUEUED",
                    BigInteger.ONE,
                    startBlock = BigInteger.ZERO,
                ), // QUEUED with no start
                buildRow(
                    "0xEXITING",
                    BigInteger.valueOf(4),
                    exitBlock = BigInteger.valueOf(500),
                ), // EXITING
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, emptyMap())

        assertThat(result["0xQUEUED"]!!.availableStartBlock).isEqualTo(500)
    }

    @Test
    fun `calculateQueueInfo matches queued validators to exiting validators by position`() {
        val rows =
            listOf(
                buildRow("0xQ1", BigInteger.ONE, startBlock = BigInteger.ZERO), // QUEUED position 1
                buildRow("0xQ2", BigInteger.ONE, startBlock = BigInteger.ZERO), // QUEUED position 2
                buildRow("0xQ3", BigInteger.ONE, startBlock = BigInteger.ZERO), // QUEUED position 3
                buildRow(
                    "0xEXIT1",
                    BigInteger.valueOf(4),
                    exitBlock = BigInteger.valueOf(100),
                ), // EXITING first
                buildRow(
                    "0xEXIT2",
                    BigInteger.valueOf(4),
                    exitBlock = BigInteger.valueOf(200),
                ), // EXITING second
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, emptyMap())

        // Q1 gets first exiting's exit block (100)
        assertThat(result["0xQ1"]!!.availableStartBlock).isEqualTo(100)
        // Q2 gets second exiting's exit block (200)
        assertThat(result["0xQ2"]!!.availableStartBlock).isEqualTo(200)
        // Q3 has no matching exiting validator, so 0
        assertThat(result["0xQ3"]!!.availableStartBlock).isEqualTo(0)
    }

    @Test
    fun `calculateQueueInfo sorts exiting validators by exit block before matching`() {
        val rows =
            listOf(
                buildRow("0xQ1", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xQ2", BigInteger.ONE, startBlock = BigInteger.ZERO),
                // Exit blocks are NOT in order in the list
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

        val result = ValidatorAssembler.calculateQueueInfo(rows, emptyMap())

        // Q1 should get the EARLIEST exit block (100), not the first in list
        assertThat(result["0xQ1"]!!.availableStartBlock).isEqualTo(100)
        // Q2 gets the next earliest (500)
        assertThat(result["0xQ2"]!!.availableStartBlock).isEqualTo(500)
    }

    @Test
    fun `calculateQueueInfo ignores exiting validators with MAX_UINT32 exit block`() {
        val rows =
            listOf(
                buildRow("0xQ1", BigInteger.ONE, startBlock = BigInteger.ZERO),
                // EXITING but exit block is MAX_UINT32 (not set)
                buildRow(
                    "0xEXIT_NOT_SET",
                    BigInteger.valueOf(4),
                    exitBlock = BigInteger.valueOf(4294967295),
                ),
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, emptyMap())

        // Q1 should have 0 availableStartBlock because the exiting validator has no real exit block
        assertThat(result["0xQ1"]!!.availableStartBlock).isEqualTo(0)
    }

    @Test
    fun `calculateQueueInfo returns 0 availableStartBlock when no startBlock and no exiting validator`() {
        val rows =
            listOf(
                buildRow(
                    "0xQUEUED",
                    BigInteger.ONE,
                    startBlock = BigInteger.ZERO,
                ), // QUEUED with no start
                buildRow("0xACTIVE", BigInteger.TWO), // ACTIVE (no exiting validator)
            )
        val existingDocs =
            mapOf(
                "0xQUEUED" to
                    Validator(
                        id = "0xQUEUED",
                        blockId = "oldBlock",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 1,
                        availableStartBlock = 750, // Previous value should NOT be used
                        version = 1,
                    )
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, existingDocs)

        assertThat(result["0xQUEUED"]!!.position).isEqualTo(1)
        // availableStartBlock is 0 (unknown) because no one is exiting
        assertThat(result["0xQUEUED"]!!.availableStartBlock).isEqualTo(0)
    }

    @Test
    fun `calculateQueueInfo preserves queue order from previous positions when no start blocks`() {
        val rows =
            listOf(
                buildRow("0xQ1", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xQ2", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xQ3", BigInteger.ONE, startBlock = BigInteger.ZERO),
            )
        // Previous block had Q2 first, Q3 second, Q1 third
        val existingDocs =
            mapOf(
                "0xQ1" to
                    Validator(
                        id = "0xQ1",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 3,
                        availableStartBlock = 0,
                        version = 1,
                    ),
                "0xQ2" to
                    Validator(
                        id = "0xQ2",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 1,
                        availableStartBlock = 0,
                        version = 1,
                    ),
                "0xQ3" to
                    Validator(
                        id = "0xQ3",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 2,
                        availableStartBlock = 0,
                        version = 1,
                    ),
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, existingDocs)

        // Order should be preserved: Q2 (pos 1), Q3 (pos 2), Q1 (pos 3)
        assertThat(result["0xQ2"]!!.position).isEqualTo(1)
        assertThat(result["0xQ3"]!!.position).isEqualTo(2)
        assertThat(result["0xQ1"]!!.position).isEqualTo(3)
    }

    @Test
    fun `calculateQueueInfo adds new validators to end of queue`() {
        val rows =
            listOf(
                buildRow("0xNEW1", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xEXISTING", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xNEW2", BigInteger.ONE, startBlock = BigInteger.ZERO),
            )
        // Only 0xEXISTING was in queue before
        val existingDocs =
            mapOf(
                "0xEXISTING" to
                    Validator(
                        id = "0xEXISTING",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 1,
                        availableStartBlock = 0,
                        version = 1,
                    )
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, existingDocs)

        // Existing validator keeps position 1, new validators added to end
        assertThat(result["0xEXISTING"]!!.position).isEqualTo(1)
        assertThat(result["0xNEW1"]!!.position).isEqualTo(2)
        assertThat(result["0xNEW2"]!!.position).isEqualTo(3)
    }

    @Test
    fun `calculateQueueInfo shifts positions when validator changes status`() {
        // Validator Q1 (was position 1) has left the queue (now ACTIVE)
        val rows =
            listOf(
                buildRow("0xQ1", BigInteger.TWO), // Now ACTIVE, no longer queued
                buildRow("0xQ2", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xQ3", BigInteger.ONE, startBlock = BigInteger.ZERO),
            )
        val existingDocs =
            mapOf(
                "0xQ1" to
                    Validator(
                        id = "0xQ1",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 1,
                        availableStartBlock = 100,
                        version = 1,
                    ),
                "0xQ2" to
                    Validator(
                        id = "0xQ2",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 2,
                        availableStartBlock = 0,
                        version = 1,
                    ),
                "0xQ3" to
                    Validator(
                        id = "0xQ3",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 3,
                        availableStartBlock = 0,
                        version = 1,
                    ),
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, existingDocs)

        // Q1 is no longer in queue, Q2 moves to position 1, Q3 moves to position 2
        assertThat(result).doesNotContainKey("0xQ1")
        assertThat(result["0xQ2"]!!.position).isEqualTo(1)
        assertThat(result["0xQ3"]!!.position).isEqualTo(2)
    }

    @Test
    fun `calculateQueueInfo shifts positions when validator disappears completely`() {
        // Validator Q1 (was position 1) has disappeared from the list entirely
        val rows =
            listOf(
                // Q1 is completely gone - not in list at all
                buildRow("0xQ2", BigInteger.ONE, startBlock = BigInteger.ZERO),
                buildRow("0xQ3", BigInteger.ONE, startBlock = BigInteger.ZERO),
            )
        val existingDocs =
            mapOf(
                "0xQ1" to
                    Validator(
                        id = "0xQ1",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 1,
                        availableStartBlock = 100,
                        version = 1,
                    ),
                "0xQ2" to
                    Validator(
                        id = "0xQ2",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 2,
                        availableStartBlock = 0,
                        version = 1,
                    ),
                "0xQ3" to
                    Validator(
                        id = "0xQ3",
                        blockId = "old",
                        blockNumber = 19,
                        blockTimestamp = 123,
                        status = Status.QUEUED,
                        queuePosition = 3,
                        availableStartBlock = 0,
                        version = 1,
                    ),
            )

        val result = ValidatorAssembler.calculateQueueInfo(rows, existingDocs)

        // Q1 disappeared, Q2 moves to position 1, Q3 moves to position 2
        assertThat(result).doesNotContainKey("0xQ1")
        assertThat(result["0xQ2"]!!.position).isEqualTo(1)
        assertThat(result["0xQ3"]!!.position).isEqualTo(2)
    }

    @Test
    fun `QUEUED validator has queuePosition set`() {
        val decoded =
            buildDecoded().toMutableMap().apply {
                put("statuses", listOf(BigInteger.ONE)) // QUEUED
                put("startBlocks", listOf(BigInteger.valueOf(100)))
            }

        val validators =
            ValidatorAssembler.unpackValidators(
                decoded,
                emptyMap(),
                BigInteger.valueOf(100),
                BigInteger("1000000000000"),
                BigInteger("1000000000000"),
                "0xBLOCK",
                20,
                1234567890,
            )

        assertThat(validators.first().queuePosition).isEqualTo(1)
        assertThat(validators.first().availableStartBlock).isEqualTo(100)
    }

    @Test
    fun `ACTIVE validator has null queuePosition`() {
        val decoded = buildDecoded() // status is ACTIVE (2) by default

        val validators =
            ValidatorAssembler.unpackValidators(
                decoded,
                emptyMap(),
                BigInteger.valueOf(100),
                BigInteger("1000000000000"),
                BigInteger("1000000000000"),
                "0xBLOCK",
                20,
                1234567890,
            )

        assertThat(validators.first().queuePosition).isNull()
        assertThat(validators.first().availableStartBlock).isNull()
    }

    @Test
    fun `disappeared validator has null queuePosition`() {
        val decoded = buildDecoded()
        val existing =
            Validator(
                id = "0xOLD",
                blockId = "oldBlock",
                blockNumber = 19,
                blockTimestamp = 123,
                status = Status.QUEUED,
                queuePosition = 1,
                availableStartBlock = 100,
                version = 1,
            )

        val validators =
            ValidatorAssembler.unpackValidators(
                decoded,
                mapOf("0xOLD" to existing),
                BigInteger.ONE,
                BigInteger("1000000000000"),
                BigInteger.ONE,
                "0xBLOCK",
                20,
                1234567890,
            )

        val disappeared = validators.firstOrNull { it.id == "0xOLD" }
        assertThat(disappeared!!.status).isEqualTo(Status.EXITED)
        assertThat(disappeared.queuePosition).isNull()
        assertThat(disappeared.availableStartBlock).isNull()
    }

    @Test
    fun `EXITING validator has exitBlock set`() {
        val decoded =
            buildDecoded().toMutableMap().apply {
                put("exitBlocks", listOf(BigInteger.valueOf(1000)))
            }

        val validators =
            ValidatorAssembler.unpackValidators(
                decoded,
                emptyMap(),
                BigInteger.TEN,
                BigInteger("1000000000000"),
                BigInteger.TEN,
                "0xBLOCK",
                20,
                1234567890,
            )

        assertThat(validators.first().exitBlock).isEqualTo(1000)
    }
}
