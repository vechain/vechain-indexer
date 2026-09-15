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
            update.entities
                .filter { it.period.kind == kind }
                .groupBy { it.blockNumber }
                .toSortedMap()
                .forEach { (block, rows) -> saveEntities(kind, block, rows) }
            update.appUsers
                .filter { it.period.kind == kind }
                .groupBy { it.blockNumber }
                .toSortedMap()
                .forEach { (block, rows) -> saveAppUsers(kind, block, rows) }
        }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveEntities(
        kind: ActionPeriodKind,
        blockNumber: Long,
        block: List<EntityActionSummary>,
    ) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = block.associateBy { Triple(it.entityType, it.entity, it.period) }.values.toList()
        jdbc.batchUpdate(
            "UPDATE ${entityTable(kind)} SET superseded_at = ? " +
                "WHERE entity_type = CAST(? AS $ENTITY_TYPE) AND entity = ? ${key(kind)}" +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, s ->
            var i = 1
            ps.setLong(i++, blockNumber)
            ps.setString(i++, s.entityType.name)
            ps.setBytes(i++, entityBytes(s.entityType, s.entity))
            keyValue(s.period)?.let { ps.setObject(i++, it) }
            ps.setLong(i, blockNumber)
        }
        jdbc.batchUpdate(ENTITY_INSERT.getValue(kind), rows, rows.size) { ps, s ->
            ActionRowMapping.bindEntity(ps, s)
        }
    }

    private fun saveAppUsers(
        kind: ActionPeriodKind,
        blockNumber: Long,
        block: List<AppUserActionSummary>,
    ) {
        val rows = block.associateBy { Triple(it.appId, it.user, it.period) }.values.toList()
        jdbc.batchUpdate(
            "UPDATE ${appUserTable(kind)} SET superseded_at = ? " +
                "WHERE app_id = ? AND wallet = ? ${key(kind)}" +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, s ->
            var i = 1
            ps.setLong(i++, blockNumber)
            ps.setBytes(i++, bytes(s.appId))
            ps.setBytes(i++, bytes(s.user))
            keyValue(s.period)?.let { ps.setObject(i++, it) }
            ps.setLong(i, blockNumber)
        }
        jdbc.batchUpdate(APP_USER_INSERT.getValue(kind), rows, rows.size) { ps, s ->
            ActionRowMapping.bindAppUser(ps, s)
        }
    }

    /** The current row of each of [keys] over [period]: wallets, apps and the GLOBAL row. */
    open fun findCurrentEntities(
        period: ActionPeriod,
        keys: Collection<Pair<EntityType, String>>,
    ): List<EntityActionSummary> {
        if (keys.isEmpty()) return emptyList()
        val kind = period.kind
        val of = { type: EntityType ->
            keys.filter { it.first == type }.map { bytes(it.second) }.distinct().toTypedArray()
        }
        return jdbc.query(
            "SELECT * FROM ${entityTable(kind)} WHERE superseded_at IS NULL ${key(kind)}" +
                "AND ((entity_type = 'USER' AND entity = ANY(?)) " +
                "OR (entity_type = 'APP' AND entity = ANY(?)) OR (entity_type = 'GLOBAL' AND ?))",
            { rs, _ -> ActionRowMapping.readEntity(rs, kind) },
            *listOfNotNull(
                    keyValue(period),
                    of(EntityType.USER),
                    of(EntityType.APP),
                    keys.any { it.first == EntityType.GLOBAL },
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

    companion object {
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
            val columns = keyColumns + period + "block_number" + measures
            val placeholders =
                keyPlaceholders +
                    period.map { "?" } +
                    "?" +
                    measures.map { if (it == "total_impact") "CAST(? AS jsonb)" else "?" }
            return "INSERT INTO $table (${columns.joinToString()}) " +
                "VALUES (${placeholders.joinToString()}) " +
                "ON CONFLICT (${(keyColumns + period + "block_number").joinToString()}) " +
                "DO UPDATE SET ${measures.joinToString { "$it = EXCLUDED.$it" }}"
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
