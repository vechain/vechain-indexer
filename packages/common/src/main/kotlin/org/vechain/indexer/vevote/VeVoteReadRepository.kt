package org.vechain.indexer.vevote

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes

/** The page behind `/vevote/proposals/comments`. */
@Repository
@ConditionalOnPostgres
open class VeVoteCommentReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun find(
        proposalId: String?,
        voter: String?,
        support: Support?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<VeVoteProposalComment> {
        val filters = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (proposalId != null) {
            filters += "proposal_id = ?"
            args += BigDecimal(proposalId)
        }
        if (voter != null) {
            filters += "voter = ?"
            args += bytes(voter)
        }
        if (support != null) {
            filters += "support = CAST(? AS vevote.support)"
            args += support.name
        }
        val where = if (filters.isEmpty()) "" else "WHERE " + filters.joinToString(" AND ")
        val order = direction.name
        return jdbc.query(
            "SELECT * FROM ${VeVoteCommentRowMapping.TABLE} $where " +
                "ORDER BY block_number $order, id $order OFFSET ? LIMIT ?",
            { rs, _ -> VeVoteCommentRowMapping.read(rs) },
            *(args + offset + limit).toTypedArray(),
        )
    }
}

/** The current tallies behind `/vevote/proposal/results`. */
@Repository
@ConditionalOnPostgres
open class VeVoteResultReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun find(
        proposalId: String?,
        support: Support?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<VeVoteProposalResult> {
        val filters = mutableListOf("superseded_at IS NULL")
        val args = mutableListOf<Any>()
        if (proposalId != null) {
            filters += "proposal_id = ?"
            args += BigDecimal(proposalId)
        }
        if (support != null) {
            filters += "support = CAST(? AS vevote.support)"
            args += support.name
        }
        val order = direction.name
        return jdbc.query(
            "SELECT * FROM ${VeVoteResultRowMapping.TABLE} WHERE " +
                filters.joinToString(" AND ") +
                " ORDER BY block_number $order, proposal_id $order, support $order " +
                "OFFSET ? LIMIT ?",
            { rs, _ -> VeVoteResultRowMapping.read(rs) },
            *(args + offset + limit).toTypedArray(),
        )
    }
}
