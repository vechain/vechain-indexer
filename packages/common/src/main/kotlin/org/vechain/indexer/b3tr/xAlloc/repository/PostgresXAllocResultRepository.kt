package org.vechain.indexer.b3tr.xAlloc.repository

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.xAlloc.XAllocResult
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("b3tr", "b3tr-x-alloc")
@Repository
open class PostgresXAllocResultRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<XAllocResult>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    XAllocResultRepository {

    override fun tableName(): String = "b3tr_x_alloc_results"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        round_id, app_id, voters, votes_received, total_amount, unallocated_amount,
        team_allocation_amount, rewards_allocation_amount
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: XAllocResult): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.roundId,
            doc.appId,
            doc.voters,
            doc.votesReceived,
            doc.totalAmount,
            doc.unallocatedAmount,
            doc.teamAllocationAmount,
            doc.rewardsAllocationAmount,
        )

    override fun mapRow(rs: ResultSet): XAllocResult =
        XAllocResult(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            roundId = rs.getInt("round_id"),
            appId = rs.getString("app_id"),
            voters = rs.getLong("voters"),
            votesReceived = rs.getBigDecimal("votes_received").toBigInteger(),
            totalAmount = rs.getBigDecimal("total_amount"),
            unallocatedAmount = rs.getBigDecimal("unallocated_amount"),
            teamAllocationAmount = rs.getBigDecimal("team_allocation_amount"),
            rewardsAllocationAmount = rs.getBigDecimal("rewards_allocation_amount"),
        )

    override fun saveAllVersioned(updated: List<XAllocResult>, existing: List<XAllocResult>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findByRoundId(roundId: Int): List<XAllocResult> =
        jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE round_id = ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            roundId,
        )

    override fun findByAppId(appId: String): List<XAllocResult> =
        jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE app_id = ? AND is_current = true
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            appId,
        )

    override fun findByAppIdAndRoundId(appId: String, roundId: Int): XAllocResult? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE app_id = ? AND round_id = ? AND is_current = true
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                appId,
                roundId,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findById(id: String): XAllocResult? = findCurrentByEntityId(id)

    override fun getLatestRecord(): XAllocResult? {
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
}
