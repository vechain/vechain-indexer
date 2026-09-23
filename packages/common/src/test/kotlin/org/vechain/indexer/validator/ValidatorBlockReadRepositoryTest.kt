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
    private val carol = "0x" + "c".repeat(40)
    private val dave = "0x" + "d".repeat(40)
    private val erin = "0x" + "e".repeat(40)
    private val frank = "0x" + "f".repeat(40)

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
     * and misses at 30. Timestamps are ten times the block number; later rows serve slot stats.
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
                    row(carol, 1000),
                    row(dave, 1005),
                    row(carol, 1010, BlockStatus.MISSED),
                    row(dave, 1020, BlockStatus.MISSED),
                    row(erin, 1050),
                    row(erin, 1060),
                    row(frank, 2000),
                    row(frank, 2010, BlockStatus.MISSED),
                    row(frank, 2500),
                    row(frank, 2600),
                )
            )
        ValidatorWriteRepository(database.jdbc)
            .save(
                listOf(alice, bob, carol, dave, erin, frank).map {
                    Validator(
                        id = it,
                        blockId = "0x" + "0".repeat(64),
                        blockNumber = 1,
                        blockTimestamp = 10,
                        status = if (it == dave) Status.EXITED else Status.ACTIVE,
                        exitBlock = if (it == dave) 1050 else null,
                    )
                }
            )
    }

    private fun keys(rows: List<ValidatorBlock>) = rows.map { it.id }

    @Test
    fun `the unfiltered page is newest first and pages by offset`() {
        val page = repository.findRewards(null, null, null, Direction.DESC, 0, 3)
        assertEquals(listOf("2600-$frank", "2500-$frank", "2010-$frank-MISSED"), keys(page))
        assertEquals(
            listOf("2000-$frank", "1060-$erin"),
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
            keys(repository.findRewards(null, 35, null, Direction.ASC, 0, 2)),
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
    fun `slot stats count slots and time offline per validator, least uptime first`() {
        assertEquals(
            listOf(
                ValidatorSlotStats(
                    bob,
                    1,
                    1,
                    missedSlotRatio = 0.5,
                    uptimeRatio = 1.0 - 700.0 / 1_000,
                ),
                ValidatorSlotStats(
                    alice,
                    4,
                    2,
                    missedSlotRatio = 2.0 / 6,
                    uptimeRatio = 1.0 - 100.0 / 1_000,
                ),
            ),
            repository.slotStats(0, 1_000),
        )
        assertEquals(
            listOf(
                ValidatorSlotStats(
                    alice,
                    2,
                    1,
                    missedSlotRatio = 1.0 / 3,
                    uptimeRatio = 1.0 - 50.0 / 100,
                )
            ),
            repository.slotStats(200, 300, alice),
        )
        assertEquals(
            emptyList<ValidatorSlotStats>(),
            repository.slotStats(200, 300, "0x" + "9".repeat(40)),
        )
    }

    @Test
    fun `an outage costs its whole duration however few slots it missed`() {
        assertEquals(
            listOf(
                ValidatorSlotStats(
                    frank,
                    3,
                    1,
                    missedSlotRatio = 0.25,
                    uptimeRatio = 1.0 - 4_900.0 / 6_000,
                )
            ),
            repository.slotStats(20_000, 26_000, frank),
        )
        assertEquals(
            listOf(ValidatorSlotStats(frank, 1, 0, missedSlotRatio = 0.0, uptimeRatio = 1.0)),
            repository.slotStats(25_500, 26_000, frank),
        )
    }

    @Test
    fun `an outage open at the window start counts until the validator returns or exits`() {
        assertEquals(
            listOf(
                ValidatorSlotStats(bob, 0, 0, missedSlotRatio = 0.0, uptimeRatio = 0.0),
                ValidatorSlotStats(carol, 0, 0, missedSlotRatio = 0.0, uptimeRatio = 0.0),
                ValidatorSlotStats(
                    dave,
                    0,
                    0,
                    missedSlotRatio = 0.0,
                    uptimeRatio = 1.0 - 200.0 / 300,
                ),
                ValidatorSlotStats(erin, 2, 0, missedSlotRatio = 0.0, uptimeRatio = 1.0),
            ),
            repository.slotStats(10_300, 10_600),
        )
        assertEquals(
            listOf(ValidatorSlotStats(carol, 0, 0, missedSlotRatio = 0.0, uptimeRatio = 0.0)),
            repository.slotStats(10_300, 10_600, carol),
        )
    }
}
