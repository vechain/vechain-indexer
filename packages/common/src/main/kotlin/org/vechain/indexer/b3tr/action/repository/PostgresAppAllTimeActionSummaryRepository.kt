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
import org.vechain.indexer.b3tr.action.AppAllTimeActionSummary
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
@Repository
open class PostgresAppAllTimeActionSummaryRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<AppAllTimeActionSummary>(
        jdbcTemplate,
        namedJdbcTemplate,
        objectMapper,
    ),
    AppAllTimeActionSummaryRepository {

    override fun tableName(): String = "b3tr_app_action_summaries_all_time"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        app_id, user_address, actions_rewarded, total_reward_amount, total_impact
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb"

    override fun insertParams(doc: AppAllTimeActionSummary): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.appId,
            doc.user,
            doc.actionsRewarded,
            doc.totalRewardAmount,
            doc.totalImpact?.let { objectMapper.writeValueAsString(it) },
        )

    override fun mapRow(rs: ResultSet): AppAllTimeActionSummary {
        val impactJson = rs.getString("total_impact")
        val impact = impactJson?.let { objectMapper.readValue(it, Impact::class.java) }

        return AppAllTimeActionSummary(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            appId = rs.getString("app_id"),
            user = rs.getString("user_address"),
            actionsRewarded = rs.getLong("actions_rewarded"),
            totalRewardAmount = rs.getBigDecimal("total_reward_amount"),
            totalImpact = impact,
        )
    }

    override fun saveAllVersioned(
        updated: List<AppAllTimeActionSummary>,
        existing: List<AppAllTimeActionSummary>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findAllByAppId(appId: String, pageable: Pageable): Slice<AppAllTimeActionSummary> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE app_id = ? AND is_current = true
                ORDER BY total_reward_amount DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                appId,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAppIdsByUser(user: String): List<AppAllTimeActionSummary> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE user_address = ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            user,
        )
    }

    override fun findByAppIdAndUser(appId: String, user: String): AppAllTimeActionSummary? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE app_id = ? AND user_address = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                appId,
                user,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    @Cacheable(value = ["app_all_time_action_countByAppId"], key = "#appId")
    override fun countByAppId(appId: String): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE app_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            appId,
        ) ?: 0L
    }

    @Cacheable(
        value = ["app_all_time_action_countByTotalRewardAmountGreaterThanAndAppId"],
        key = "#totalRewardAmount.stripTrailingZeros().toPlainString() + '-' + #appId",
    )
    override fun countByTotalRewardAmountGreaterThanAndAppId(
        totalRewardAmount: BigDecimal,
        appId: String,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE total_reward_amount > ? AND app_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            totalRewardAmount,
            appId,
        ) ?: 0L
    }

    @Cacheable(
        value = ["app_all_time_action_countByActionsRewardedGreaterThanAndAppId"],
        key = "#actionsRewarded + '-' + #appId",
    )
    override fun countByActionsRewardedGreaterThanAndAppId(
        actionsRewarded: Long,
        appId: String,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE actions_rewarded > ? AND app_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            actionsRewarded,
            appId,
        ) ?: 0L
    }
}
