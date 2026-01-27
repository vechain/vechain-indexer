package org.vechain.indexer.vevote

import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("vevote", "vevote-comments")
@Repository
open class PostgresVevoteCommentRepository(private val jdbcTemplate: JdbcTemplate) :
    VevoteCommentRepository {

    private fun tableName(): String = "vevote_proposal_comments"

    private fun mapRow(rs: ResultSet): VeVoteProposalComment {
        return VeVoteProposalComment(
            id = rs.getString("id"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            voter = rs.getString("voter"),
            proposalId = rs.getString("proposal_id"),
            support = Support.valueOf(rs.getString("support")),
            weight = rs.getBigDecimal("weight").toBigInteger(),
            reason = rs.getString("reason"),
        )
    }

    private fun insertColumns(): String =
        """
        id, block_id, block_number, block_timestamp, voter, proposal_id, support, weight, reason
        """
            .trimIndent()

    private fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?"

    private fun insertParams(comment: VeVoteProposalComment): Array<Any?> =
        arrayOf(
            comment.id,
            comment.blockId,
            comment.blockNumber,
            comment.blockTimestamp,
            comment.voter,
            comment.proposalId,
            comment.support.name,
            java.math.BigDecimal(comment.weight),
            comment.reason,
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(comments: List<VeVoteProposalComment>) {
        if (comments.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (id) DO NOTHING
            """
                .trimIndent(),
            comments.map { insertParams(it) },
        )
    }

    override fun existsById(id: String): Boolean {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ${tableName()} WHERE id = ?",
                Int::class.java,
                id,
            )!! > 0
        } catch (_: EmptyResultDataAccessException) {
            false
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        jdbcTemplate.update("DELETE FROM ${tableName()} WHERE block_number >= ?", blockNumber)
    }

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id FROM ${tableName()}
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

    private fun queryWithPagination(
        whereClause: String,
        pageable: Pageable,
        vararg params: Any?,
    ): Slice<VeVoteProposalComment> {
        val limit = pageable.pageSize + 1
        val offset = pageable.offset

        val results =
            jdbcTemplate.query(
                """
                SELECT * FROM ${tableName()}
                WHERE $whereClause
                ORDER BY block_number DESC
                LIMIT ? OFFSET ?
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                *params,
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
    ): Slice<VeVoteProposalComment> {
        return queryWithPagination("proposal_id = ?", pageable, proposalId)
    }

    override fun findAllByVoter(voter: String, pageable: Pageable): Slice<VeVoteProposalComment> {
        return queryWithPagination("voter = ?", pageable, voter)
    }

    override fun findAllByProposalIdAndVoter(
        proposalId: String,
        voter: String,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> {
        return queryWithPagination("proposal_id = ? AND voter = ?", pageable, proposalId, voter)
    }

    override fun findAllBySupport(
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> {
        return queryWithPagination("support = ?", pageable, support.name)
    }

    override fun findAllByProposalIdAndSupport(
        proposalId: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> {
        return queryWithPagination(
            "proposal_id = ? AND support = ?",
            pageable,
            proposalId,
            support.name,
        )
    }

    override fun findAllByVoterAndSupport(
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> {
        return queryWithPagination("voter = ? AND support = ?", pageable, voter, support.name)
    }

    override fun findAllByProposalIdAndVoterAndSupport(
        proposalId: String,
        voter: String,
        support: Support,
        pageable: Pageable,
    ): Slice<VeVoteProposalComment> {
        return queryWithPagination(
            "proposal_id = ? AND voter = ? AND support = ?",
            pageable,
            proposalId,
            voter,
            support.name,
        )
    }
}
