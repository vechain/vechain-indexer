package org.vechain.indexer.backfill

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.backfill.BackfillState.Phase
import org.vechain.indexer.chain.ChainHead
import org.vechain.indexer.history.HistoryIndexes
import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

/** The phases against a real catalogue: what a drop leaves, and what a rebuild puts back. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackfillCoordinatorTest {

    private val database = PostgresTestDatabase()
    private val set = HistoryIndexes.SET
    private val chainHead = mockk<ChainHead>()
    private val properties = BackfillProperties().apply { enterBehindBlocks = 500_000 }
    private lateinit var builder: IndexBuilder
    private lateinit var state: BackfillState

    @BeforeAll
    fun start() {
        database.start()
        builder = IndexBuilder(database.properties, IndexBuilder.Settings(workers = 3))
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach
    fun restore() {
        builder.build(set)
        state = BackfillState()
    }

    private fun coordinator() = BackfillCoordinator(set, builder, chainHead, properties, state)

    private fun entry(block: Long, status: Status = Status.SYNCING) =
        IndexingResult.LogResult(block, emptyList(), status)

    private fun head(block: Long) {
        every { chainHead.bestBlockNumber() } returns block
    }

    @Test
    fun `an indexer near the head keeps every index`() {
        head(400_000)
        val coordinator = coordinator()

        runBlocking { coordinator.beforeEntry(entry(1)) }

        assertEquals(mapOf(set.schema to Phase.SERVING), state.phases())
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
    }

    @Test
    fun `a resync drops the indexes the API alone reads`() {
        head(25_000_000)
        val coordinator = coordinator()

        runBlocking { coordinator.beforeEntry(entry(1)) }

        assertEquals(mapOf(set.schema to Phase.BACKFILL), state.phases())
        assertEquals(set.indexes, builder.missing(set))
    }

    @Test
    fun `reaching the head rebuilds them and lets the block through`() {
        head(25_000_000)
        val coordinator = coordinator()
        runBlocking { coordinator.beforeEntry(entry(1)) }

        head(25_000_000)
        runBlocking { coordinator.beforeEntry(entry(25_000_000, Status.FULLY_SYNCED)) }

        assertEquals(mapOf(set.schema to Phase.SERVING), state.phases())
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
    }

    @Test
    fun `a restart mid-backfill stays in backfill and drops nothing twice`() {
        builder.drop(set)
        head(25_000_000)

        runBlocking { coordinator().beforeEntry(entry(3_000_000)) }

        assertEquals(mapOf(set.schema to Phase.BACKFILL), state.phases())
        assertEquals(set.indexes, builder.missing(set))
    }

    @Test
    fun `a snapshot restored short of the head builds without pausing anything`() {
        builder.drop(set)
        head(3_000_100)

        runBlocking { coordinator().beforeEntry(entry(3_000_000)) }

        // Serving from the first block: the build runs underneath, CONCURRENTLY, off this thread.
        assertEquals(mapOf(set.schema to Phase.SERVING), state.phases())
        val deadline = System.currentTimeMillis() + 60_000
        while (builder.missing(set).isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
    }

    @Test
    fun `an unreachable head decides nothing`() {
        every { chainHead.bestBlockNumber() } returns null

        runBlocking { coordinator().beforeEntry(entry(1)) }

        assertEquals(emptyMap<String, Phase>(), state.phases())
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
    }
}
