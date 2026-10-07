package org.vechain.indexer.wov.marketplace

import java.math.BigInteger
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.PostgresTestDatabase

/** The buyer page against a production-shaped schema: 12k buyers, ~99k running rows. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WovMarketplacePlanTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: WovMarketplaceWriteRepository
    private lateinit var reader: WovMarketplaceReadRepository

    private val tokens =
        listOf("0x" + "00".repeat(20), "0x" + "11".repeat(20), "0x" + "22".repeat(20))

    @BeforeAll
    fun start() {
        database.start()
        writer = WovMarketplaceWriteRepository(database.jdbc)
        reader = WovMarketplaceReadRepository(database.jdbc)
        seed()
        database.jdbc.execute("ANALYZE wov_marketplace.buyer")
        database.jdbc.execute("ANALYZE wov_marketplace.buyer_running")
    }

    @AfterAll fun stop() = database.close()

    private fun buyer(n: Int) = "0x" + n.toString(16).padStart(40, '0')

    /** Buyer n first buys at block 1000(n-1) and again every 750k blocks, rotating the token. */
    private fun purchases(n: Int): List<Long> =
        generateSequence((n - 1) * 1_000L) { it + STEP }.takeWhile { it < BLOCKS }.toList()

    private fun token(block: Long) = tokens[(block / STEP % 3).toInt()]

    /** Spread evenly over the range, so no whale or hot token skews the planner's statistics. */
    private fun seed() {
        (1..BUYERS).chunked(1_000).forEach { chunk ->
            val running = mutableListOf<WovBuyerRunning>()
            val buyers = mutableListOf<WovBuyer>()
            chunk.forEach { n ->
                val totals = mutableMapOf<String, Pair<Long, Long>>()
                purchases(n).forEach { block ->
                    val (items, spend) = totals[token(block)] ?: (0L to 0L)
                    totals[token(block)] = (items + 1) to (spend + 10)
                    running +=
                        WovBuyerRunning(
                            buyer(n),
                            token(block),
                            block,
                            block * 10,
                            items + 1,
                            BigInteger.valueOf(spend + 10),
                        )
                }
                val first = purchases(n).first()
                buyers += WovBuyer(buyer(n), first, first * 10)
            }
            writer.save(
                WovMarketplaceEntry(BLOCKS, BLOCKS * 10, running = running, buyers = buyers)
            )
        }
    }

    private fun plan(from: Long, to: Long): String =
        database.dataSource.connection.use { c ->
            c.prepareStatement(
                    "EXPLAIN (ANALYZE, FORMAT TEXT) " +
                        PAGE_SQL.replace("?", "%s").format(to, to, from, "'\\x'::bytea", to, PAGE)
                )
                .executeQuery()
                .use { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.joinToString("\n")
                }
        }

    @Test
    fun `a page walks the buyer key and seeks each edge, with no sort over the buyers`() {
        val windows =
            mapOf(
                "lifetime" to (0L to BLOCKS * 10),
                "30 days" to (BLOCKS * 10 - 30 * DAY to BLOCKS * 10),
                "24 hours" to (BLOCKS * 10 - DAY to BLOCKS * 10),
            )
        windows.forEach { (name, window) ->
            val (from, to) = window
            val plan = plan(from, to)
            assertTrue(plan.contains("Index Scan using buyer_pkey"), "$name:\n$plan")
            assertTrue(plan.contains("buyer_running_ts_idx"), "$name:\n$plan")
            assertTrue(!plan.contains("Sort"), "$name sorts:\n$plan")
            val ms = Regex("Execution Time: ([0-9.]+) ms").find(plan)!!.groupValues[1].toDouble()
            println("wov buyers page, $name window: $ms ms")
            assertTrue(ms < 1_000, "$name took $ms ms:\n$plan")
        }
    }

    @Test
    fun `the seeded totals come back exactly`() {
        val page = reader.buyers(0, BLOCKS * 10, null, PAGE)

        assertEquals(PAGE, page.size)
        assertEquals(buyer(1), page.first().buyer)
        assertEquals(purchases(1).size.toLong(), page.first().items)
        assertEquals(purchases(1).size * 10L, page.first().spend.sumOf { it.spend.toLong() })
        assertEquals(3, page.first().spend.size)
    }

    private companion object {
        const val BUYERS = 12_000
        const val BLOCKS = 12_000_000L
        const val STEP = 750_000L
        const val DAY = 86_400L
        const val PAGE = 100
        val PAGE_SQL = WovMarketplaceReadRepository.PAGE
    }
}
