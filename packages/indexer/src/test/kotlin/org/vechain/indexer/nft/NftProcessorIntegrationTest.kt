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

/** The processor against a real schema: fixture transfers in, owners and resume point out. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NftProcessorIntegrationTest {

    private val database = PostgresTestDatabase()
    private lateinit var processor: NftProcessor

    @BeforeAll
    fun start() {
        database.start()
        val repository = NftWriteRepository(database.jdbc)
        processor =
            NftProcessor(
                NftService(repository),
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

    private fun process(events: List<IndexedEvent>) = runBlocking {
        processor.process(
            IndexingResult.LogResult(events.maxOf { it.blockNumber }, events, Status.SYNCING)
        )
    }

    private fun owners(): List<String> =
        database.jdbc.queryForList(
            "SELECT token_id::text || '@' || block_number || ':' || encode(owner, 'hex') FROM nft.ownership " +
                "WHERE superseded_at IS NULL ORDER BY token_id",
            String::class.java,
        )

    @Test
    fun `transfers are written, resumed from, rolled back and replayed`() {
        assertNull(processor.getLastSyncedBlock())
        val (mint, move) = IndexedEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE

        // Token 1 is minted at 11245998, re-minted at 21245998 beside token 2, and moves at
        // 21246000.
        process(listOf(mint))
        process(IndexedEventsFixtures.INDEXED_EVENTS_NFT_TRANSFER)
        process(listOf(move))
        assertEquals(
            listOf(
                "1@21246000:884a36ca0b582c54255aac68a2664cd0ca8c592d",
                "2@21245999:884a36ca0b582c54255aac68a2664cd0ca8c592d",
            ),
            owners(),
        )
        assertEquals(4, database.count("nft.ownership"))
        assertEquals(BlockIdentifier(21246000, move.blockId), processor.getLastSyncedBlock())

        val before = owners()
        process(listOf(move))
        assertEquals(before, owners())
        assertEquals(4, database.count("nft.ownership"))

        processor.rollback(21246000)
        assertEquals(
            listOf(
                "1@21245998:4d2b488dd3638459f75040bd7bdf77b17cef7712",
                "2@21245999:884a36ca0b582c54255aac68a2664cd0ca8c592d",
            ),
            owners(),
        )
        assertEquals(BlockIdentifier(21245999, null), processor.getLastSyncedBlock())

        processor.rollback(21245998)
        assertEquals(listOf("1@11245998:4d2b488dd3638459f75040bd7bdf77b17cef7712"), owners())
    }
}
