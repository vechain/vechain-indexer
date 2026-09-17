package org.vechain.indexer.postgres

import java.util.function.Supplier
import javax.sql.DataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.thor.model.BlockIdentifier

/** The rollback behind a transaction proxy, so the pairing is proven rather than read. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexerStateRollbackTest {

    @Configuration @EnableTransactionManagement open class Transactions

    private val database = PostgresTestDatabase()
    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var repository: IndexerStateRepository

    /** Stands in for a schema whose undo is several statements, the last of which fails. */
    private val failingTables =
        object : PostgresIndexerTables {
            override fun rollbackFrom(blockNumber: Long) {
                database.jdbc.update(
                    "INSERT INTO public.indexer_state (name, version) VALUES (?, 1)",
                    MARKER,
                )
                error("the connection went away")
            }

            override fun truncate() = Unit
        }

    private val tables =
        object : PostgresIndexerTables {
            override fun rollbackFrom(blockNumber: Long) {
                database.jdbc.update(
                    "INSERT INTO public.indexer_state (name, version) VALUES (?, 1)",
                    MARKER,
                )
            }

            override fun truncate() = Unit
        }

    @BeforeAll
    fun start() {
        database.start()
        context =
            AnnotationConfigApplicationContext().apply {
                registerBean(DataSource::class.java, Supplier { database.dataSource })
                registerBean(
                    PostgresConfig.TRANSACTION_MANAGER,
                    PlatformTransactionManager::class.java,
                    Supplier { JdbcTransactionManager(database.dataSource) },
                )
                register(Transactions::class.java)
                registerBean(
                    IndexerStateRepository::class.java,
                    Supplier { IndexerStateRepository(database.jdbc) },
                )
                refresh()
            }
        repository = context.getBean(IndexerStateRepository::class.java)
    }

    @AfterAll
    fun stop() {
        context.close()
        database.close()
    }

    @BeforeEach
    fun reset() {
        database.jdbc.update(
            "DELETE FROM public.indexer_state WHERE name IN (?, ?)",
            SCHEMA,
            MARKER,
        )
        repository.recordVersion(SCHEMA, 1)
        repository.saveCheckpoint(SCHEMA, BlockIdentifier(900, null))
    }

    @Test
    fun `a failure part way through the tables leaves the checkpoint and the rows alone`() {
        assertThrows<IllegalStateException> { repository.rollbackFrom(SCHEMA, failingTables, 500) }

        assertEquals(900L, repository.checkpoint(SCHEMA)?.number)
        assertNull(repository.storedVersion(MARKER))
    }

    @Test
    fun `a rollback that completes parks the checkpoint below the block with the rows`() {
        repository.rollbackFrom(SCHEMA, tables, 500)

        assertEquals(499L, repository.checkpoint(SCHEMA)?.number)
        assertEquals(1, repository.storedVersion(MARKER))
    }

    companion object {
        private const val SCHEMA = "rollback_test"
        private const val MARKER = "rollback_test_marker"
    }
}
