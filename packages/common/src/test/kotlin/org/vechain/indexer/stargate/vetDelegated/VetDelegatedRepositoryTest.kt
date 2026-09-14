package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.timeseries.TimeFramePeriod

/** The series' write and read sides on a seeded set; see [seed] for which blocks rolled what. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VetDelegatedRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: VetDelegatedWriteRepository
    private lateinit var reader: VetDelegatedReadRepository

    @BeforeAll
    fun start() {
        database.start()
        writer = VetDelegatedWriteRepository(database.jdbc)
        reader = VetDelegatedReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun record(block: Long, total: Long, frames: List<TimeFrame> = emptyList()) =
        VetDelegatedByBlock(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            total = BigInteger.valueOf(total),
            byLevel =
                mapOf(
                    TokenLevel.Dawn to BigInteger.valueOf(total),
                    TokenLevel.MjolnirX to BigInteger("15600000000000000000000000"),
                ),
            totalNftCount = 2,
            nftCountByLevel = mapOf(TokenLevel.Dawn to 1, TokenLevel.MjolnirX to 1),
            period =
                TimeFramePeriod(
                    hourOfDay = 12,
                    dayOfMonth = 25,
                    weekOfYear = 43,
                    month = 10,
                    year = 2025,
                    timeFrames = frames,
                    blockTotal = BigInteger.ONE,
                    hourTotal = BigInteger.TWO,
                    dayTotal = BigInteger.valueOf(3),
                    weekTotal = BigInteger.valueOf(4),
                    monthTotal = BigInteger.valueOf(5),
                    yearTotal = BigInteger.valueOf(6),
                ),
        )

    /** Blocks 10, 20 (rolled HOUR), 30 (rolled HOUR and DAY) and 40; timestamps are block × 10. */
    private fun seed() {
        writer.save(
            listOf(
                record(10, 100),
                record(20, 200, listOf(TimeFrame.HOUR)),
                record(30, 300, listOf(TimeFrame.HOUR, TimeFrame.DAY)),
                record(40, 400),
            )
        )
    }

    private fun blocks(rows: List<VetDelegatedByBlock>) = rows.map { it.blockNumber }

    @Test
    fun `every field survives the round trip, per-level maps included`() {
        assertEquals(record(40, 400), reader.getLatestRecord())
    }

    @Test
    fun `at-or-before lookups take the newest row that qualifies`() {
        assertEquals(30L, reader.findLatestBeforeOrAtBlockNumber(35)?.blockNumber)
        assertEquals(40L, reader.findLatestBeforeOrAtBlockNumber(40)?.blockNumber)
        assertNull(reader.findLatestBeforeOrAtBlockNumber(9))
        assertEquals(20L, reader.findLatestBeforeOrAtBlockTimestamp(250)?.blockNumber)
        assertNull(reader.findLatestBeforeOrAtBlockTimestamp(99))
    }

    @Test
    fun `the unpaged series is every row, the framed one only what rolled that period`() {
        val page = PageRequest.of(0, 10, Sort.by(ASC, "blockTimestamp"))
        assertEquals(listOf(10L, 20L, 30L, 40L), blocks(reader.findAll(page).content))
        assertEquals(
            listOf(20L, 30L),
            blocks(reader.findByTimeFramesContains(TimeFrame.HOUR, page).content),
        )
        assertEquals(
            listOf(30L),
            blocks(reader.findByTimeFramesContains(TimeFrame.DAY, page).content),
        )
        assertEquals(
            emptyList<Long>(),
            blocks(reader.findByTimeFramesContains(TimeFrame.YEAR, page).content),
        )
    }

    @Test
    fun `the page direction follows the sort and hasNext comes from the extra row`() {
        val desc = reader.findAll(PageRequest.of(0, 2, Sort.by(DESC, "blockTimestamp")))
        assertEquals(listOf(40L, 30L), blocks(desc.content))
        assertTrue(desc.hasNext())

        val last = reader.findAll(PageRequest.of(1, 2, Sort.by(ASC, "blockTimestamp")))
        assertEquals(listOf(30L, 40L), blocks(last.content))
        assertTrue(!last.hasNext())
    }

    @Test
    fun `timestamp bounds are exclusive, with and without a frame`() {
        val page = PageRequest.of(0, 10, Sort.by(ASC, "blockTimestamp"))
        assertEquals(listOf(30L, 40L), blocks(reader.findByBlockTimestampAfter(200, page).content))
        assertEquals(listOf(10L), blocks(reader.findByBlockTimestampBefore(200, page).content))
        assertEquals(
            listOf(20L, 30L),
            blocks(reader.findByBlockTimestampBetween(100, 400, page).content),
        )
        assertEquals(
            listOf(30L),
            blocks(
                reader
                    .findByTimeFramesContainsAndBlockTimestampAfter(TimeFrame.HOUR, 200, page)
                    .content
            ),
        )
        assertEquals(
            listOf(20L),
            blocks(
                reader
                    .findByTimeFramesContainsAndBlockTimestampBefore(TimeFrame.HOUR, 300, page)
                    .content
            ),
        )
        assertEquals(
            listOf(20L),
            blocks(
                reader
                    .findByTimeFramesContainsAndBlockTimestampBetween(
                        TimeFrame.HOUR,
                        100,
                        300,
                        page,
                    )
                    .content
            ),
        )
    }

    @Test
    fun `the writer resumes from the newest row, replays in place and rolls back by block`() {
        assertEquals(40L, writer.latest()?.blockNumber)

        writer.save(listOf(record(40, 444)))
        assertEquals(BigInteger.valueOf(444), writer.latest()?.total)

        writer.rollbackFrom(30)
        assertEquals(20L, writer.latest()?.blockNumber)

        writer.save(listOf(record(30, 300, listOf(TimeFrame.HOUR, TimeFrame.DAY)), record(40, 400)))
    }

    @Test
    fun `truncate empties the table`() {
        val scratch = VetDelegatedWriteRepository(database.jdbc)
        scratch.save(listOf(record(50, 500)))
        scratch.truncate()
        assertNull(scratch.latest())
        seed()
    }
}
