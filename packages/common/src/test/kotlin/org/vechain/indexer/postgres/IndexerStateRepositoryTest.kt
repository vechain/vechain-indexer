package org.vechain.indexer.postgres

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.thor.model.BlockIdentifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexerStateRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: IndexerStateRepository
    private val truncated = mutableListOf<String>()
    private val rolledBackFrom = mutableListOf<Long>()
    private var pruned = 0
    private val tables =
        object : PostgresIndexerTables {
            override fun rollbackFrom(blockNumber: Long) {
                rolledBackFrom += blockNumber
            }

            override fun truncate() {
                truncated += SCHEMA
            }

            override fun prune(before: Long) = pruned
        }

    @BeforeAll
    fun start() {
        database.start()
        repository = IndexerStateRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach
    fun reset() {
        database.jdbc.update(
            "DELETE FROM public.indexer_state WHERE name IN (?, ?)",
            SCHEMA,
            OTHER_SCHEMA,
        )
        truncated.clear()
        rolledBackFrom.clear()
    }

    @Test
    fun `checkpoints report every indexer, including one that has written no block yet`() {
        repository.recordVersion(SCHEMA, 2)
        repository.recordVersion(OTHER_SCHEMA, 1)
        repository.saveCheckpoint(SCHEMA, BlockIdentifier(25_909_780, "0x018b5a14"))

        val checkpoints =
            repository.checkpoints().filter { it.schema in setOf(SCHEMA, OTHER_SCHEMA) }

        assertEquals(
            listOf(
                IndexerCheckpoint(SCHEMA, 2, 25_909_780, "0x018b5a14"),
                IndexerCheckpoint(OTHER_SCHEMA, 1, null, null),
            ),
            checkpoints,
        )
    }

    @Test
    fun `an unknown schema has no version, checkpoint or pruned horizon`() {
        assertNull(repository.storedVersion(SCHEMA))
        assertNull(repository.checkpoint(SCHEMA))
        assertNull(repository.prunedBelow(SCHEMA))
    }

    @Test
    fun `the pruned horizon moves only when rows went, only rises, and a resync forgets it`() {
        repository.recordVersion(SCHEMA, 1)

        pruned = 0
        assertEquals(0, repository.prune(SCHEMA, tables, 100))
        assertNull(repository.prunedBelow(SCHEMA))

        pruned = 3
        assertEquals(3, repository.prune(SCHEMA, tables, 100))
        repository.prune(SCHEMA, tables, 50)
        assertEquals(100L, repository.prunedBelow(SCHEMA))

        repository.resync(SCHEMA, 2, tables)
        assertNull(repository.prunedBelow(SCHEMA))
    }

    @Test
    fun `the checkpoint round-trips with and without a block id`() {
        repository.recordVersion(SCHEMA, 1)

        repository.saveCheckpoint(SCHEMA, BlockIdentifier(7, "0x" + "07".repeat(32)))
        assertEquals(BlockIdentifier(7, "0x" + "07".repeat(32)), repository.checkpoint(SCHEMA))

        repository.saveCheckpoint(SCHEMA, BlockIdentifier(8, null))
        assertEquals(BlockIdentifier(8, null), repository.checkpoint(SCHEMA))
    }

    @Test
    fun `resync truncates the tables, raises the version and forgets the checkpoint`() {
        repository.recordVersion(SCHEMA, 1)
        repository.saveCheckpoint(SCHEMA, BlockIdentifier(7, null))

        repository.resync(SCHEMA, 2, tables)

        assertEquals(listOf(SCHEMA), truncated)
        assertEquals(2, repository.storedVersion(SCHEMA))
        assertNull(repository.checkpoint(SCHEMA))
    }

    @Test
    fun `recording a version again keeps the checkpoint`() {
        repository.recordVersion(SCHEMA, 1)
        repository.saveCheckpoint(SCHEMA, BlockIdentifier(7, null))

        repository.recordVersion(SCHEMA, 1)

        assertEquals(7L, repository.checkpoint(SCHEMA)?.number)
    }

    @Test
    fun `a trim undoes the blocks past the checkpoint and leaves the marker alone`() {
        repository.recordVersion(SCHEMA, 1)
        val checkpoint = BlockIdentifier(7, "0x" + "07".repeat(32))
        repository.saveCheckpoint(SCHEMA, checkpoint)

        repository.trimTo(tables, checkpoint.number)

        assertEquals(listOf(8L), rolledBackFrom)
        assertEquals(checkpoint, repository.checkpoint(SCHEMA))
    }

    companion object {
        private const val SCHEMA = "state_test"
        private const val OTHER_SCHEMA = "state_test_other"
    }
}
