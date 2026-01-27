package org.vechain.indexer.validator

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
import org.vechain.indexer.stargate.token.TokenLevel

@Profile("validator", "delegation", "stargate", "vet-delegated-by-block")
@Repository
open class PostgresDelegationRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<Delegation>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    DelegationRepository {

    override fun tableName(): String = "delegations"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        validator, token_id, owner, status, token_level, staked_amount, total_rewards_claimed,
        notify, tx_id, validator_next_cycle, validator_cycle_length, force
        """
            .trimIndent()

    override fun insertPlaceholders(): String =
        "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: Delegation): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.validator,
            doc.tokenId,
            doc.owner,
            doc.status.name,
            doc.tokenLevel.name,
            doc.stakedAmount,
            java.math.BigDecimal(doc.totalRewardsClaimed),
            doc.notify,
            doc.txId,
            doc.validatorNextCycle,
            doc.validatorCycleLength,
            doc.force,
        )

    override fun mapRow(rs: ResultSet): Delegation {
        return Delegation(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            validator = rs.getString("validator"),
            tokenId = rs.getString("token_id"),
            owner = rs.getString("owner"),
            status = Status.valueOf(rs.getString("status")),
            tokenLevel = TokenLevel.valueOf(rs.getString("token_level")),
            stakedAmount = rs.getString("staked_amount"),
            totalRewardsClaimed = rs.getBigDecimal("total_rewards_claimed").toBigInteger(),
            notify = rs.getBoolean("notify"),
            txId = rs.getString("tx_id"),
            validatorNextCycle = rs.getLong("validator_next_cycle"),
            validatorCycleLength = rs.getLong("validator_cycle_length"),
            force = rs.getBoolean("force"),
        )
    }

    override fun saveAllVersioned(updated: List<Delegation>, existing: List<Delegation>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun saveAll(delegations: List<Delegation>) {
        if (delegations.isEmpty()) return

        val entityIds = delegations.map { it.id }

        // Mark existing current versions as non-current
        if (entityIds.isNotEmpty()) {
            namedJdbcTemplate.update(
                """
                UPDATE ${tableName()}
                SET is_current = false
                WHERE ${entityIdColumn()} IN (:entityIds) AND is_current = true
                """
                    .trimIndent(),
                mapOf("entityIds" to entityIds),
            )
        }

        // Insert new versions
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (${entityIdColumn()}, version) DO UPDATE SET is_current = true
            """
                .trimIndent(),
            delegations.map { insertParams(it) },
        )
    }

    override fun findByNotify(notify: Boolean): List<Delegation> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE notify = ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            notify,
        )
    }

    override fun findByValidatorNextCycleInAndStatusIn(
        blockNumber: List<Long>,
        statuses: List<Status>,
    ): List<Delegation> {
        if (blockNumber.isEmpty() || statuses.isEmpty()) return emptyList()

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE validator_next_cycle IN (:blockNumbers)
              AND status IN (:statuses)
              AND is_current = true
            """
                .trimIndent(),
            mapOf("blockNumbers" to blockNumber, "statuses" to statuses.map { it.name }),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findByValidatorIn(validators: List<String>): List<Delegation> {
        if (validators.isEmpty()) return emptyList()

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE validator IN (:validators) AND is_current = true
            """
                .trimIndent(),
            mapOf("validators" to validators),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findValidatorIdsByStatusNot(status: Status): List<String> {
        return jdbcTemplate.queryForList(
            """
            SELECT DISTINCT validator FROM ${tableName()}
            WHERE status != ? AND is_current = true
            """
                .trimIndent(),
            String::class.java,
            status.name,
        )
    }

    override fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<Status>,
        pageable: Pageable,
    ): Slice<Delegation> {
        if (statuses.isEmpty()) return SliceImpl(emptyList(), pageable, false)

        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE validator = :validator AND status IN (:statuses) AND is_current = true
                ORDER BY entity_id ASC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                mapOf(
                    "validator" to validator,
                    "statuses" to statuses.map { it.name },
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

    override fun findByValidatorAndStatusIn(
        validator: String,
        statuses: List<Status>,
    ): List<Delegation> {
        if (statuses.isEmpty()) return emptyList()

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE validator = :validator AND status IN (:statuses) AND is_current = true
            """
                .trimIndent(),
            mapOf("validator" to validator, "statuses" to statuses.map { it.name }),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findByValidator(validator: String, pageable: Pageable): Slice<Delegation> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE validator = ? AND is_current = true
                ORDER BY entity_id ASC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                validator,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByTokenId(tokenId: String, pageable: Pageable): Slice<Delegation> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE token_id = ? AND is_current = true
                ORDER BY entity_id ASC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                tokenId,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findByTokenIdIn(tokenIds: List<String>): List<Delegation> {
        if (tokenIds.isEmpty()) return emptyList()

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE token_id IN (:tokenIds) AND is_current = true
            """
                .trimIndent(),
            mapOf("tokenIds" to tokenIds),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findByStatusIn(
        statuses: Collection<Status>,
        pageable: Pageable,
    ): Slice<Delegation> {
        if (statuses.isEmpty()) return SliceImpl(emptyList(), pageable, false)

        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE status IN (:statuses) AND is_current = true
                ORDER BY entity_id ASC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                mapOf("statuses" to statuses.map { it.name }, "limit" to limit, "offset" to offset),
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAll(pageable: Pageable): Slice<Delegation> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE is_current = true
                ORDER BY entity_id ASC
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

    override fun aggregateDelegationCountsByValidator(): List<DelegationCountAggregateResult> {
        return jdbcTemplate
            .query(
                """
            SELECT validator, status, COUNT(*) as count
            FROM ${tableName()}
            WHERE status IN ('QUEUED', 'ACTIVE', 'EXITING') AND is_current = true
            GROUP BY validator, status
            ORDER BY validator
            """
                    .trimIndent()
            ) { rs, _ ->
                Triple(rs.getString("validator"), rs.getString("status"), rs.getLong("count"))
            }
            .groupBy { it.first }
            .map { (validatorId, rows) ->
                DelegationCountAggregateResult(
                    _id = validatorId,
                    counts = rows.map { DelegationStatusCount(it.second, it.third) },
                )
            }
    }

    override fun aggregateDelegationCountsByValidator(
        validator: String
    ): List<DelegationCountAggregateResult> {
        return jdbcTemplate
            .query(
                """
            SELECT validator, status, COUNT(*) as count
            FROM ${tableName()}
            WHERE validator = ? AND status IN ('QUEUED', 'ACTIVE', 'EXITING') AND is_current = true
            GROUP BY validator, status
            """
                    .trimIndent(),
                { rs, _ ->
                    Triple(rs.getString("validator"), rs.getString("status"), rs.getLong("count"))
                },
                validator,
            )
            .groupBy { it.first }
            .map { (validatorId, rows) ->
                DelegationCountAggregateResult(
                    _id = validatorId,
                    counts = rows.map { DelegationStatusCount(it.second, it.third) },
                )
            }
    }

    override fun aggregateActiveDelegationsByLevel(): List<DelegationLevelAggregateResult> {
        return jdbcTemplate.query(
            """
            SELECT token_level as level,
                   SUM(CAST(staked_amount AS NUMERIC)) as total_wei,
                   COUNT(*) as nft_count
            FROM ${tableName()}
            WHERE status IN ('ACTIVE', 'EXITING') AND is_current = true
            GROUP BY token_level
            """
                .trimIndent()
        ) { rs, _ ->
            DelegationLevelAggregateResult(
                level = rs.getString("level"),
                totalWei = rs.getBigDecimal("total_wei").toBigInteger().toString(),
                nftCount = rs.getLong("nft_count"),
            )
        }
    }
}
