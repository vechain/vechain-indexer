package org.vechain.indexer.postgres

import com.zaxxer.hikari.HikariDataSource
import java.sql.DriverManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.config.postgres.PostgresProperties
import org.vechain.indexer.postgres.PostgresFixtures.block
import org.vechain.indexer.postgres.PostgresFixtures.decodedEvent
import org.vechain.indexer.postgres.PostgresFixtures.rawEvent
import org.vechain.indexer.postgres.PostgresFixtures.transaction
import org.vechain.indexer.postgres.PostgresFixtures.transfer
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.transaction.IndexedTransaction

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresWriteRepositoryTest {

    private val postgres = PostgreSQLContainer("postgres:16")
    private lateinit var dataSource: HikariDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: PostgresWriteRepository
    private lateinit var properties: PostgresProperties

    @BeforeAll
    fun start() {
        postgres.start()
        val config = PostgresConfig()
        properties =
            PostgresProperties(
                url = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                apiPassword = "api-secret",
            )
        dataSource = config.postgresDataSource(properties) as HikariDataSource
        config.postgresFlyway(dataSource).migrate()
        jdbc = JdbcTemplate(dataSource)
        repository = PostgresWriteRepository(jdbc)
    }

    @AfterAll
    fun stop() {
        dataSource.close()
        postgres.stop()
    }

    @BeforeEach
    fun reset() {
        repository.resync(1)
    }

    private fun count(table: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

    private fun insertTwoBlocks(): List<IndexedTransaction> {
        val b1 = block(1)
        val t1 =
            transaction(
                b1,
                0,
                events = listOf(decodedEvent(), rawEvent()),
                transfers = listOf(transfer()),
            )
        val t2 = transaction(b1, 1, reverted = true)
        val block1 = block(1, listOf(t1, t2))
        val b2 = block(2)
        val t3 = transaction(b2, 0, clauses = listOf())
        val block2 = block(2, listOf(t3))
        repository.insert(block1, listOf(t1, t2), BlockTotals.ZERO.plus(listOf(t1, t2)))
        repository.insert(block2, listOf(t3), repository.newestTotals()!!.plus(listOf(t3)))
        return listOf(t1, t2, t3)
    }

    @Test
    fun `an empty schema has no synced block and no totals`() {
        assertNull(repository.lastSynced())
        assertNull(repository.newestTotals())
    }

    @Test
    fun `insert writes the block tree and the running totals`() {
        insertTwoBlocks()

        assertEquals(BlockIdentifier(2, PostgresFixtures.hash(2)), repository.lastSynced())
        assertEquals(BlockTotals(3, 2, 1, 1), repository.newestTotals())
        assertEquals(2, count("block"))
        assertEquals(3, count("transaction"))
        assertEquals(2, count("clause"))
        assertEquals(2, count("event"))
        assertEquals(1, count("transfer"))
    }

    @Test
    fun `rows assemble back into the transactions that were written`() {
        val written = insertTwoBlocks()

        for (expected in written) {
            val id = PostgresHex.bytes(expected.id)
            val row =
                jdbc
                    .query(
                        "SELECT * FROM transaction WHERE id = ?",
                        { rs, _ -> PostgresRowMappers.transaction(rs) },
                        id,
                    )
                    .single()
            val block =
                jdbc
                    .query(
                        "SELECT * FROM block WHERE number = ?",
                        { rs, _ -> PostgresRowMappers.block(rs) },
                        row.blockNumber,
                    )
                    .single()
            val assembled =
                PostgresRowMapping.assemble(
                    row,
                    PostgresRowMapping.BlockRef(PostgresHex.hex(block.id), block.timestamp),
                    jdbc.query(
                        "SELECT * FROM clause WHERE tx_id = ?",
                        { rs, _ -> PostgresRowMappers.clause(rs) },
                        id,
                    ),
                    jdbc.query(
                        "SELECT * FROM event WHERE tx_id = ?",
                        { rs, _ -> PostgresRowMappers.event(rs) },
                        id,
                    ),
                    jdbc.query(
                        "SELECT * FROM transfer WHERE tx_id = ?",
                        { rs, _ -> PostgresRowMappers.transfer(rs) },
                        id,
                    ),
                )
            assertEquals(expected, assembled)
        }
    }

    @Test
    fun `a block row assembles with its transaction ids in canonical order`() {
        val written = insertTwoBlocks()
        val row =
            jdbc
                .query(
                    "SELECT * FROM block WHERE number = 1",
                    { rs, _ -> PostgresRowMappers.block(rs) },
                )
                .single()
        val ids =
            jdbc.query(
                "SELECT id FROM transaction WHERE block_number = 1 ORDER BY tx_index",
                { rs, _ -> PostgresHex.hex(rs.getBytes(1)) },
            )

        assertEquals(block(1, written.take(2)), PostgresRowMapping.assembleBlock(row, ids))
    }

    @Test
    fun `rollback deletes the block and cascades through its children`() {
        insertTwoBlocks()

        repository.rollbackFrom(1)

        assertNull(repository.lastSynced())
        assertEquals(0, count("transaction") + count("clause") + count("event") + count("transfer"))
    }

    @Test
    fun `rolling back the head leaves the previous totals in place`() {
        insertTwoBlocks()

        repository.rollbackFrom(2)

        assertEquals(1L, repository.lastSynced()?.number)
        assertEquals(BlockTotals(2, 2, 1, 1), repository.newestTotals())
    }

    @Test
    fun `a block cannot be written twice`() {
        insertTwoBlocks()
        assertThrows(Exception::class.java) {
            repository.insert(block(2), emptyList(), BlockTotals.ZERO)
        }
    }

    @Test
    fun `resync truncates every table and records the version`() {
        insertTwoBlocks()
        assertEquals(1, repository.storedVersion())

        repository.resync(2)

        assertEquals(2, repository.storedVersion())
        assertEquals(0, count("block"))
        assertEquals(0, count("event"))
    }

    @Test
    fun `the api role can read the tables and nothing more`() {
        insertTwoBlocks()
        PostgresApiRole(jdbc, properties).sync()

        DriverManager.getConnection(postgres.jdbcUrl, PostgresApiRole.ROLE, "api-secret").use { api
            ->
            api.createStatement().executeQuery("SELECT count(*) FROM block").use { rs ->
                rs.next()
                assertEquals(2, rs.getInt(1))
            }
            assertThrows(Exception::class.java) {
                api.createStatement().execute("DELETE FROM block")
            }
        }

        // Re-running with a new password re-passwords rather than failing on the existing role.
        PostgresApiRole(jdbc, properties.copy(apiPassword = "rotated")).sync()
        DriverManager.getConnection(postgres.jdbcUrl, PostgresApiRole.ROLE, "rotated").close()
    }

    @Test
    fun `insert is atomic`() {
        val b = block(1)
        val tx = transaction(b, 0)
        val duplicate = transaction(b, 1).copy(id = tx.id)

        assertThrows(Exception::class.java) {
            TransactionTemplate(PostgresConfig().postgresTransactionManager(dataSource))
                .executeWithoutResult {
                    repository.insert(
                        block(1, listOf(tx, duplicate)),
                        listOf(tx, duplicate),
                        BlockTotals.ZERO,
                    )
                }
        }

        assertEquals(0, count("block"))
    }
}
