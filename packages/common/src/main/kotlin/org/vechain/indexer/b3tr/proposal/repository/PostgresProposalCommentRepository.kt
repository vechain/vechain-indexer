package org.vechain.indexer.b3tr.proposal.repository

import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.proposal.ProposalComment
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-comments")
@Repository
open class PostgresProposalCommentRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val namedJdbcTemplate: NamedParameterJdbcTemplate,
) : ProposalCommentRepository {

    private fun tableName(): String = "b3tr_proposal_comments"

    private fun mapRow(rs: ResultSet): ProposalComment =
        ProposalComment(
            id = rs.getString("id"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            voter = rs.getString("voter"),
            proposalId = rs.getString("proposal_id"),
            support = Support.valueOf(rs.getString("support")),
            weight = rs.getBigDecimal("weight").toBigInteger(),
            power = rs.getBigDecimal("power").toBigInteger(),
            reason = rs.getString("reason"),
        )

    @Transactional
    override fun saveAll(comments: List<ProposalComment>): List<ProposalComment> {
        if (comments.isEmpty()) {
            return comments
        }

        // Delete existing records with same IDs to handle re-processing
        val ids = comments.map { it.id }
        namedJdbcTemplate.update(
            "DELETE FROM ${tableName()} WHERE id IN (:ids)",
            mapOf("ids" to ids),
        )

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (
                id, block_id, block_number, block_timestamp,
                voter, proposal_id, support, weight, power, reason
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
                .trimIndent(),
            comments.map { comment ->
                arrayOf<Any?>(
                    comment.id,
                    comment.blockId,
                    comment.blockNumber,
                    comment.blockTimestamp,
                    comment.voter,
                    comment.proposalId,
                    comment.support.name,
                    comment.weight,
                    comment.power,
                    comment.reason,
                )
            },
        )

        return comments
    }

    override fun findAllByProposalId(
        proposalId: String,
        pageable: Pageable,
    ): Slice<ProposalComment> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE proposal_id = ?
                ORDER BY block_number DESC
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

    override fun findAllByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE proposal_id = ? AND support = ?
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

    override fun findAllByProposalIdAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<ProposalComment> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE proposal_id = ? AND voter = ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                proposalId,
                voter,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAllByProposalIdAndVoterAndSupport(
        proposalId: String,
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE proposal_id = ? AND voter = ? AND support = ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                proposalId,
                voter,
                support.name,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAllByVoter(voter: String, pageable: Pageable): Slice<ProposalComment> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE voter = ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                voter,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun findAllByVoterAndSupport(
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<ProposalComment> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE voter = ? AND support = ?
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                voter,
                support.name,
                limit,
                offset,
            )

        val hasNext = results.size > pageable.pageSize
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(content, pageable, hasNext)
    }

    override fun getLatestRecord(): ProposalComment? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
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

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id
                FROM ${tableName()}
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                BlockIdentifier(number = rs.getLong("block_number"), id = rs.getString("block_id"))
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    @Transactional
    override fun rollback(blockNumber: Long) {
        jdbcTemplate.update("DELETE FROM ${tableName()} WHERE block_number >= ?", blockNumber)
    }
}
