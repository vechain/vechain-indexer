package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.action.ActionRowMapping.ENTITY_TYPE
import org.vechain.indexer.b3tr.action.ActionRowMapping.MEASURES
import org.vechain.indexer.b3tr.action.ActionRowMapping.appUserTable
import org.vechain.indexer.b3tr.action.ActionRowMapping.entityBytes
import org.vechain.indexer.b3tr.action.ActionRowMapping.entityTable
import org.vechain.indexer.b3tr.action.ActionRowMapping.keyValue
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables

/** The `b3tr_action` schema: six rollups of the reward events, written together per block. */
@Repository
@ConditionalOnPostgres
open class ActionWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Everything one entry adds, written together so a rollback cannot split them. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(update: ActionSummaryUpdate) {
        for (kind in ActionPeriodKind.entries) {
            saveEntities(kind, update.entities.filter { it.period.kind == kind })
            saveAppUsers(kind, update.appUsers.filter { it.period.kind == kind })
        }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveEntities(kind: ActionPeriodKind, entries: List<EntityActionSummary>) {
        val chains = chains(entries) { Triple(it.entityType, it.entity, it.period) }
        if (chains.isEmpty()) return
        val opening = chains.map { it.first() }
        jdbc.update(
            ENTITY_SUPERSEDE.getValue(kind),
            *arguments(
                listOf(
                    opening.map { it.entityType.name }.toTypedArray(),
                    opening.map { entityBytes(it.entityType, it.entity) }.toTypedArray(),
                ),
                opening,
            ),
        )
        val rows = chains.flatMap(::versions)
        jdbc.batchUpdate(ENTITY_INSERT.getValue(kind), rows, rows.size) { ps, v ->
            ActionRowMapping.bindEntity(ps, v.row, v.supersededAt)
        }
    }

    private fun saveAppUsers(kind: ActionPeriodKind, entries: List<AppUserActionSummary>) {
        val chains = chains(entries) { Triple(it.appId, it.user, it.period) }
        if (chains.isEmpty()) return
        val opening = chains.map { it.first() }
        jdbc.update(
            APP_USER_SUPERSEDE.getValue(kind),
            *arguments(
                listOf(
                    opening.map { bytes(it.appId) }.toTypedArray(),
                    opening.map { bytes(it.user) }.toTypedArray(),
                ),
                opening,
            ),
        )
        val rows = chains.flatMap(::versions)
        jdbc.batchUpdate(APP_USER_INSERT.getValue(kind), rows, rows.size) { ps, v ->
            ActionRowMapping.bindAppUser(ps, v.row, v.supersededAt)
        }
    }

    /** The current row of each of [keys] over [period]: wallets, apps and the GLOBAL row. */
    open fun findCurrentEntities(
        period: ActionPeriod,
        keys: Collection<Pair<EntityType, String>>,
    ): List<EntityActionSummary> {
        if (keys.isEmpty()) return emptyList()
        val kind = period.kind
        val distinct = keys.distinct()
        return jdbc.query(
            "SELECT t.* FROM ${entityTable(kind)} t " +
                "JOIN unnest(CAST(? AS $ENTITY_TYPE[]), ?::bytea[]) AS k(entity_type, entity) " +
                "ON k.entity_type = t.entity_type AND k.entity = t.entity " +
                "WHERE t.superseded_at IS NULL ${key(kind, "t.")}",
            { rs, _ -> ActionRowMapping.readEntity(rs, kind) },
            *listOfNotNull(
                    distinct.map { it.first.name }.toTypedArray(),
                    distinct.map { entityBytes(it.first, it.second) }.toTypedArray(),
                    keyValue(period),
                )
                .toTypedArray(),
        )
    }

    /** The current row of each (app, wallet) pair in [keys] over [period]. */
    open fun findCurrentAppUsers(
        period: ActionPeriod,
        keys: Collection<Pair<String, String>>,
    ): List<AppUserActionSummary> {
        if (keys.isEmpty()) return emptyList()
        val kind = period.kind
        val pairs = keys.distinct()
        return jdbc.query(
            "SELECT t.* FROM ${appUserTable(kind)} t " +
                "JOIN unnest(?::bytea[], ?::bytea[]) AS k(app_id, wallet) " +
                "ON k.app_id = t.app_id AND k.wallet = t.wallet " +
                "WHERE t.superseded_at IS NULL ${key(kind, "t.")}",
            { rs, _ -> ActionRowMapping.readAppUser(rs, kind) },
            *listOfNotNull(
                    pairs.map { bytes(it.first) }.toTypedArray(),
                    pairs.map { bytes(it.second) }.toTypedArray(),
                    keyValue(period),
                )
                .toTypedArray(),
        )
    }

