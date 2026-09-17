package org.vechain.indexer.history

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.postgres.ConcurrentIndexBuilder
import org.vechain.indexer.postgres.PostgresTestDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoryActionIndexesTest {

    private val database = PostgresTestDatabase()

    @BeforeAll fun start() = database.start().let {}

    @AfterAll fun stop() = database.close()

    @Test
    fun `builds the missing and invalid indexes`() {
        val jdbc = database.jdbc
        jdbc.execute("DROP INDEX history.event_action_to_idx")
        jdbc.execute(
            "UPDATE pg_index SET indisvalid = false WHERE indexrelid = 'history.event_action_app_idx'::regclass"
        )
        val invalidOid = oid("event_action_app_idx")

        ConcurrentIndexBuilder(database.properties)
            .ensure(HistoryActionIndexes.SCHEMA, HistoryActionIndexes.INDEXES)

        assertEquals(
            HistoryActionIndexes.INDEXES.map { it.name to true }.toMap(),
            validity(),
        )
        assertNotEquals(invalidOid, oid("event_action_app_idx"))
    }

    private fun validity(): Map<String, Boolean> =
        database.jdbc
            .query(
                "SELECT x.relname, i.indisvalid FROM pg_index i JOIN pg_class x ON x.oid = i.indexrelid " +
                    "WHERE i.indrelid = 'history.event'::regclass AND x.relname LIKE 'event_action_%'"
            ) { rs, _ ->
                rs.getString(1) to rs.getBoolean(2)
            }
            .toMap()

    private fun oid(index: String): Long =
        database.jdbc.queryForObject("SELECT 'history.$index'::regclass::oid", Long::class.java)!!
}
