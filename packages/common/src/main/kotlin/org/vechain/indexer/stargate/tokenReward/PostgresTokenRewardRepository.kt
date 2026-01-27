package org.vechain.indexer.stargate.tokenReward

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("stargate", "token-reward")
@Repository
open class PostgresTokenRewardRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<TokenReward>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    TokenRewardRepository {

    override fun tableName(): String = "stargate_token_rewards"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        token_id, cycle, validator, rewards, effective_stake, reward_period,
        day_of_month, week_of_year, month, year,
        day_reward, week_reward, month_reward, year_reward, cycle_reward
        """
            .trimIndent()

    override fun insertPlaceholders(): String =
        "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: TokenReward): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.tokenId,
            doc.cycle,
            doc.validator,
            doc.rewards,
            doc.effectiveStake,
            doc.rewardPeriod.name,
            doc.dayOfMonth,
            doc.weekOfYear,
            doc.month,
            doc.year,
            doc.dayReward,
            doc.weekReward,
            doc.monthReward,
            doc.yearReward,
            doc.cycleReward,
        )

    override fun mapRow(rs: ResultSet): TokenReward {
        return TokenReward(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            tokenId = rs.getString("token_id"),
            cycle = rs.getLong("cycle"),
            validator = rs.getString("validator"),
            rewards = rs.getBigDecimal("rewards").toBigInteger(),
            effectiveStake = rs.getBigDecimal("effective_stake")?.toBigInteger(),
            rewardPeriod = RewardPeriod.valueOf(rs.getString("reward_period")),
            dayOfMonth = rs.getLong("day_of_month"),
            weekOfYear = rs.getLong("week_of_year"),
            month = rs.getLong("month"),
            year = rs.getLong("year"),
            dayReward = rs.getBigDecimal("day_reward")?.toBigInteger(),
            weekReward = rs.getBigDecimal("week_reward")?.toBigInteger(),
            monthReward = rs.getBigDecimal("month_reward")?.toBigInteger(),
            yearReward = rs.getBigDecimal("year_reward")?.toBigInteger(),
            cycleReward = rs.getBigDecimal("cycle_reward")?.toBigInteger(),
        )
    }

    override fun saveAllVersioned(updated: List<TokenReward>, existing: List<TokenReward>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findById(id: String): TokenReward? {
        return findCurrentByEntityId(id)
    }

    override fun findAllById(ids: Collection<String>): List<TokenReward> {
        if (ids.isEmpty()) return emptyList()
        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE ${entityIdColumn()} IN (:ids) AND is_current = true
            """
                .trimIndent(),
            mapOf("ids" to ids),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun saveAll(rewards: List<TokenReward>) {
        if (rewards.isEmpty()) return
        saveAllVersioned(rewards, emptyList())
    }

    override fun findAllByValidatorAndRewardPeriodAndCycle(
        validator: String,
        rewardPeriod: RewardPeriod,
        cycle: Long,
    ): List<TokenReward> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE validator = ? AND reward_period = ? AND cycle = ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            validator,
            rewardPeriod.name,
            cycle,
        )
    }

    override fun findByTokenIdAndRewardPeriodInAndValidator(
        tokenId: String,
        rewardPeriod: List<RewardPeriod>,
        validator: String,
        pageable: Pageable,
    ): Slice<TokenReward> {
        if (rewardPeriod.isEmpty()) return SliceImpl(emptyList(), pageable, false)

        val limit = pageable.pageSize + 1
        val offset = pageable.offset
        val periodNames = rewardPeriod.map { it.name }

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE token_id = :tokenId 
                AND reward_period IN (:periods) 
                AND validator = :validator 
                AND is_current = true
                ORDER BY block_number DESC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                mapOf(
                    "tokenId" to tokenId,
                    "periods" to periodNames,
                    "validator" to validator,
                    "limit" to limit,
                    "offset" to offset,
                ),
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByTokenIdAndRewardPeriodIn(
        tokenId: String,
        rewardPeriod: List<RewardPeriod>,
        pageable: Pageable,
    ): Slice<TokenReward> {
        if (rewardPeriod.isEmpty()) return SliceImpl(emptyList(), pageable, false)

        val limit = pageable.pageSize + 1
        val offset = pageable.offset
        val periodNames = rewardPeriod.map { it.name }

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE token_id = :tokenId 
                AND reward_period IN (:periods) 
                AND is_current = true
                ORDER BY block_number DESC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                mapOf(
                    "tokenId" to tokenId,
                    "periods" to periodNames,
                    "limit" to limit,
                    "offset" to offset,
                ),
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }
}