    override fun rollbackFrom(blockNumber: Long) {
        TABLES.forEach { table ->
            jdbc.update("DELETE FROM $table WHERE block_number >= ?", blockNumber)
            jdbc.update(
                "UPDATE $table SET superseded_at = NULL WHERE superseded_at >= ?",
                blockNumber,
            )
        }
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE ${TABLES.joinToString()}")
    }

    override fun prune(before: Long): Int = TABLES.sumOf {
        jdbc.update("DELETE FROM $it WHERE superseded_at < ?", before)
    }

    /** One row of an entry and the block that replaces it: the next row of its key, if any. */
    private class Version<T>(val row: T, val supersededAt: Long?)

    companion object {
        /**
         * An entry's rows by key, ordered by block and one per block: a batch spanning the entry
         * carries each row's successor, where a batch per block left the next one to close it.
         */
        private fun <T : ActionSummaryRow, K> chains(rows: List<T>, key: (T) -> K): List<List<T>> =
            rows.groupBy(key).values.map {
                it.associateBy { row -> row.blockNumber }.toSortedMap().values.toList()
            }

        private fun <T : ActionSummaryRow> versions(chain: List<T>): List<Version<T>> =
            chain.mapIndexed { i, row ->
                Version(row, chain.getOrNull(i + 1)?.blockNumber)
            }

        /**
         * Closes every key the entry opens in one statement, where a statement per key had the
         * server parse and plan thousands of them for a single fast-sync batch.
         */
        private fun supersede(
            table: String,
            kind: ActionPeriodKind,
            keyColumns: List<String>,
            keyArrayTypes: List<String>,
        ): String {
            val columns = keyColumns + listOfNotNull(kind.keyColumn)
            val types = keyArrayTypes + listOfNotNull(kind.keyArrayType)
            return "UPDATE $table t SET superseded_at = k.at " +
                "FROM unnest(${types.joinToString { "CAST(? AS $it)" }}, ?::bigint[]) " +
                "AS k(${(columns + "at").joinToString()}) " +
                "WHERE ${columns.joinToString(" AND ") { "t.$it = k.$it" }} " +
                "AND t.superseded_at IS NULL AND t.block_number < k.at"
        }

        /** [keys] first, then the period column's values where it has one, then the blocks. */
        private fun arguments(
            keys: List<Array<out Any>>,
            opening: List<ActionSummaryRow>,
        ): Array<Any> =
            (keys +
                    listOfNotNull(
                        opening
                            .takeIf { it.first().period.kind.keyColumn != null }
                            ?.map { keyValue(it.period).toString() }
                            ?.toTypedArray(),
                        opening.map { it.blockNumber }.toTypedArray(),
                    ))
                .toTypedArray()

        private val ENTITY_SUPERSEDE =
            ActionPeriodKind.entries.associateWith {
                supersede(
                    entityTable(it),
                    it,
                    listOf("entity_type", "entity"),
                    listOf("$ENTITY_TYPE[]", "bytea[]"),
                )
            }

        private val APP_USER_SUPERSEDE =
            ActionPeriodKind.entries.associateWith {
                supersede(
                    appUserTable(it),
                    it,
                    listOf("app_id", "wallet"),
                    listOf("bytea[]", "bytea[]"),
                )
            }

        private val TABLES =
            ActionPeriodKind.entries.flatMap { listOf(entityTable(it), appUserTable(it)) }

        private fun key(kind: ActionPeriodKind, prefix: String = ""): String =
            kind.keyColumn?.let { "AND $prefix$it = ? " } ?: ""

        private fun insert(
            table: String,
            keyColumns: List<String>,
            keyPlaceholders: List<String>,
            kind: ActionPeriodKind,
            measures: List<String>,
        ): String {
            val period = listOfNotNull(kind.keyColumn)
            val written = measures + "superseded_at"
            val columns = keyColumns + period + "block_number" + written
            val placeholders =
                keyPlaceholders +
                    period.map { "?" } +
                    "?" +
                    written.map { if (it == "total_impact") "CAST(? AS jsonb)" else "?" }
            return "INSERT INTO $table (${columns.joinToString()}) " +
                "VALUES (${placeholders.joinToString()}) " +
                "ON CONFLICT (${(keyColumns + period + "block_number").joinToString()}) " +
                "DO UPDATE SET ${written.joinToString { "$it = EXCLUDED.$it" }}"
        }

        private val ENTITY_INSERT =
            ActionPeriodKind.entries.associateWith {
                insert(
                    entityTable(it),
                    listOf("entity_type", "entity"),
                    listOf("CAST(? AS $ENTITY_TYPE)", "?"),
                    it,
                    MEASURES + "unique_users",
                )
            }

        private val APP_USER_INSERT =
            ActionPeriodKind.entries.associateWith {
                insert(appUserTable(it), listOf("app_id", "wallet"), listOf("?", "?"), it, MEASURES)
            }
    }
}
