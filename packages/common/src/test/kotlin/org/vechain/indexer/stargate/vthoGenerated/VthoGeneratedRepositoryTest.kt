package org.vechain.indexer.stargate.vthoGenerated

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.timeseries.TimeFramePeriod

/** The series' write and read sides; the shared paging is covered by the VET-delegated test. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VthoGeneratedRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: VthoGeneratedWriteRepository
    private lateinit var reader: VthoGeneratedReadRepository

    @BeforeAll
    fun start() {
        database.start()
        writer = VthoGeneratedWriteRepository(database.jdbc)
        reader = VthoGeneratedReadRepository(database.jdbc)
        writer.save(
            listOf(record(10, 100), record(20, 200, listOf(TimeFrame.HOUR)), record(30, 300))
        )
    }

    @AfterAll fun stop() = database.close()

    private fun record(block: Long, total: Long, frames: List<TimeFrame> = emptyList()) =
        VthoGeneratedByBlock(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            total = BigInteger.TEN.pow(24) + BigInteger.valueOf(total),
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
                ),
        )

    private fun blocks(rows: List<VthoGeneratedByBlock>) = rows.map { it.blockNumber }

    @Test
    fun `every field survives the round trip`() {
        assertEquals(record(30, 300), reader.getLatestRecord())
        assertEquals(
            record(20, 200, listOf(TimeFrame.HOUR)),
            reader.findLatestBeforeOrAtBlockNumber(25),
        )
    }

    @Test
    fun `the historic series is every row after the timestamp, oldest first`() {
        assertEquals(listOf(20L, 30L), blocks(reader.findByBlockTimestampAfter(100)))
        assertEquals(
            listOf(20L),
            blocks(reader.findByTimeFramesContainsAndBlockTimestampAfter(TimeFrame.HOUR, 100)),
        )
        assertEquals(
            listOf(30L, 20L),
            blocks(
                reader
                    .findByBlockTimestampAfter(
                        100,
                        PageRequest.of(0, 5, Sort.by(DESC, "blockTimestamp")),
                    )
                    .content
            ),
        )
    }

    @Test
    fun `the writer resumes from the newest row, rolls back by block and truncates`() {
        val scratch = VthoGeneratedWriteRepository(database.jdbc)
        assertEquals(30L, scratch.latest()?.blockNumber)
        scratch.rollbackFrom(30)
        assertEquals(20L, scratch.latest()?.blockNumber)
        scratch.truncate()
        assertNull(scratch.latest())
        writer.save(
            listOf(record(10, 100), record(20, 200, listOf(TimeFrame.HOUR)), record(30, 300))
        )
    }
}
