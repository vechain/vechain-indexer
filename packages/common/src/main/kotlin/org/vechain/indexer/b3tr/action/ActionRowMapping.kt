package org.vechain.indexer.b3tr.action

import com.fasterxml.jackson.core.type.TypeReference
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDate
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresJson

/** The `b3tr_action` rows to [EntityActionSummary] and [AppUserActionSummary] and back. */
object ActionRowMapping {
    const val SCHEMA = "b3tr_action"
    const val ENTITY_TYPE = "$SCHEMA.entity_type"

    /** The columns after the key and `block_number`, in the order [bindMeasures] sets them. */
    val MEASURES =
        listOf(
            "block_id",
            "block_timestamp",
            "actions_rewarded",
            "total_reward_amount",
            "total_impact",
        )

    private val impactType = object : TypeReference<Impact>() {}

    fun entityTable(kind: ActionPeriodKind) = "$SCHEMA.${kind.entityTable}"

    fun appUserTable(kind: ActionPeriodKind) = "$SCHEMA.${kind.appUserTable}"

    /** The GLOBAL row has no address, so its key is the empty byte string. */
    fun entityBytes(entityType: EntityType, entity: String): ByteArray =
        if (entityType == EntityType.GLOBAL) ByteArray(0) else bytes(entity)

    /** What the period's key column is bound to: a DATE, an INT, or nothing for all time. */
    fun keyValue(period: ActionPeriod): Any? =
        when (period) {
            is ActionPeriod.AllTime -> null
            is ActionPeriod.Day -> LocalDate.parse(period.date)
            is ActionPeriod.Round -> period.roundId
        }

    fun readPeriod(rs: ResultSet, kind: ActionPeriodKind): ActionPeriod =
        when (kind) {
            ActionPeriodKind.ALL_TIME -> ActionPeriod.AllTime
            ActionPeriodKind.DAILY ->
                ActionPeriod.Day(rs.getObject("date", LocalDate::class.java).toString())
            ActionPeriodKind.ROUND -> ActionPeriod.Round(rs.getInt("round_id"))
        }

    fun bindEntity(ps: PreparedStatement, s: EntityActionSummary) {
        var i = 1
        ps.setString(i++, s.entityType.name)
        ps.setBytes(i++, entityBytes(s.entityType, s.entity))
        keyValue(s.period)?.let { ps.setObject(i++, it) }
        ps.setLong(i++, s.blockNumber)
        i =
            bindMeasures(
                ps,
                i,
                s.blockId,
                s.blockTimestamp,
                s.actionsRewarded,
                s.totalRewardAmount,
                s.totalImpact,
            )
        ps.setLong(i, s.uniqueUsers)
    }

    fun bindAppUser(ps: PreparedStatement, s: AppUserActionSummary) {
        var i = 1
        ps.setBytes(i++, bytes(s.appId))
        ps.setBytes(i++, bytes(s.user))
        keyValue(s.period)?.let { ps.setObject(i++, it) }
        ps.setLong(i++, s.blockNumber)
        bindMeasures(
            ps,
            i,
            s.blockId,
            s.blockTimestamp,
            s.actionsRewarded,
            s.totalRewardAmount,
            s.totalImpact,
        )
    }

    private fun bindMeasures(
        ps: PreparedStatement,
        from: Int,
        blockId: String,
        blockTimestamp: Long,
        actionsRewarded: Long,
        totalRewardAmount: java.math.BigDecimal,
        totalImpact: Impact?,
    ): Int {
        var i = from
        ps.setBytes(i++, bytes(blockId))
        ps.setLong(i++, blockTimestamp)
        ps.setLong(i++, actionsRewarded)
        ps.setBigDecimal(i++, totalRewardAmount)
        ps.setString(i++, PostgresJson.write(totalImpact))
        return i
    }

    fun readEntity(rs: ResultSet, kind: ActionPeriodKind): EntityActionSummary {
        val entityType = EntityType.valueOf(rs.getString("entity_type"))
        return EntityActionSummary(
            entityType = entityType,
            entity =
                if (entityType == EntityType.GLOBAL) EntityType.GLOBAL.name
                else hex(rs.getBytes("entity")),
            period = readPeriod(rs, kind),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            actionsRewarded = rs.getLong("actions_rewarded"),
            totalRewardAmount = rs.getBigDecimal("total_reward_amount"),
            totalImpact = PostgresJson.read(rs.getString("total_impact"), impactType),
            uniqueUsers = rs.getLong("unique_users"),
        )
    }

    fun readAppUser(rs: ResultSet, kind: ActionPeriodKind): AppUserActionSummary =
        AppUserActionSummary(
            appId = hex(rs.getBytes("app_id")),
            user = hex(rs.getBytes("wallet")),
            period = readPeriod(rs, kind),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            actionsRewarded = rs.getLong("actions_rewarded"),
            totalRewardAmount = rs.getBigDecimal("total_reward_amount"),
            totalImpact = PostgresJson.read(rs.getString("total_impact"), impactType),
        )
}
