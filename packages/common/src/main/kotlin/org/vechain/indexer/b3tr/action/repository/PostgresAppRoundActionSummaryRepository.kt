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
import org.vechain.indexer.b3tr.action.AppRoundActionSummary
import org.vechain.indexer.b3tr.action.Impact
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
@Repository
open class PostgresAppRoundActionSummaryRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<AppRoundActionSummary>(
        jdbcTemplate,
        namedJdbcTemplate,
        objectMapper,
    ),
    AppRoundActionSummaryRepository {

    override fun tableName(): String = "b3tr_app_action_summaries_round"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        app_id, user_address, round_id, actions_rewarded, total_reward_amount, total_impact
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb"

    override fun insertParams(doc: AppRoundActionSummary): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.appId,
            doc.user,
            doc.roundId,
            doc.actionsRewarded,
            doc.totalRewardAmount,
            doc.totalImpact?.let { objectMapper.writeValueAsString(it) },
        )

    override fun mapRow(rs: ResultSet): AppRoundActionSummary {
        val impactJson = rs.getString("total_impact")
        val impact = impactJson?.let { objectMapper.readValue(it, Impact::class.java) }

        return AppRoundActionSummary(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            appId = rs.getString("app_id"),
            user = rs.getString("user_address"),
            roundId = rs.getInt("round_id"),
            actionsRewarded = rs.getLong("actions_rewarded"),
            totalRewardAmount = rs.getBigDecimal("total_reward_amount"),
            totalImpact = impact,
        )
    }

    override fun saveAllVersioned(
        updated: List<AppRoundActionSummary>,
        existing: List<AppRoundActionSummary>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findFirstByOrderByBlockNumberDesc(): AppRoundActionSummary? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE is_current = true
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                mapRow(rs)
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findAllByAppIdAndRoundId(
        appId: String,
        roundId: Int,
        pageable: Pageable,
    ): Slice<AppRoundActionSummary> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE app_id = ? AND round_id = ? AND is_current = true
                ORDER BY total_reward_amount DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                appId,
                roundId,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAppIdsByUserAndRoundId(
        user: String,
        roundId: Int,
    ): List<AppRoundActionSummary> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE user_address = ? AND round_id = ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            user,
            roundId,
        )
    }

    override fun findByAppIdAndUserAndRoundId(
        appId: String,
        user: String,
        roundId: Int,
    ): AppRoundActionSummary? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE app_id = ? AND user_address = ? AND round_id = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                appId,
                user,
                roundId,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    @Cacheable(value = ["app_round_countByAppIdAndRoundId"], key = "#appId + '-' + #roundId")
    override fun countByAppIdAndRoundId(appId: String, roundId: Int): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE app_id = ? AND round_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            appId,
            roundId,
        ) ?: 0L
    }

    @Cacheable(
        value = ["app_round_countByTotalRewardAmountGreaterThanAndAppIdAndRoundId"],
        key =
            "#totalRewardAmount.stripTrailingZeros().toPlainString() + '-' + #appId + '-' + #roundId",
    )
    override fun countByTotalRewardAmountGreaterThanAndAppIdAndRoundId(
        totalRewardAmount: BigDecimal,
        appId: String,
        roundId: Int,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE total_reward_amount > ? AND app_id = ? AND round_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            totalRewardAmount,
            appId,
            roundId,
        ) ?: 0L
    }

    @Cacheable(
        value = ["app_round_countByActionsRewardedGreaterThanAndAppIdAndRoundId"],
        key = "#actionsRewarded + '-' + #appId + '-' + #roundId",
    )
    override fun countByActionsRewardedGreaterThanAndAppIdAndRoundId(
        actionsRewarded: Long,
        appId: String,
        roundId: Int,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE actions_rewarded > ? AND app_id = ? AND round_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            actionsRewarded,
            appId,
            roundId,
        ) ?: 0L
    }
}
