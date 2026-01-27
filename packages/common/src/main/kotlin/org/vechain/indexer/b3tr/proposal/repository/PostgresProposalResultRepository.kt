package org.vechain.indexer.b3tr.proposal.repository

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.proposal.ProposalResult
import org.vechain.indexer.b3tr.proposal.ProposalState
import org.vechain.indexer.b3tr.proposal.VoteResults
import org.vechain.indexer.postgres.PostgresVersionedRepository

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
@Repository
open class PostgresProposalResultRepository(
    jdbcTemplate: JdbcTemplate,
    namedJdbcTemplate: NamedParameterJdbcTemplate,
    objectMapper: ObjectMapper,
) :
    PostgresVersionedRepository<ProposalResult>(jdbcTemplate, namedJdbcTemplate, objectMapper),
    ProposalResultRepository {

    override fun tableName(): String = "b3tr_proposal_results"

    override fun entityIdColumn(): String = "entity_id"

    override fun insertColumns(): String =
        """
        entity_id, version, is_current, block_id, block_number, block_timestamp,
        created_at_block_number, start_round_id, state, results, description
        """
            .trimIndent()

    override fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?"

    override fun insertParams(doc: ProposalResult): Array<Any?> =
        arrayOf(
            doc.proposalId,
            doc.version,
            true, // is_current
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            doc.createdAtBlockNumber,
            doc.startRoundId,
            doc.state.name,
            doc.results?.let { objectMapper.writeValueAsString(it) },
            doc.description,
        )

    override fun mapRow(rs: ResultSet): ProposalResult {
        val resultsJson = rs.getString("results")
        val results = resultsJson?.let { objectMapper.readValue(it, VoteResults::class.java) }

        return ProposalResult(
            proposalId = rs.getString("entity_id"),
            version = rs.getInt("version"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            createdAtBlockNumber = rs.getLong("created_at_block_number"),
            startRoundId = rs.getInt("start_round_id"),
            state = ProposalState.valueOf(rs.getString("state")),
            results = results,
            description = rs.getString("description"),
        )
    }

    override fun saveAllVersioned(updated: List<ProposalResult>, existing: List<ProposalResult>) {
        super.saveAllVersioned(updated, existing)
    }

    override fun findById(proposalId: String): ProposalResult? {
        return findCurrentByEntityId(proposalId)
    }

    override fun findAll(pageable: Pageable): Slice<ProposalResult> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE is_current = true
                ORDER BY created_at_block_number DESC
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

    override fun findByStateIn(states: List<ProposalState>): List<ProposalResult> {
        if (states.isEmpty()) {
            return emptyList()
        }

        val stateNames = states.map { it.name }

        return namedJdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE state IN (:states) AND is_current = true
            """
                .trimIndent(),
            mapOf("states" to stateNames),
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findByStateIn(
        states: List<ProposalState>,
        pageable: Pageable,
    ): Slice<ProposalResult> {
        if (states.isEmpty()) {
            return SliceImpl(emptyList(), pageable, false)
        }

        val limit = pageable.pageSize + 1
        val offset = pageable.offset
        val stateNames = states.map { it.name }

        val results =
            namedJdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE state IN (:states) AND is_current = true
                ORDER BY created_at_block_number DESC
                LIMIT :limit OFFSET :offset
                """
                    .trimIndent(),
                mapOf("states" to stateNames, "limit" to limit, "offset" to offset),
            ) { rs, _ ->
                mapRow(rs)
            }

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun getLatestRecord(): ProposalResult? {
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
