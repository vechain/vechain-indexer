package org.vechain.indexer.b3tr.action.repository

import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.ResultSet
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.b3tr.action.UserDailyActionSummary
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("b3tr", "b3tr-actions", "b3tr-user-daily-action-summary")
@Repository
open class PostgresUserDailyActionSummaryRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<UserDailyActionSummary>(
        jdbcTemplate,
        namedJdbcTemplate,
        objectMapper,
    ),
    UserDailyActionSummaryRepository {

    override fun tableName(): String = "b3tr_user_action_summaries_daily"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        entity, entity_type, date, actions_rewarded, total_reward_amount, total_impact
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb"

    override fun insertParams(doc: UserDailyActionSummary): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.entity,
            doc.entityType.name,
            doc.date,
            doc.actionsRewarded,
            doc.totalRewardAmount,
            doc.totalImpact?.let { objectMapper.writeValueAsString(it) },
        )

    override fun mapRow(rs: ResultSet): UserDailyActionSummary {
        val impactJson = rs.getString("total_impact")
        val impact = impactJson?.let { objectMapper.readValue(it, Impact::class.java) }

        return UserDailyActionSummary(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            entity = rs.getString("entity"),
            entityType = EntityType.valueOf(rs.getString("entity_type")),
            date = rs.getString("date"),
            actionsRewarded = rs.getLong("actions_rewarded"),
            totalRewardAmount = rs.getBigDecimal("total_reward_amount"),
            totalImpact = impact,
        )
    }

    override fun saveAllVersioned(
        updated: List<UserDailyActionSummary>,
        existing: List<UserDailyActionSummary>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findAllByEntityAndDateBetween(
        entity: String,
        startDate: String,
        endDate: String,
        pageable: Pageable,
    ): Slice<UserDailyActionSummary> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE entity = ? AND date >= ? AND date <= ? AND is_current = true
                ORDER BY date DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                entity,
                startDate,
                endDate,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAllByEntityTypeAndDate(
        entityType: EntityType,
        date: String,
        pageable: Pageable,
    ): Slice<UserDailyActionSummary> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE entity_type = ? AND date = ? AND is_current = true
                ORDER BY total_reward_amount DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                entityType.name,
                date,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByEntityAndDate(entity: String, date: String): UserDailyActionSummary? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE entity = ? AND date = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                entity,
                date,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    @Cacheable(
        value = ["user_daily_action_countByTotalRewardAmountGreaterThanAndEntityTypeAndDate"],
        key = "#totalRewardAmount + '-' + #entityType + '-' + #date",
    )
    override fun countByTotalRewardAmountGreaterThanAndEntityTypeAndDate(
        totalRewardAmount: BigDecimal,
        entityType: EntityType,
        date: String,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE total_reward_amount > ? AND entity_type = ? AND date = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            totalRewardAmount,
            entityType.name,
            date,
        ) ?: 0L
    }

    @Cacheable(
        value = ["user_daily_action_countByActionsRewardedGreaterThanAndEntityTypeAndDate"],
        key = "#actionsRewarded + '-' + #entityType + '-' + #date",
    )
    override fun countByActionsRewardedGreaterThanAndEntityTypeAndDate(
        actionsRewarded: Long,
        entityType: EntityType,
        date: String,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE actions_rewarded > ? AND entity_type = ? AND date = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            actionsRewarded,
            entityType.name,
            date,
        ) ?: 0L
    }

    @Cacheable(
        value = ["user_daily_action_countByEntityTypeAndDate"],
        key = "#entityType + '-' + #date",
    )
    override fun countByEntityTypeAndDate(entityType: EntityType, date: String): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE entity_type = ? AND date = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            entityType.name,
            date,
        ) ?: 0L
    }
}
