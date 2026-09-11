package org.vechain.indexer.history

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.history.HistoryFixtures.FULL
import org.vechain.indexer.history.HistoryFixtures.address
import org.vechain.indexer.history.HistoryFixtures.event
import org.vechain.indexer.postgres.PostgresTestDatabase
import org.vechain.indexer.validator.Status

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoryWriteRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var writer: HistoryWriteRepository

    @BeforeAll
    fun start() {
        database.start()
        writer = HistoryWriteRepository(database.jdbc)
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun reset() = writer.truncate()

    private fun stored(): List<IndexedHistoryEvent> =
        database.jdbc
            .query("SELECT * FROM history.event ORDER BY block_number, id") { rs, _ ->
                HistoryRowMapping.row(rs)
            }
            .map(HistoryRowMapping::assemble)

    @Test
    fun `every column round-trips through the table`() {
        writer.save(listOf(FULL))

        assertEquals(listOf(FULL), stored())
    }

    @Test
    fun `the enum type mirrors HistoryEventName and every value is insertable`() {
        val labels =
            database.jdbc.queryForList(
                "SELECT e.enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid " +
                    "JOIN pg_namespace n ON n.oid = t.typnamespace " +
                    "WHERE n.nspname = 'history' AND t.typname = 'event_name' ORDER BY e.enumsortorder",
                String::class.java,
            )
        assertEquals(HistoryEventName.entries.map { it.name }, labels)

        writer.save(HistoryEventName.entries.mapIndexed { i, name -> event(i, 1, name) })
        assertEquals(HistoryEventName.entries.toList(), stored().map { it.eventName })
    }

    @Test
    fun `addresses fan out once per distinct address and carry the sort keys`() {
        writer.save(
            listOf(
                event(1, 10, origin = address(1), gasPayer = address(1), to = address(2)),
                event(2, 10, origin = address(3)),
            )
        )

        val rows =
            database.jdbc.queryForList(
                "SELECT encode(address, 'hex') || ':' || block_timestamp || ':' || event_name || ':' || " +
                    "block_number FROM history.event_address ORDER BY 1",
                String::class.java,
            )
        assertEquals(
            listOf("1", "2", "3").map { "0".repeat(39) + "$it:100:TRANSFER_VET:10" },
            rows,
        )
    }

    @Test
    fun `rollback removes the block from both tables and leaves earlier blocks alone`() {
        writer.save(listOf(event(1, 10), event(2, 10, to = address(2))))
        writer.save(listOf(event(3, 11), event(4, 12)))

        writer.rollbackFrom(11)

        assertEquals(
            listOf(HistoryFixtures.sha1(1), HistoryFixtures.sha1(2)),
            stored().map { it.id },
        )
        assertEquals(3, database.count("history.event_address"))
    }

    @Test
    fun `replaying a block changes nothing`() {
        writer.save(listOf(event(2, 10, to = address(2)), FULL))

        writer.save(listOf(event(2, 10, to = address(2)), FULL))

        assertEquals(2, database.count("history.event"))
        assertEquals(7, database.count("history.event_address"))
    }

    @Test
    fun `a delegation lifecycle row keeps its bookkeeping`() {
        writer.save(listOf(FULL))
        val row = stored().single()
        assertEquals(Status.EXITING, row.delegationLifecycleStatus)
        assertEquals(1001, row.delegationLifecycleOrder)
        assertEquals(1, database.count("history.event WHERE lifecycle_status IS NOT NULL"))
    }

    @Test
    fun `truncate empties both tables`() {
        writer.save(listOf(FULL))
        writer.truncate()
        assertTrue(stored().isEmpty())
        assertEquals(0, database.count("history.event_address"))
    }
}
