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
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummary
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("b3tr", "b3tr-actions", "b3tr-user-all-time-action-summary")
@Repository
open class PostgresUserAllTimeActionSummaryRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<UserAllTimeActionSummary>(
        jdbcTemplate,
        namedJdbcTemplate,
        objectMapper,
    ),
    UserAllTimeActionSummaryRepository {

    override fun tableName(): String = "b3tr_user_action_summaries_all_time"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        entity, entity_type, actions_rewarded, total_reward_amount, total_impact
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb"

    override fun insertParams(doc: UserAllTimeActionSummary): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.entity,
            doc.entityType.name,
            doc.actionsRewarded,
            doc.totalRewardAmount,
            doc.totalImpact?.let { objectMapper.writeValueAsString(it) },
        )

    override fun mapRow(rs: ResultSet): UserAllTimeActionSummary {
        val impactJson = rs.getString("total_impact")
        val impact = impactJson?.let { objectMapper.readValue(it, Impact::class.java) }

        return UserAllTimeActionSummary(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            entity = rs.getString("entity"),
            entityType = EntityType.valueOf(rs.getString("entity_type")),
            actionsRewarded = rs.getLong("actions_rewarded"),
            totalRewardAmount = rs.getBigDecimal("total_reward_amount"),
            totalImpact = impact,
        )
    }

    override fun saveAllVersioned(
        updated: List<UserAllTimeActionSummary>,
        existing: List<UserAllTimeActionSummary>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findByEntity(entity: String): UserAllTimeActionSummary? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE entity = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                entity,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findAllByEntityType(
        type: EntityType,
        pageable: Pageable,
    ): Slice<UserAllTimeActionSummary> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE entity_type = ? AND is_current = true
                ORDER BY total_reward_amount DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                type.name,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    @Cacheable(
        value = ["user_all_time_action_countByTotalRewardAmountGreaterThanAndEntityType"],
        key = "#totalRewardAmount + '-' + #entityType",
    )
    override fun countByTotalRewardAmountGreaterThanAndEntityType(
        totalRewardAmount: BigDecimal,
        entityType: EntityType,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE total_reward_amount > ? AND entity_type = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            totalRewardAmount,
            entityType.name,
        ) ?: 0L
    }

    @Cacheable(
        value = ["user_all_time_action_countByActionsRewardedGreaterThanAndEntityType"],
        key = "#actionsRewarded + '-' + #entityType",
    )
    override fun countByActionsRewardedGreaterThanAndEntityType(
        actionsRewarded: Long,
        entityType: EntityType,
    ): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE actions_rewarded > ? AND entity_type = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            actionsRewarded,
            entityType.name,
        ) ?: 0L
    }

    @Cacheable(value = ["user_all_time_action_countByEntityType"], key = "#entityType")
    override fun countByEntityType(entityType: EntityType): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM ${tableName()}
            WHERE entity_type = ? AND is_current = true
            """
                .trimIndent(),
            Long::class.java,
            entityType.name,
        ) ?: 0L
    }
}
