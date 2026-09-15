package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.action.ActionRowMapping.ENTITY_TYPE
import org.vechain.indexer.b3tr.action.ActionRowMapping.appUserTable
import org.vechain.indexer.b3tr.action.ActionRowMapping.entityBytes
import org.vechain.indexer.b3tr.action.ActionRowMapping.entityTable
import org.vechain.indexer.b3tr.action.ActionRowMapping.keyValue
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** The rollups behind the `/b3tr/actions` overviews and leaderboards, each read in one query. */
@Repository
@ConditionalOnPostgres
open class ActionReadRepository(@Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate) {

    open fun findEntity(
        period: ActionPeriod,
        entityType: EntityType,
        entity: String,
    ): EntityActionSummary? {
        val kind = period.kind
        return jdbc
            .query(
                "SELECT * FROM ${entityTable(kind)} WHERE superseded_at IS NULL " +
                    "AND entity_type = CAST(? AS $ENTITY_TYPE) AND entity = ? ${key(kind)}",
                { rs, _ -> ActionRowMapping.readEntity(rs, kind) },
                *listOfNotNull(entityType.name, entityBytes(entityType, entity), keyValue(period))
                    .toTypedArray(),
            )
            .firstOrNull()
    }

    open fun findAppUser(period: ActionPeriod, appId: String, user: String): AppUserActionSummary? {
        val kind = period.kind
        return jdbc
            .query(
                "SELECT * FROM ${appUserTable(kind)} WHERE superseded_at IS NULL " +
                    "AND app_id = ? AND wallet = ? ${key(kind)}",
                { rs, _ -> ActionRowMapping.readAppUser(rs, kind) },
                *listOfNotNull(bytes(appId), bytes(user), keyValue(period)).toTypedArray(),
            )
            .firstOrNull()
    }

    /** The apps that rewarded [user] over [period]. */
    open fun findAppIds(period: ActionPeriod, user: String): List<String> {
        val kind = period.kind
        return jdbc.query(
            "SELECT app_id FROM ${appUserTable(kind)} WHERE superseded_at IS NULL " +
                "AND wallet = ? ${key(kind)}ORDER BY app_id",
            { rs, _ -> hex(rs.getBytes("app_id")) },
            *listOfNotNull(bytes(user), keyValue(period)).toTypedArray(),
        )
    }

    /** How many entities of [entityType] stand above [value] on [on]; the rank is one more. */
    @Cacheable(
        value = ["b3tr_action_rank_counts"],
        key = "'entity|' + #period + '|' + #entityType + '|' + #on + '|' + #value.toPlainString()",
    )
    open fun countEntitiesAbove(
        period: ActionPeriod,
        entityType: EntityType,
        on: ActionSortField,
        value: BigDecimal,
    ): Long {
        val kind = period.kind
        return jdbc.queryForObject(
            "SELECT count(*) FROM ${entityTable(kind)} WHERE superseded_at IS NULL " +
                "AND entity_type = CAST(? AS $ENTITY_TYPE) ${key(kind)}AND ${on.column} > ?",
            Long::class.java,
            *listOfNotNull(entityType.name, keyValue(period), on.argument(value)).toTypedArray(),
        )!!
    }

    /** How many of [appId]'s wallets stand above [value] on [on]; the rank is one more. */
    @Cacheable(
        value = ["b3tr_action_rank_counts"],
        key = "'app|' + #period + '|' + #appId + '|' + #on + '|' + #value.toPlainString()",
    )
    open fun countAppUsersAbove(
        period: ActionPeriod,
        appId: String,
        on: ActionSortField,
        value: BigDecimal,
    ): Long {
        val kind = period.kind
        return jdbc.queryForObject(
            "SELECT count(*) FROM ${appUserTable(kind)} WHERE superseded_at IS NULL " +
                "AND app_id = ? ${key(kind)}AND ${on.column} > ?",
            Long::class.java,
            *listOfNotNull(bytes(appId), keyValue(period), on.argument(value)).toTypedArray(),
        )!!
    }

