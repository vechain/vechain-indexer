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
import org.vechain.indexer.b3tr.action.AppDailyActionSummary
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.postgres.PostgresVersionedRepository
import org.vechain.indexer.thor.HexUtils

@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
@Repository
open class PostgresAppDailyActionSummaryRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<AppDailyActionSummary>(
        jdbcTemplate,
        namedJdbcTemplate,
        objectMapper,
    ),
    AppDailyActionSummaryRepository {

    override fun tableName(): String = "b3tr_app_action_summaries_daily"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        app_id, user_address, date, actions_rewarded, total_reward_amount, total_impact
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb"

    override fun insertParams(doc: AppDailyActionSummary): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.appId,
            doc.user,
            doc.date,
            doc.actionsRewarded,
            doc.totalRewardAmount,
            doc.totalImpact?.let { objectMapper.writeValueAsString(it) },
        )

    override fun mapRow(rs: ResultSet): AppDailyActionSummary {
        val impactJson = rs.getString("total_impact")
        val impact = impactJson?.let { objectMapper.readValue(it, Impact::class.java) }

        return AppDailyActionSummary(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            appId = rs.getString("app_id"),
            user = rs.getString("user_address"),
            date = rs.getString("date"),
            actionsRewarded = rs.getLong("actions_rewarded"),
            totalRewardAmount = rs.getBigDecimal("total_reward_amount"),
            totalImpact = impact,
        )
    }

    override fun saveAllVersioned(
        updated: List<AppDailyActionSummary>,
        existing: List<AppDailyActionSummary>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findAllByAppIdAndDate(
        appId: String,
        date: String,
        pageable: Pageable,
    ): Slice<AppDailyActionSummary> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE app_id = ? AND date = ? AND is_current = true
                ORDER BY total_reward_amount DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                appId,
                date,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByUserAndDate(user: String, date: String): List<AppDailyActionSummary> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE user_address = ? AND date = ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            user,
            date,
        )
    }

    override fun findByAppIdAndUserAndDate(
        appId: String,
        user: String,
        date: String,
    ): AppDailyActionSummary? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE app_id = ? AND user_address = ? AND date = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                appId,
                user,
                date,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    @Cacheable(value = ["app_daily_action_countByAppIdAndDate"], key = "#appId + '-' + #date")
    override fun countByAppIdAndDate(appId: String, date: String): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE app_id = ? AND date = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            appId,
            date,
        ) ?: 0L
    }

    @Cacheable(
        value = ["app_daily_action_countByTotalRewardAmountGreaterThanAndAppIdAndDate"],
        key = "#totalRewardAmount.stripTrailingZeros().toPlainString() + '-' + #appId + '-' + #date",
    )
    override fun countByTotalRewardAmountGreaterThanAndAppIdAndDate(
        totalRewardAmount: BigDecimal,
        appId: String,
        date: String,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE total_reward_amount > ? AND app_id = ? AND date = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            totalRewardAmount,
            appId,
            date,
        ) ?: 0L
    }

    @Cacheable(
        value = ["app_daily_action_countByActionsRewardedGreaterThanAndAppIdAndDate"],
        key = "#actionsRewarded + '-' + #appId + '-' + #date",
    )
    override fun countByActionsRewardedGreaterThanAndAppIdAndDate(
        actionsRewarded: Long,
        appId: String,
        date: String,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE actions_rewarded > ? AND app_id = ? AND date = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            actionsRewarded,
            appId,
            date,
        ) ?: 0L
    }

    // Implementation of CustomAppDailyActionSummaryRepository
    override fun findAppUserOverviewsByFilters(
        appId: String?,
        user: String?,
        startDate: String?,
        endDate: String?,
        pageable: Pageable,
    ): Slice<AppDailyActionSummary> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        conditions.add("is_current = true")

        appId?.let {
            conditions.add("app_id = ?")
            params.add(HexUtils.normalise(it))
        }
        user?.let {
            conditions.add("user_address = ?")
            params.add(HexUtils.normalise(it))
        }
        startDate?.let {
            conditions.add("date >= ?")
            params.add(it)
        }
        endDate?.let {
            conditions.add("date <= ?")
            params.add(it)
        }

        val whereClause = conditions.joinToString(" AND ")
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        params.add(limit)
        params.add(offset)

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE $whereClause
                ORDER BY date DESC, total_reward_amount DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                *params.toTypedArray(),
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }
}
