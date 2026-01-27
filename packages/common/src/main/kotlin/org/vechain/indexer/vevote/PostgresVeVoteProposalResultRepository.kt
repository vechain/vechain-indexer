package org.vechain.indexer.vevote

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

@Profile("vevote", "vevote-results")
@Repository
open class PostgresVeVoteProposalResultRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<VeVoteProposalResult>(
        jdbcTemplate,
        namedJdbcTemplate,
        objectMapper,
    ),
    VeVoteProposalResultRepository {

    override fun tableName(): String = "vevote_proposal_results"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        proposal_id, support, total_weight, total_voters
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    override fun insertParams(doc: VeVoteProposalResult): Array<Any?> =
        arrayOf(
            doc.id,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.proposalId,
            doc.support.name,
            doc.totalWeight,
            doc.totalVoters,
        )

    override fun mapRow(rs: ResultSet): VeVoteProposalResult {
        return VeVoteProposalResult(
            id = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            proposalId = rs.getString("proposal_id"),
            support = Support.valueOf(rs.getString("support")),
            totalWeight = rs.getBigDecimal("total_weight"),
            totalVoters = rs.getInt("total_voters"),
        )
    }

    override fun saveAllVersioned(
        updated: List<VeVoteProposalResult>,
        existing: List<VeVoteProposalResult>,
    ) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findById(id: String): VeVoteProposalResult? {
        return findCurrentByEntityId(id)
    }

    override fun findByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalResult> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE proposal_id = ? AND support = ? AND is_current = true
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                proposalId,
                support.name,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAllByProposalId(
        proposalId: String,
        pageable: Pageable,
    ): Slice<VeVoteProposalResult> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE proposal_id = ? AND is_current = true
                ORDER BY support ASC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                proposalId,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAllBySupport(
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalResult> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE support = ? AND is_current = true
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                support.name,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }
}
