package org.vechain.indexer.validator

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.timeseries.TimeSeriesResolution

/** One test per API read on a seeded ledger; see [seed] for who signed and missed what. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidatorBlockReadRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: ValidatorBlockReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)

    @BeforeAll
    fun start() {
        database.start()
        repository = ValidatorBlockReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun row(
        validator: String,
        block: Long,
        status: BlockStatus = BlockStatus.VALIDATED,
        hourly: Boolean? = null,
        daily: Boolean? = null,
    ) =
        ValidatorBlock(
            id = ValidatorBlockRowMapping.id(block, validator, status),
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            validator = validator,
            total = if (status == BlockStatus.VALIDATED) BigInteger.valueOf(block) else null,
            status = status,
            isHourly = hourly,
            isDaily = daily,
        )

    /**
     * alice signs 10, 20 (hourly), 30 (hourly, daily) and 40, missing at 25 and 35; bob signs 25
     * and misses at 30. Timestamps are ten times the block number.
     */
    private fun seed() {
        ValidatorBlockWriteRepository(database.jdbc)
            .save(
                listOf(
                    row(alice, 10),
                    row(alice, 20, hourly = true),
                    row(alice, 25, BlockStatus.MISSED),
                    row(bob, 25),
                    row(alice, 30, hourly = true, daily = true),
                    row(bob, 30, BlockStatus.MISSED),
                    row(alice, 35, BlockStatus.MISSED),
                    row(alice, 40),
                )
            )
    }

    private fun keys(rows: List<ValidatorBlock>) = rows.map { it.id }

    @Test
    fun `the unfiltered page is newest first and pages by offset`() {
        val page = repository.findRewards(null, null, null, Direction.DESC, 0, 3)
        assertEquals(listOf("40-$alice", "35-$alice-MISSED", "30-$alice"), keys(page))
        assertEquals(
            listOf("30-$bob-MISSED", "25-$alice-MISSED"),
            keys(repository.findRewards(null, null, null, Direction.DESC, 3, 2)),
        )
    }

    @Test
    fun `a block number bounds the page from above descending and from below ascending`() {
        assertEquals(
            listOf("25-$alice-MISSED", "25-$bob", "20-$alice", "10-$alice"),
            keys(repository.findRewards(null, 25, null, Direction.DESC, 0, 10)),
        )
        assertEquals(
            listOf("35-$alice-MISSED", "40-$alice"),
            keys(repository.findRewards(null, 35, null, Direction.ASC, 0, 10)),
        )
    }

    @Test
    fun `validator and status filters combine`() {
        assertEquals(
            listOf("35-$alice-MISSED", "25-$alice-MISSED"),
            keys(repository.findRewards(alice, null, BlockStatus.MISSED, Direction.DESC, 0, 10)),
        )
        assertEquals(
            listOf("25-$bob"),
            keys(repository.findRewards(bob, null, BlockStatus.VALIDATED, Direction.DESC, 0, 10)),
        )
    }

    @Test
    fun `findByBlockNumber returns every row of the block or one validator's`() {
        assertEquals(
            listOf("30-$alice", "30-$bob-MISSED"),
            keys(repository.findByBlockNumber(30, null)),
        )
        assertEquals(listOf("30-$bob-MISSED"), keys(repository.findByBlockNumber(30, bob)))
        assertEquals(emptyList<String>(), keys(repository.findByBlockNumber(99, null)))
    }

    @Test
    fun `the historic series is VALIDATED only, raw or sampled by flag`() {
        assertEquals(
            listOf("20-$alice", "30-$alice", "40-$alice"),
            keys(repository.findValidatedInRange(alice, 200, 400, TimeSeriesResolution.RAW)),
        )
        assertEquals(
            listOf("20-$alice", "30-$alice"),
            keys(repository.findValidatedInRange(alice, 0, 1_000, TimeSeriesResolution.HOURLY)),
        )
        assertEquals(
            listOf("30-$alice"),
            keys(repository.findValidatedInRange(alice, 0, 1_000, TimeSeriesResolution.DAILY)),
        )
    }

    @Test
    fun `the bookend is the newest VALIDATED row at or before a timestamp`() {
        assertEquals("30-$alice", repository.findLatestValidatedAtOrBefore(alice, 349)?.id)
        assertEquals("40-$alice", repository.findLatestValidatedAtOrBefore(alice, 400)?.id)
        assertNull(repository.findLatestValidatedAtOrBefore(alice, 99))
    }

    @Test
    fun `slot stats count proposed and missed per validator, worst ratio first`() {
        assertEquals(
            listOf(
                ValidatorSlotStats(bob, proposedBlocks = 1, missedSlots = 1, missedSlotRatio = 0.5),
                ValidatorSlotStats(
                    alice,
                    proposedBlocks = 4,
                    missedSlots = 2,
                    missedSlotRatio = 2.0 / 6,
                ),
            ),
            repository.slotStats(0, 1_000),
        )
        assertEquals(
            listOf(
                ValidatorSlotStats(
                    alice,
                    proposedBlocks = 2,
                    missedSlots = 1,
                    missedSlotRatio = 1.0 / 3,
                )
            ),
            repository.slotStats(200, 300, alice),
        )
        assertEquals(
            emptyList<ValidatorSlotStats>(),
            repository.slotStats(200, 300, "0x" + "c".repeat(40)),
        )
    }
}
