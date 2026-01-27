package org.vechain.indexer.stargate.token

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
import org.vechain.indexer.validator.Status

@Profile("stargate", "stargate-token")
@Repository
open class PostgresStargateTokenRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<StargateToken>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    StargateTokenRepository {

    override fun tableName(): String = "stargate_tokens"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        token_id, level, owner, manager, delegation_status, validator_id,
        total_rewards_claimed, total_bootstrap_rewards_claimed, vet_staked,
        migrated, boosted, delegation_next_period, delegation_period_length, validator_exiting
        """
            .trimIndent()

    override fun insertPlaceholders(): String =
        "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: StargateToken): Array<Any?> =
        arrayOf(
            doc.tokenId,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.tokenId,
            doc.level.name,
            doc.owner,
            doc.manager,
            doc.delegationStatus.name,
            doc.validatorId,
            doc.totalRewardsClaimed,
            doc.totalBootstrapRewardsClaimed,
            doc.vetStaked,
            doc.migrated,
            doc.boosted,
            doc.delegationNextPeriod,
            doc.delegationPeriodLength,
            doc.validatorExiting,
        )

    override fun mapRow(rs: ResultSet): StargateToken {
        return StargateToken(
            tokenId = rs.getString("token_id"),
            level = TokenLevel.valueOf(rs.getString("level")),
            owner = rs.getString("owner"),
            manager = rs.getString("manager"),
            delegationStatus = Status.valueOf(rs.getString("delegation_status")),
            validatorId = rs.getString("validator_id"),
            totalRewardsClaimed = rs.getBigDecimal("total_rewards_claimed").toBigInteger(),
            totalBootstrapRewardsClaimed =
                rs.getBigDecimal("total_bootstrap_rewards_claimed").toBigInteger(),
            vetStaked = rs.getBigDecimal("vet_staked").toBigInteger(),
            migrated = rs.getBoolean("migrated"),
            boosted = rs.getBoolean("boosted"),
            blockNumber = rs.getLong("block_number"),
            blockId = rs.getString("block_id"),
            blockTimestamp = rs.getLong("block_timestamp"),
            version = rs.getInt("version"),
            delegationNextPeriod = rs.getLongOrNull("delegation_next_period"),
            delegationPeriodLength = rs.getLongOrNull("delegation_period_length"),
            validatorExiting = rs.getBoolean("validator_exiting").takeIf { !rs.wasNull() },
        )
    }

    override fun saveAllVersioned(updated: List<StargateToken>, existing: List<StargateToken>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findById(id: String): StargateToken? {
        return findCurrentByEntityId(id)
    }

    override fun findAllById(ids: Collection<String>): List<StargateToken> {
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

    override fun saveAll(tokens: Collection<StargateToken>) {
        if (tokens.isEmpty()) return
        // For saveAll without versioning context, treat all as new/updated
        saveAllVersioned(tokens.toList(), emptyList())
    }

    override fun findAll(pageable: Pageable): Slice<StargateToken> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE is_current = true
                ORDER BY token_id ASC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByOwner(owner: String, pageable: Pageable): Slice<StargateToken> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE owner = ? AND is_current = true
                ORDER BY token_id ASC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                owner,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByManager(manager: String, pageable: Pageable): Slice<StargateToken> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE manager = ? AND is_current = true
                ORDER BY token_id ASC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                manager,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByOwnerOrManager(
        owner: String,
        manager: String,
        pageable: Pageable,
    ): Slice<StargateToken> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE (owner = ? OR manager = ?) AND is_current = true
                ORDER BY token_id ASC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                owner,
                manager,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByValidatorIdIn(validatorIds: Set<String>): List<StargateToken> {
        if (validatorIds.isEmpty()) return emptyList()
        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE validator_id IN (:validatorIds) AND is_current = true
            """
                .trimIndent(),
            mapOf("validatorIds" to validatorIds),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findByDelegationNextPeriodAndDelegationStatusIn(
        blockNumbers: List<Long>,
        statuses: List<String>,
    ): List<StargateToken> {
        if (blockNumbers.isEmpty() || statuses.isEmpty()) return emptyList()
        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE delegation_next_period IN (:blockNumbers) 
            AND delegation_status IN (:statuses) 
            AND is_current = true
            """
                .trimIndent(),
            mapOf("blockNumbers" to blockNumbers, "statuses" to statuses),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findAllDistinctValidatorIds(): List<String?> {
        return jdbcTemplate.queryForList(
            """
            SELECT DISTINCT validator_id FROM ${tableName()}
            WHERE is_current = true
            """
                .trimIndent(),
            String::class.java,
        )
    }
}
