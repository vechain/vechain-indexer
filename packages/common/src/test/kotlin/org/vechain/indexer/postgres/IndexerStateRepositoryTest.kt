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
    private val tables =
        object : PostgresIndexerTables {
            override fun rollbackFrom(blockNumber: Long) = Unit

            override fun truncate() {
                truncated += SCHEMA
            }
        }

    @BeforeAll
    fun start() {
        database.start()
        repository = IndexerStateRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach
    fun reset() {
        database.jdbc.update("DELETE FROM public.indexer_state WHERE name = ?", SCHEMA)
        truncated.clear()
    }

    @Test
    fun `an unknown schema has no version and no checkpoint`() {
        assertNull(repository.storedVersion(SCHEMA))
        assertNull(repository.checkpoint(SCHEMA))
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

    companion object {
        private const val SCHEMA = "state_test"
    }
}
