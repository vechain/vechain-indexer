package org.vechain.indexer.explorer

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExplorerRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: ExplorerWriteRepository
    private lateinit var usage: BlockUsageReadRepository
    private lateinit var fees: AverageFeesPerUserReadRepository

    private val alice = "0x" + "a".repeat(40)
    private val bob = "0x" + "b".repeat(40)
    private val day = 1_704_067_200L

    @BeforeAll
    fun start() {
        database.start()
        writer = ExplorerWriteRepository(database.jdbc)
        usage = BlockUsageReadRepository(database.jdbc)
        fees = AverageFeesPerUserReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Three blocks on one day; the middle one opens an hour, the last one a day. */
    private fun seed() {
        writer.save(
            blockUsage(10, day, emptyList()),
            summary(10, day, "1", 1),
            origins(10, day, alice),
        )
        writer.save(
            blockUsage(20, day + 3600, listOf(TimeFrame.HOUR)),
            summary(20, day, "3", 2),
            origins(20, day, alice, bob),
        )
        writer.save(
            blockUsage(30, day + 86400, listOf(TimeFrame.HOUR, TimeFrame.DAY)),
            summary(30, day + 86400, "5", 1),
            origins(30, day + 86400, alice),
        )
    }

    private fun blockUsage(block: Long, timestamp: Long, frames: List<TimeFrame>) =
        BlockUsage(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = timestamp,
            cumulativeGasLimit = BigInteger.valueOf(block * 100),
            cumulativeGasUsed = BigInteger.valueOf(block * 10),
            cumulativeBaseFeePerGas = if (block == 10L) null else BigInteger.valueOf(block),
            cumulativeNumTransactions = BigInteger.valueOf(block),
            cumulativeNumClauses = BigInteger.valueOf(block * 2),
            timeFrames = frames,
        )

    private fun summary(block: Long, dayStart: Long, total: String, users: Long) =
        AverageFeesPerUser(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = dayStart,
            date = if (dayStart == day) "2024-01-01" else "2024-01-02",
            dayStartTimestamp = dayStart,
            totalFeesPaid = BigDecimal(total).setScale(18),
            dailyActiveUsers = users,
            averageFeesPerUser = BigDecimal(total).setScale(12),
        )

    private fun origins(block: Long, dayStart: Long, vararg addresses: String) = addresses.map {
        DailyActiveOrigin(dayStart, it, block)
    }

    @Test
    fun `block usage round-trips and is sampled by frame or by every block`() {
        assertEquals(
            listOf(10L, 20L, 30L),
            usage.findAllInTimestampRange(day, day + 86400).map { it.blockNumber },
        )
        assertEquals(
            listOf(20L, 30L),
            usage.findFrameInTimestampRange(TimeFrame.HOUR, day, day + 86400).map {
                it.blockNumber
            },
        )
        assertEquals(
            listOf(TimeFrame.HOUR, TimeFrame.DAY),
            usage.findFrameInTimestampRange(TimeFrame.DAY, day, day + 86400).single().timeFrames,
        )
        assertEquals(blockUsage(10, day, emptyList()), usage.findLatestAtOrBefore(day))
        assertNull(usage.findLatestAtOrBefore(day - 1))
    }

    @Test
    fun `only a day's newest summary is current, and a window returns one per day`() {
        assertEquals(
            listOf(20L, 30L),
            fees.findBetween(day, day + 86400).map { it.blockNumber },
        )
        assertEquals(summary(20, day, "3", 2), writer.findCurrentFees(day))
        assertNull(writer.findCurrentFees(day - 86400))
    }

    @Test
    fun `the known origins of a day are the ones already written`() {
        assertEquals(setOf(alice, bob), writer.findKnownOrigins(day, setOf(alice, bob)))
        assertEquals(setOf(alice), writer.findKnownOrigins(day + 86400, setOf(alice, bob)))
        assertEquals(emptySet<String>(), writer.findKnownOrigins(day, emptySet()))
    }

    @Test
    fun `rollback undoes all three tables together and truncate empties them`() {
        writer.rollbackFrom(20)
        assertEquals(
            listOf(10L),
            usage.findAllInTimestampRange(day, day + 86400).map { it.blockNumber },
        )
        assertEquals(10L, writer.findCurrentFees(day)?.blockNumber)
        assertEquals(setOf(alice), writer.findKnownOrigins(day, setOf(alice, bob)))
        assertEquals(emptySet<String>(), writer.findKnownOrigins(day + 86400, setOf(alice)))

        writer.truncate()
        assertNull(writer.findCurrentFees(day))
        assertEquals(emptyList<BlockUsage>(), usage.findAllInTimestampRange(0, day + 86400))
        seed()
    }

    @Test
    fun `prune drops superseded summaries below the horizon and reports how many`() {
        assertEquals(0, writer.prune(20))
        assertEquals(1, writer.prune(21))
        assertEquals(20L, writer.findCurrentFees(day)?.blockNumber)
        writer.truncate()
        seed()
    }
}
