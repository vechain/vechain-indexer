package org.vechain.indexer.postgres

import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import kotlin.time.Duration.Companion.nanoseconds
import org.slf4j.LoggerFactory
import org.vechain.indexer.config.postgres.PostgresProperties

/** An index too big for a migration: [definition] is the column list and any WHERE clause. */
data class ConcurrentIndex(val name: String, val table: String, val definition: String)

/** Builds indexes CONCURRENTLY off the startup path, so the indexer runs while they grow. */
class ConcurrentIndexBuilder(private val properties: PostgresProperties) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /** Builds what is missing or invalid in [indexes]; a no-op once they all stand. */
    fun ensure(schema: String, indexes: List<ConcurrentIndex>) {
        connect().use { c -> indexes.forEach { build(c, schema, it) } }
    }

    private fun build(c: Connection, schema: String, index: ConcurrentIndex) {
        when (validity(c, schema, index.name)) {
            true -> return
            // An interrupted build leaves the index behind, invalid; CONCURRENTLY does not replace
            // it.
            false -> execute(c, "DROP INDEX CONCURRENTLY IF EXISTS $schema.${index.name}")
            null -> {}
        }
        val started = System.nanoTime()
        execute(
            c,
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS ${index.name} ON $schema.${index.table} " +
                index.definition,
        )
        logger.info(
            "{}.{} built in {}",
            schema,
            index.name,
            (System.nanoTime() - started).nanoseconds,
        )
    }

    /** True when the index is valid, false when a build left it invalid, null when absent. */
    private fun validity(c: Connection, schema: String, name: String): Boolean? =
        c.prepareStatement(
                "SELECT i.indisvalid FROM pg_index i JOIN pg_class x ON x.oid = i.indexrelid " +
                    "JOIN pg_namespace n ON n.oid = x.relnamespace WHERE n.nspname = ? AND x.relname = ?"
            )
            .use { ps ->
                ps.setString(1, schema)
                ps.setString(2, name)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) else null }
            }

    private fun execute(c: Connection, sql: String) = c.createStatement().use { it.execute(sql) }

    // Not pooled: those connections time out a read after minutes, and a build sends nothing for
    // hours. Autocommit, because CONCURRENTLY refuses to run inside a transaction.
    private fun connect(): Connection =
        DriverManager.getConnection(
                properties.url,
                Properties().apply {
                    setProperty("user", properties.username)
                    setProperty("password", properties.password)
                    setProperty("socketTimeout", "0")
                    setProperty("tcpKeepAlive", "true")
                    setProperty("ApplicationName", "index-builder")
                },
            )
            .also { it.autoCommit = true }
}
