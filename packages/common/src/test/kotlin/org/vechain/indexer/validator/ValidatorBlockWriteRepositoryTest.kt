package org.vechain.indexer.validator

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.timeseries.TimeSeriesResolution

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidatorBlockWriteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ValidatorBlockWriteRepository
    private lateinit var reader: ValidatorBlockReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        writer = ValidatorBlockWriteRepository(database.jdbc)
        reader = ValidatorBlockReadRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun reset() = writer.truncate()

    private fun validated(validator: String, block: Long, hourly: Boolean? = null) =
        ValidatorBlock(
            id = "$block-$validator",
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            validator = validator,
            blockReward =
                BigInteger(
                    "1000000000000000000000000000000000000000000000000000000000000000000000000000000"
                ) - BigInteger.ONE,
            priorityReward = BigInteger.TEN,
            total = BigInteger("11"),
            status = BlockStatus.VALIDATED,
            delegatorRewards = BigInteger.ZERO,
            validatorRewards = BigInteger("11"),
            isHourly = hourly,
        )

    private fun missed(validator: String, block: Long) =
        ValidatorBlock(
            id = "$block-$validator-MISSED",
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            validator = validator,
            status = BlockStatus.MISSED,
        )

    private fun all(): List<ValidatorBlock> =
        database.jdbc.query(
            "SELECT * FROM validator_block.slot ORDER BY block_number, validator, status"
        ) { rs, _ ->
            ValidatorBlockRowMapping.read(rs)
        }

    @Test
    fun `rows survive the round trip with the id rebuilt and a 78-digit reward intact`() {
        val rows = listOf(validated(alice, 10, hourly = true), missed(bob, 10), validated(bob, 11))

        writer.save(rows)

        assertEquals(rows, all())
    }

    @Test
    fun `a replayed block upserts in place`() {
        writer.save(listOf(validated(alice, 10)))
        writer.save(listOf(validated(alice, 10, hourly = true), validated(alice, 11)))

        assertEquals(listOf(validated(alice, 10, hourly = true), validated(alice, 11)), all())
    }

    @Test
    fun `rollback removes the block and everything after it`() {
        writer.save(listOf(validated(alice, 10), validated(alice, 11), missed(bob, 12)))

        writer.rollbackFrom(11)

        assertEquals(listOf(validated(alice, 10)), all())
    }

    @Test
    fun `latestSampled is each validator's newest flagged VALIDATED timestamp`() {
        writer.save(
            listOf(
                validated(alice, 10, hourly = true),
                validated(alice, 20, hourly = true),
                validated(alice, 30),
                validated(bob, 25, hourly = true),
                missed(bob, 40),
            )
        )

        assertEquals(
            mapOf(alice to 200L, bob to 250L),
            writer.latestSampled(TimeSeriesResolution.HOURLY),
        )
        assertEquals(emptyMap<String, Long>(), writer.latestSampled(TimeSeriesResolution.DAILY))
    }

    @Test
    fun `the sampled lookup survives the deferrable indexes being dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(ValidatorBlockIndexes.SET)
        try {
            writer.save(listOf(validated(alice, 10, hourly = true), missed(bob, 20)))

            assertEquals(mapOf(alice to 100L), writer.latestSampled(TimeSeriesResolution.HOURLY))

            writer.rollbackFrom(20)
            assertEquals(listOf(10L), all().map { it.blockNumber })
        } finally {
            builder.build(ValidatorBlockIndexes.SET)
        }
    }

    @Test
    fun `truncate empties the table and prune is a no-op`() {
        writer.save(listOf(validated(alice, 10)))
        assertEquals(0, writer.prune(before = 100))
        writer.truncate()
        assertTrue(all().isEmpty())
    }
}
