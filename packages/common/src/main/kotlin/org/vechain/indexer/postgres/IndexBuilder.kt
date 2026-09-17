package org.vechain.indexer.postgres

import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.nanoseconds
import org.slf4j.LoggerFactory
import org.vechain.indexer.config.postgres.PostgresProperties

/** Drops and rebuilds a schema's [DeferrableIndex]es, under a running indexer or a paused one. */
class IndexBuilder(
    private val properties: PostgresProperties,
    private val settings: Settings = Settings(),
    private val budget: IndexBuildBudget = IndexBuildBudget(settings.workers),
) {

    /** Per-session build memory, and this builder's share of a budget it is not given one of. */
    data class Settings(
        val workers: Int = 4,
        val maintenanceWorkMem: String = "1GB",
        val parallelMaintenanceWorkers: Int = 2,
    )

    private val logger = LoggerFactory.getLogger(this::class.java)

    /** The indexes of [set] that are absent, or left invalid or unlabelled by a build. */
    fun missing(set: IndexSet): List<DeferrableIndex> =
        connect().use { c -> set.indexes.filter { !stands(c, set.schema, it) } }

    private fun stands(c: Connection, schema: String, index: DeferrableIndex): Boolean =
        validity(c, schema, index.name) == true &&
            (!index.primaryKey || isPrimaryKey(c, schema, index.name))

    /** Milliseconds each: the row those indexes were costing stops paying for them at once. */
    fun drop(set: IndexSet) {
        connect().use { c ->
            set.indexes.forEach {
                if (it.primaryKey) {
                    execute(
                        c,
                        "ALTER TABLE ${set.schema}.${it.table} DROP CONSTRAINT IF EXISTS ${it.name}",
                    )
                }
                execute(c, "DROP INDEX IF EXISTS ${set.schema}.${it.name}")
            }
        }
    }

    /** Serial and online: the caller keeps processing blocks while these grow. */
    fun buildConcurrently(set: IndexSet, indexes: List<DeferrableIndex> = missing(set)) {
        if (indexes.isEmpty()) return
        connect().use { c ->
            indexes.forEach { budget.withPermit { create(c, set.schema, it, concurrently = true) } }
            label(c, set.schema, indexes)
        }
    }

    /** Several at a time, their SHARE locks not conflicting; only for a paused processor. */
    fun build(set: IndexSet, indexes: List<DeferrableIndex> = missing(set)) {
        if (indexes.isEmpty()) return
        val started = System.nanoTime()
        val pool = Executors.newFixedThreadPool(minOf(budget.permits, indexes.size))
        try {
            pool
                .invokeAll(
                    indexes.map { index ->
                        Callable {
                            budget.withPermit {
                                connect().use {
                                    create(it, set.schema, index, concurrently = false)
                                }
                            }
                        }
                    }
                )
                // get() rethrows the first failure; the rest have finished or failed by now.
                .forEach { it.get() }
        } finally {
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.MINUTES)
        }
        // Not inside the parallel phase: the ALTER wants the table exclusively.
        connect().use { label(it, set.schema, indexes) }
        logger.info(
            "{}: built {} deferrable indexes in {}",
            set.schema,
            indexes.size,
            (System.nanoTime() - started).nanoseconds,
        )
    }

    private fun create(
        c: Connection,
        schema: String,
        index: DeferrableIndex,
        concurrently: Boolean,
    ) {
        // An interrupted CONCURRENTLY build leaves the index behind, invalid, and does not replace
        // it; a plain CREATE INDEX rolls back and leaves nothing.
        if (validity(c, schema, index.name) == false) {
            execute(c, "DROP INDEX IF EXISTS $schema.${index.name}")
        }
        val started = System.nanoTime()
        execute(
            c,
            "CREATE ${if (index.primaryKey) "UNIQUE " else ""}INDEX " +
                "${if (concurrently) "CONCURRENTLY " else ""}IF NOT EXISTS " +
                "${index.name} ON $schema.${index.table} ${index.definition}",
        )
        logger.info(
            "{}.{} built in {}",
            schema,
            index.name,
            (System.nanoTime() - started).nanoseconds,
        )
    }

    /**
     * Relabels a built unique index as the key, which is instant: the columns are still NOT NULL.
     */
    private fun label(c: Connection, schema: String, indexes: List<DeferrableIndex>) =
        indexes
            .filter { it.primaryKey && !isPrimaryKey(c, schema, it.name) }
            .forEach {
                execute(c, "ALTER TABLE $schema.${it.table} ADD PRIMARY KEY USING INDEX ${it.name}")
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

    private fun isPrimaryKey(c: Connection, schema: String, name: String): Boolean =
        c.prepareStatement(
                "SELECT true FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace " +
                    "WHERE c.contype = 'p' AND n.nspname = ? AND c.conname = ?"
            )
            .use { ps ->
                ps.setString(1, schema)
                ps.setString(2, name)
                ps.executeQuery().use { it.next() }
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
            .also {
                it.autoCommit = true
                execute(it, "SET maintenance_work_mem = '${settings.maintenanceWorkMem}'")
                execute(
                    it,
                    "SET max_parallel_maintenance_workers = ${settings.parallelMaintenanceWorkers}",
                )
            }
}
