package org.vechain.indexer.postgres

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.vechain.indexer.history.HistoryIndexes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndexBuilderTest {

    private val database = PostgresTestDatabase()
    private val set = HistoryIndexes.SET
    private lateinit var builder: IndexBuilder

    @BeforeAll
    fun start() {
        database.start()
        builder = IndexBuilder(database.properties, IndexBuilder.Settings(workers = 3))
    }

    @AfterAll fun stop() = database.close()

    @BeforeEach fun restore() = builder.build(set)

    @Test
    fun `a migrated schema carries every index its set declares`() {
        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
    }

    @Test
    fun `a drop takes the whole set and a build puts it back`() {
        builder.drop(set)

        assertEquals(set.indexes, builder.missing(set))

        builder.build(set)

        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
        assertEquals(set.indexes.map { it.name }.toSet(), standing())
    }

    @Test
    fun `a rebuilt primary key comes back as the constraint, not a bare unique index`() {
        builder.drop(set)
        assertEquals(emptyList<String>(), constraints())

        builder.build(set)

        assertEquals(
            set.indexes.filter { it.primaryKey }.map { it.name }.sorted(),
            constraints().sorted(),
        )
    }

    @Test
    fun `an index an interrupted build left invalid counts as missing and is replaced`() {
        val invalid = set.indexes.first()
        database.jdbc.execute(
            "UPDATE pg_index SET indisvalid = false " +
                "WHERE indexrelid = '${set.schema}.${invalid.name}'::regclass"
        )
        val before = oid(invalid.name)

        assertEquals(listOf(invalid), builder.missing(set))

        builder.buildConcurrently(set)

        assertEquals(emptyList<DeferrableIndex>(), builder.missing(set))
        assertNotEquals(before, oid(invalid.name))
    }

    @Test
    fun `a build never reports more indexes than actually stand`() {
        builder.drop(set)
        var reported = 0

        builder.buildConcurrently(set, onBuilt = { assert(++reported <= stood()) })

        assertEquals(set.indexes.size, reported)
        assertEquals(set.indexes.size, stood())
    }

    /** What [IndexBuilder.missing] would call standing: a key not yet relabelled does not. */
    private fun stood(): Int = set.indexes.size - builder.missing(set).size

    private fun standing(): Set<String> =
        database.jdbc
            .query(
                "SELECT indexname FROM pg_indexes WHERE schemaname = ?",
                { rs, _ -> rs.getString(1) },
                set.schema,
            )
            .toSet()
            .intersect(set.indexes.map { it.name }.toSet())

    private fun constraints(): List<String> =
        database.jdbc.query(
            "SELECT c.conname FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace " +
                "WHERE c.contype = 'p' AND n.nspname = ?",
            { rs, _ -> rs.getString(1) },
            set.schema,
        )

    private fun oid(index: String): Long =
        database.jdbc.queryForObject(
            "SELECT '${set.schema}.$index'::regclass::oid",
            Long::class.java,
        )!!
}
