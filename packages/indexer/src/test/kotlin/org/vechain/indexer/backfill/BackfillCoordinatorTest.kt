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
import org.vechain.indexer.backfill.BackfillState.Progress
import org.vechain.indexer.chain.ChainHead
import org.vechain.indexer.history.HistoryIndexes
import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.IndexSet
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

    private fun progress(phase: Phase, standing: Int) =
        mapOf(set.schema to Progress(phase, standing, set.indexes.size))

    @Test
    fun `an indexer near the head keeps every index`() {
        head(400_000)
        val coordinator = coordinator()

        runBlocking { coordinator.beforeEntry(entry(1)) }

        assertEquals(progress(Phase.SERVING, set.indexes.size), state.progress())
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
    }

    @Test
    fun `a resync drops the indexes the API alone reads`() {
        head(25_000_000)
        val coordinator = coordinator()

        runBlocking { coordinator.beforeEntry(entry(1)) }

        assertEquals(progress(Phase.BACKFILL, 0), state.progress())
        assertEquals(set.indexes, builder.missing(set))
    }

    @Test
    fun `reaching the head rebuilds them and lets the block through`() {
        head(25_000_000)
        val coordinator = coordinator()
        runBlocking { coordinator.beforeEntry(entry(1)) }

        head(25_000_000)
        runBlocking { coordinator.beforeEntry(entry(25_000_000, Status.FULLY_SYNCED)) }

        assertEquals(progress(Phase.SERVING, set.indexes.size), state.progress())
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
    }

    @Test
    fun `a restart mid-backfill stays in backfill and drops nothing twice`() {
        builder.drop(set)
        head(25_000_000)

        runBlocking { coordinator().beforeEntry(entry(3_000_000)) }

        assertEquals(progress(Phase.BACKFILL, 0), state.progress())
        assertEquals(set.indexes, builder.missing(set))
    }

    @Test
    fun `a snapshot restored short of the head builds without pausing anything`() {
        builder.drop(set)
        head(3_000_100)

        runBlocking { coordinator().beforeEntry(entry(3_000_000)) }

        // Serving from the first block: the build runs underneath, CONCURRENTLY, off this thread.
        assertEquals(Phase.SERVING, state.progress()[set.schema]?.phase)
        awaitBuilt(set, set.indexes)
        // Every index is labelled by now, so every build reported itself before the loop ended.
        assertEquals(progress(Phase.SERVING, set.indexes.size), state.progress())
    }

    @Test
    fun `an unreachable head decides nothing`() {
        every { chainHead.bestBlockNumber() } returns null

        runBlocking { coordinator().beforeEntry(entry(1)) }

        assertEquals(emptyMap<String, Progress>(), state.progress())
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
    }

    @Test
    fun `an index the indexer itself needs grows under a backfill and outlives the drop`() {
        val set = needing()
        builder.drop(set)
        head(25_000_000)
        try {
            runBlocking { coordinator(set).beforeEntry(entry(1)) }

            assertEquals(progress(Phase.BACKFILL, 0), state.progress())
            assertEquals(set.indexes, builder.missing(set))
            awaitBuilt(set, set.needed)

            builder.drop(set)

            assertEquals(emptyList<DeferrableIndex>(), builder.missing(set, set.needed))
        } finally {
            database.jdbc.execute("DROP INDEX IF EXISTS history.$NEEDED_TEST_INDEX")
        }
    }

    @Test
    fun `backfill switched off still grows what the indexer itself needs`() {
        val set = needing()
        head(25_000_000)
        try {
            val off = BackfillProperties().apply { enabled = false }
            runBlocking { coordinator(set, off).beforeEntry(entry(1)) }

            assertEquals(emptyMap<String, Progress>(), state.progress())
            awaitBuilt(set, set.needed)
        } finally {
            database.jdbc.execute("DROP INDEX IF EXISTS history.$NEEDED_TEST_INDEX")
        }
    }

    private fun needing() =
        set.copy(needed = listOf(DeferrableIndex(NEEDED_TEST_INDEX, "event", "(origin, id)")))

    private fun coordinator(set: IndexSet, properties: BackfillProperties = this.properties) =
        BackfillCoordinator(set, builder, chainHead, properties, state)

    private fun awaitBuilt(set: IndexSet, indexes: List<DeferrableIndex>) {
        val deadline = System.currentTimeMillis() + 60_000
        while (
            builder.missing(set, indexes).isNotEmpty() && System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(100)
        }
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set, indexes))
    }

    private companion object {
        const val NEEDED_TEST_INDEX = "event_needed_test_idx"
    }
}