    /**
     * The entities of [entityType] after the cursor, in [direction] of [sortBy] with the entity
     * breaking ties ascending either way, as the Mongo keyset did.
     */
    open fun leaderboard(
        period: ActionPeriod,
        entityType: EntityType,
        sortBy: ActionSortField,
        direction: Direction,
        limit: Int,
        cursorValue: String?,
        cursorEntity: String?,
    ): List<EntityActionSummary> {
        val kind = period.kind
        val args = mutableListOf<Any>(entityType.name)
        keyValue(period)?.let { args += it }
        return jdbc.query(
            "SELECT * FROM ${entityTable(kind)} WHERE superseded_at IS NULL " +
                "AND entity_type = CAST(? AS $ENTITY_TYPE) ${key(kind)}" +
                keyset(sortBy, direction, cursorValue, cursorEntity, "entity", args) +
                "ORDER BY ${sortBy.column} ${direction.name}, entity LIMIT ?",
            { rs, _ -> ActionRowMapping.readEntity(rs, kind) },
            *(args + limit).toTypedArray(),
        )
    }

    /** [appId]'s wallets after the cursor, ordered as [leaderboard] is. */
    open fun appLeaderboard(
        period: ActionPeriod,
        appId: String,
        sortBy: ActionSortField,
        direction: Direction,
        limit: Int,
        cursorValue: String?,
        cursorWallet: String?,
    ): List<AppUserActionSummary> {
        val kind = period.kind
        val args = mutableListOf<Any>(bytes(appId))
        keyValue(period)?.let { args += it }
        return jdbc.query(
            "SELECT * FROM ${appUserTable(kind)} WHERE superseded_at IS NULL " +
                "AND app_id = ? ${key(kind)}" +
                keyset(sortBy, direction, cursorValue, cursorWallet, "wallet", args) +
                "ORDER BY ${sortBy.column} ${direction.name}, wallet LIMIT ?",
            { rs, _ -> ActionRowMapping.readAppUser(rs, kind) },
            *(args + limit).toTypedArray(),
        )
    }

    /** A wallet's days between [startDate] and [endDate] inclusive, one page in [direction]. */
    open fun findDailyRange(
        user: String,
        startDate: String,
        endDate: String,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<EntityActionSummary> =
        jdbc.query(
            "SELECT * FROM ${entityTable(ActionPeriodKind.DAILY)} WHERE superseded_at IS NULL " +
                "AND entity_type = 'USER' AND entity = ? AND date BETWEEN ? AND ? " +
                "ORDER BY date ${direction.name} OFFSET ? LIMIT ?",
            { rs, _ -> ActionRowMapping.readEntity(rs, ActionPeriodKind.DAILY) },
            bytes(user),
            LocalDate.parse(startDate),
            LocalDate.parse(endDate),
            offset,
            limit,
        )

    /** The newest round on record: the one still open, or the last one indexed. */
    open fun latestRound(): Int? =
        jdbc.queryForObject(
            "SELECT max(round_id) FROM ${entityTable(ActionPeriodKind.ROUND)} " +
                "WHERE superseded_at IS NULL AND entity_type = 'GLOBAL'",
            Int::class.javaObjectType,
        )

    private fun key(kind: ActionPeriodKind): String = kind.keyColumn?.let { "AND $it = ? " } ?: ""

    /** The rows past the cursor record: strictly past it on the sort, or tied and past its key. */
    private fun keyset(
        sortBy: ActionSortField,
        direction: Direction,
        cursorValue: String?,
        cursorKey: String?,
        keyColumn: String,
        args: MutableList<Any>,
    ): String {
        if (cursorValue == null || cursorKey == null) return ""
        val value = sortBy.argument(cursorValue.toBigDecimal())
        val past = if (direction == Direction.DESC) "<" else ">"
        args += value
        args += value
        args += bytes(cursorKey)
        return "AND (${sortBy.column} $past ? OR (${sortBy.column} = ? AND $keyColumn > ?)) "
    }
}
