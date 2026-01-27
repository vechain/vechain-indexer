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
import org.vechain.indexer.b3tr.action.UserRoundActionSummary
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
@Repository
open class PostgresUserRoundActionSummaryRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<UserRoundActionSummary>(
        jdbcTemplate,
        namedJdbcTemplate,
        objectMapper,
    ),
    UserRoundActionSummaryRepository {

    override fun tableName(): String = "b3tr_user_action_summaries_round"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        entity, entity_type, round_id, actions_rewarded, total_reward_amount, total_impact
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb"

    override fun insertParams(doc: UserRoundActionSummary): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.entity,
            doc.entityType.name,
            doc.roundId,
            doc.actionsRewarded,
            doc.totalRewardAmount,
            doc.totalImpact?.let { objectMapper.writeValueAsString(it) },
        )

    override fun mapRow(rs: ResultSet): UserRoundActionSummary {
        val impactJson = rs.getString("total_impact")
        val impact = impactJson?.let { objectMapper.readValue(it, Impact::class.java) }

        return UserRoundActionSummary(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            entity = rs.getString("entity"),
            entityType = EntityType.valueOf(rs.getString("entity_type")),
            roundId = rs.getInt("round_id"),
            actionsRewarded = rs.getLong("actions_rewarded"),
            totalRewardAmount = rs.getBigDecimal("total_reward_amount"),
            totalImpact = impact,
        )
    }

    override fun saveAllVersioned(
        updated: List<UserRoundActionSummary>,
        existing: List<UserRoundActionSummary>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findFirstByOrderByBlockNumberDesc(): UserRoundActionSummary? {
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

    override fun findAllByEntityTypeAndRoundId(
        entityType: EntityType,
        roundId: Int,
        pageable: Pageable,
    ): Slice<UserRoundActionSummary> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE entity_type = ? AND round_id = ? AND is_current = true
                ORDER BY total_reward_amount DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                entityType.name,
                roundId,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByEntityAndRoundId(entity: String, roundId: Int): UserRoundActionSummary? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE entity = ? AND round_id = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                entity,
                roundId,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    @Cacheable(
        value = ["user_round_countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId"],
        key = "#totalRewardAmount + '-' + #entityType + '-' + #roundId",
    )
    override fun countByTotalRewardAmountGreaterThanAndEntityTypeAndRoundId(
        totalRewardAmount: BigDecimal,
        entityType: EntityType,
        roundId: Int,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE total_reward_amount > ? AND entity_type = ? AND round_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            totalRewardAmount,
            entityType.name,
            roundId,
        ) ?: 0L
    }

    @Cacheable(
        value = ["user_round_countByActionsRewardedGreaterThanAndEntityTypeAndRoundId"],
        key = "#actionsRewarded + '-' + #entityType + '-' + #roundId",
    )
    override fun countByActionsRewardedGreaterThanAndEntityTypeAndRoundId(
        actionsRewarded: Long,
        entityType: EntityType,
        roundId: Int,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE actions_rewarded > ? AND entity_type = ? AND round_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            actionsRewarded,
            entityType.name,
            roundId,
        ) ?: 0L
    }

    @Cacheable(
        value = ["user_round_countByEntityTypeAndRoundId"],
        key = "#entityType + '-' + #roundId",
    )
    override fun countByEntityTypeAndRoundId(entityType: EntityType, roundId: Int): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE entity_type = ? AND round_id = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            entityType.name,
            roundId,
        ) ?: 0L
    }
}
