package org.vechain.indexer.b3tr.xAlloc

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XAllocResultRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: XAllocResultWriteRepository
    private lateinit var reader: XAllocResultReadRepository

    private val app1 = "0x" + "11".repeat(32)
    private val app2 = "0x" + "22".repeat(32)

    @BeforeAll
    fun start() {
        database.start()
        writer = XAllocResultWriteRepository(database.jdbc)
        reader = XAllocResultReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    /** Round 1 gives both apps votes and only app1 earnings; round 2 repeats app1. */
    private fun seed() {
        writer.save(
            listOf(
                result(1, app1, 10, votes = 5),
                result(1, app2, 10, votes = 9),
                result(1, app1, 20, votes = 5, total = "3.5"),
                result(2, app1, 30, votes = 1, total = "1.0"),
            )
        )
    }

    private fun result(
        roundId: Int,
        appId: String,
        block: Long,
        votes: Long,
        total: String? = null,
    ) =
        XAllocResult(
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            roundId = roundId,
            appId = appId,
            voters = votes,
            votesReceived = BigInteger.valueOf(votes * 10),
            totalAmount = total?.let { BigDecimal(it).setScale(18) },
        )

    @Test
    fun `a result round-trips and only its newest row is current`() {
        val current = reader.findByAppIdAndRoundId(app1, 1)!!
        assertEquals(20L, current.blockNumber)
        assertEquals(BigDecimal("3.500000000000000000"), current.totalAmount)
        assertNull(current.unallocatedAmount)
        assertNull(reader.findByAppIdAndRoundId(app2, 2))
    }

    @Test
    fun `a round lists its apps by votes, and its earners by amount`() {
        assertEquals(listOf(app2, app1), reader.findByRoundId(1).map { it.appId })
        assertEquals(listOf(app1), reader.findEarningsByRoundId(1).map { it.appId })
        assertEquals(listOf(1, 2), reader.findEarningsByAppId(app1).map { it.roundId })
        assertEquals(emptyList<XAllocResult>(), reader.findEarningsByAppId(app2))
    }

    @Test
    fun `the indexer reads the current row of every round and app it touches`() {
        assertEquals(
            listOf(1 to app1, 1 to app2, 2 to app1),
            writer
                .findCurrent(setOf(1, 2), setOf(app1, app2))
                .map { it.roundId to it.appId }
                .sortedWith(compareBy({ it.first }, { it.second })),
        )
        assertEquals(emptyList<XAllocResult>(), writer.findCurrent(emptySet(), setOf(app1)))
    }

    @Test
    fun `the indexer's own read still works with the page indexes dropped`() {
        val builder = IndexBuilder(database.properties)
        builder.drop(XAllocResultIndexes.SET)
        try {
            writer.save(listOf(result(2, app1, 40, votes = 7, total = "2.0")))

            assertEquals(
                listOf(40L),
                writer.findCurrent(setOf(2), setOf(app1)).map { it.blockNumber },
            )

            writer.rollbackFrom(40)
            assertEquals(
                listOf(30L),
                writer.findCurrent(setOf(2), setOf(app1)).map { it.blockNumber },
            )
        } finally {
            builder.build(XAllocResultIndexes.SET)
        }
    }

    @Test
    fun `rollback reopens the superseded row and truncate empties the table`() {
        writer.rollbackFrom(20)
        assertEquals(10L, reader.findByAppIdAndRoundId(app1, 1)?.blockNumber)
        assertEquals(10L, reader.latestBlockNumber())

        writer.truncate()
        assertEquals(0L, reader.latestBlockNumber())
        seed()
    }

    @Test
    fun `prune drops superseded rows below the horizon and reports how many`() {
        assertEquals(0, writer.prune(20))
        assertEquals(1, writer.prune(21))
        assertEquals(20L, reader.findByAppIdAndRoundId(app1, 1)?.blockNumber)
        writer.truncate()
        seed()
    }
}
