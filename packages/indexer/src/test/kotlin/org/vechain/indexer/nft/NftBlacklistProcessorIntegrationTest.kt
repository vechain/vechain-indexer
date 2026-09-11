package org.vechain.indexer.nft

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.model.BlockIdentifier

/** The processor against a real schema: fixture events in, current flags and resume point out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NftBlacklistProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var processor: NftBlacklistProcessor
    private lateinit var reader: NftBlacklistReadRepository

    private val flaggedAt21774662 =
        listOf(
            "0x4d4a0fcda8963879e1fbacfb25a179111b8e4ae0",
            "0xf416bc92ffab1704bc247d620322fa95a178d496",
        )
    private val flipped = "0x9d51a33a211e77fd3621cbf135d543cd3bb7490a"

    @BeforeAll
    fun start() {
        database.start()
        val repository = NftBlacklistWriteRepository(database.jdbc)
        reader = NftBlacklistReadRepository(database.jdbc)
        processor =
            NftBlacklistProcessor(
                NftBlacklistService(repository),
                repository,
                IndexerStateRepository(database.jdbc),
                CheckpointProperties().apply { saveIntervalSeconds = 0 },
                InlineVersioningProperties(),
                // A real registry: a recording mock keeps one call per block in a batch's gap.
                ProcessorMetrics(SimpleMeterRegistry()),
                version = 1,
            )
        processor.bootstrap()
    }

    @AfterAll fun stop() = database.close()

    private fun process(endBlock: Long, events: List<IndexedEvent>) = runBlocking {
        processor.process(IndexingResult.LogResult(endBlock, events, Status.SYNCING))
    }

    @Test
    fun `flags are written, flipped, resumed from, rolled back and replayed`() {
        assertNull(processor.getLastSyncedBlock())

        // Block 21774662 blacklists two collections; the batch ends on a later, empty block.
        process(21774700, IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST)
        assertEquals(flaggedAt21774662, reader.blacklisted())
        assertEquals(BlockIdentifier(21774700, null), processor.getLastSyncedBlock())

        // 21774662 flags a third collection and 21774999 clears it again, in one entry.
        process(21774999, IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST_DUPLICATE)
        assertEquals(flaggedAt21774662, reader.blacklisted())
        assertEquals(false, reader.current(flipped)!!.isBlacklisted)
        assertEquals(
            3,
            database.count("nft_blacklist.collection_state WHERE superseded_at IS NULL"),
        )

        // Replaying the entry leaves the rows as they were.
        val before = rows()
        process(21774999, IndexedEventsFixtures.INDEXED_EVENTS_BLACKLIST_DUPLICATE)
        assertEquals(before, rows())

        // Rolling back the clearing block reinstates the flag and moves the resume point.
        processor.rollback(21774999)
        assertEquals((flaggedAt21774662 + flipped).sorted(), reader.blacklisted())
        assertEquals(BlockIdentifier(21774998, null), processor.getLastSyncedBlock())

        // A whitelist at 21774555, processed after a deeper rollback, lands as history.
        processor.rollback(21774600)
        assertEquals(emptyList<String>(), reader.blacklisted())
        process(21774600, IndexedEventsFixtures.INDEXED_EVENTS_WHITELIST)
        assertEquals(false, reader.current(flipped)!!.isBlacklisted)
    }

    private fun rows(): List<String> =
        database.jdbc.queryForList(
            "SELECT encode(contract_address, 'hex') || ':' || block_number || ':' || is_blacklisted " +
                "|| ':' || coalesce(superseded_at::text, '-') FROM nft_blacklist.collection_state " +
                "ORDER BY 1",
            String::class.java,
        )
}
