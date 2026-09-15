package org.vechain.indexer.b3tr.proposal

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes

/** The current proposal results behind the `/b3tr/proposals` endpoints. */
@Repository
@ConditionalOnPostgres
open class ProposalResultReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun findByProposalId(proposalId: String): ProposalResult? {
        val id = proposalId.toBigIntegerOrNull() ?: return null
        return query("$CURRENT AND proposal_id = ?", BigDecimal(id)).firstOrNull()
    }

    /** Every proposal, newest first, optionally narrowed to a set of states. */
    open fun find(
        states: List<ProposalState>,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<ProposalResult> {
        val order = direction.name
        val filter =
            if (states.isEmpty()) "" else "AND state = ANY(CAST(? AS b3tr_proposal.state[]))"
        val args =
            if (states.isEmpty()) emptyList<Any>()
            else listOf<Any>(states.joinToString(",", "{", "}") { it.name })
        return query(
            "$CURRENT $filter ORDER BY created_at_block_number $order, proposal_id $order " +
                "OFFSET ? LIMIT ?",
            *(args + offset + limit).toTypedArray(),
        )
    }

    open fun latestBlockNumber(): Long =
        jdbc.queryForObject(
            "SELECT coalesce(max(block_number), 0) FROM ${ProposalResultRowMapping.TABLE}",
            Long::class.java,
        )!!

    private fun query(sql: String, vararg args: Any): List<ProposalResult> =
        jdbc.query(sql, { rs, _ -> ProposalResultRowMapping.read(rs) }, *args)

    companion object {
        private const val CURRENT =
            "SELECT * FROM ${ProposalResultRowMapping.TABLE} WHERE superseded_at IS NULL"
    }
}

/** The page behind the `/b3tr/proposals/.../comments` endpoints. */
@Repository
@ConditionalOnPostgres
open class ProposalCommentReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun find(
        proposalId: String?,
        voter: String?,
        support: Support?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<ProposalComment> {
        val filters = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (proposalId != null) {
            val id = proposalId.toBigIntegerOrNull() ?: return emptyList()
            filters += "proposal_id = ?"
            args += BigDecimal(id)
        }
        if (voter != null) {
            filters += "voter = ?"
            args += bytes(voter)
        }
        if (support != null) {
            filters += "support = CAST(? AS b3tr_proposal.support)"
            args += support.name
        }
        val where = if (filters.isEmpty()) "" else "WHERE " + filters.joinToString(" AND ")
        val order = direction.name
        return jdbc.query(
            "SELECT * FROM ${ProposalCommentRowMapping.TABLE} $where " +
                "ORDER BY block_number $order, proposal_id $order, voter $order OFFSET ? LIMIT ?",
            { rs, _ -> ProposalCommentRowMapping.read(rs) },
            *(args + offset + limit).toTypedArray(),
        )
    }

    open fun latestBlockNumber(): Long =
        jdbc.queryForObject(
            "SELECT coalesce(max(block_number), 0) FROM ${ProposalCommentRowMapping.TABLE}",
            Long::class.java,
        )!!
}
