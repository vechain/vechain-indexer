package org.vechain.indexer.blocks

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
import org.vechain.indexer.blocks.BlocksFixtures.block
import org.vechain.indexer.blocks.BlocksFixtures.decodedEvent
import org.vechain.indexer.blocks.BlocksFixtures.rawEvent
import org.vechain.indexer.blocks.BlocksFixtures.transaction
import org.vechain.indexer.blocks.BlocksFixtures.transfer
import org.vechain.indexer.postgres.PostgresApiRole
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.transaction.IndexedTransaction

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlocksWriteRepositoryTest {

    private val database = PostgresTestDatabase(apiPassword = "api-secret")
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: BlocksWriteRepository

    @BeforeAll
    fun start() {
        database.start()
        jdbc = database.jdbc
        repository = BlocksWriteRepository(jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach
    fun reset() {
        repository.truncate()
    }

    private fun count(table: String): Int = database.count("blocks.$table")

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

        assertEquals(BlockIdentifier(2, BlocksFixtures.hash(2)), repository.lastSynced())
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
                        "SELECT * FROM blocks.transaction WHERE id = ?",
                        { rs, _ -> BlocksRowMappers.transaction(rs) },
                        id,
                    )
                    .single()
            val block =
                jdbc
                    .query(
                        "SELECT * FROM blocks.block WHERE number = ?",
                        { rs, _ -> BlocksRowMappers.block(rs) },
                        row.blockNumber,
                    )
                    .single()
            val assembled =
                BlocksRowMapping.assemble(
                    row,
                    BlocksRowMapping.BlockRef(PostgresHex.hex(block.id), block.timestamp),
                    jdbc.query(
                        "SELECT * FROM blocks.clause WHERE tx_id = ?",
                        { rs, _ -> BlocksRowMappers.clause(rs) },
                        id,
                    ),
                    jdbc.query(
                        "SELECT * FROM blocks.event WHERE tx_id = ?",
                        { rs, _ -> BlocksRowMappers.event(rs) },
                        id,
                    ),
                    jdbc.query(
                        "SELECT * FROM blocks.transfer WHERE tx_id = ?",
                        { rs, _ -> BlocksRowMappers.transfer(rs) },
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
                    "SELECT * FROM blocks.block WHERE number = 1",
                    { rs, _ -> BlocksRowMappers.block(rs) },
                )
                .single()
        val ids =
            jdbc.query(
                "SELECT id FROM blocks.transaction WHERE block_number = 1 ORDER BY tx_index",
                { rs, _ -> PostgresHex.hex(rs.getBytes(1)) },
            )

        assertEquals(block(1, written.take(2)), BlocksRowMapping.assembleBlock(row, ids))
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
    fun `truncate empties every table`() {
        insertTwoBlocks()

        repository.truncate()

        assertEquals(0, count("block") + count("event"))
        assertNull(repository.lastSynced())
    }

    @Test
    fun `the api role can read the tables and nothing more`() {
        insertTwoBlocks()
        PostgresApiRole(jdbc, database.properties).sync()

        DriverManager.getConnection(database.jdbcUrl, PostgresApiRole.ROLE, "api-secret").use { api
            ->
            api.createStatement().executeQuery("SELECT count(*) FROM blocks.block").use { rs ->
                rs.next()
                assertEquals(2, rs.getInt(1))
            }
            api.createStatement().executeQuery("SELECT count(*) FROM public.indexer_state").close()
            assertThrows(Exception::class.java) {
                api.createStatement().execute("DELETE FROM blocks.block")
            }
        }

        // Re-running with a new password re-passwords rather than failing on the existing role.
        PostgresApiRole(jdbc, database.properties.copy(apiPassword = "rotated")).sync()
        DriverManager.getConnection(database.jdbcUrl, PostgresApiRole.ROLE, "rotated").close()
    }

    @Test
    fun `insert is atomic`() {
        val b = block(1)
        val tx = transaction(b, 0)
        val duplicate = transaction(b, 1).copy(id = tx.id)

        assertThrows(Exception::class.java) {
            database.transactions().executeWithoutResult {
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
