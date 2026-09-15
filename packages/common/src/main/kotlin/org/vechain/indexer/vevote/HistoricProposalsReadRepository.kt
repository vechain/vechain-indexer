package org.vechain.indexer.vevote

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes

/** The page behind `/vevote/historic-proposals`, whose tallies are counted from the votes. */
@Repository
@ConditionalOnPostgres
open class HistoricProposalsReadRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun find(
        proposalId: String?,
        contractAddress: String?,
        test: Boolean?,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<HistoricProposals> {
        val filters = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (proposalId != null) {
            val id = proposalId.toBigIntegerOrNull() ?: return emptyList()
            filters += "p.proposal_id = ?"
            args += BigDecimal(id)
        }
        if (contractAddress != null) {
            filters += "p.contract = ?"
            args += bytes(contractAddress)
        }
        if (test != null) {
            filters += "p.test = ?"
            args += test
        }
        val where = if (filters.isEmpty()) "" else "WHERE " + filters.joinToString(" AND ")
        val order = direction.name
        return jdbc.query(
            "$SELECT $where ORDER BY p.block_number $order, p.proposal_id $order, " +
                "p.contract $order OFFSET ? LIMIT ?",
            { rs, _ -> HistoricProposalRowMapping.read(rs) },
            *(args + offset + limit).toTypedArray(),
        )
    }

    companion object {
        /** The options, the tally the contract reported, and the votes counted per option. */
        private val SELECT =
            """
            SELECT p.*, d.ipfs_hash, c.labels, t.on_chain_tallies, t.on_chain_total,
                   v.vote_choices, v.vote_counts, v.vote_total
            FROM ${HistoricProposalRowMapping.TABLE} p
            LEFT JOIN LATERAL (
                SELECT de.ipfs_hash
                FROM ${HistoricProposalRowMapping.DESCRIPTION_TABLE} de
                WHERE de.contract = p.contract AND de.proposal_id = p.proposal_id
                ORDER BY de.block_number DESC LIMIT 1
            ) d ON TRUE
            LEFT JOIN LATERAL (
                SELECT array_agg(label ORDER BY position) AS labels
                FROM ${HistoricProposalRowMapping.CHOICE_TABLE} ch
                WHERE ch.contract = p.contract AND ch.proposal_id = p.proposal_id
            ) c ON TRUE
            LEFT JOIN LATERAL (
                SELECT array_agg(votes ORDER BY position) AS on_chain_tallies,
                       coalesce(sum(votes), 0) AS on_chain_total
                FROM ${HistoricProposalRowMapping.TALLY_TABLE} ta
                WHERE ta.contract = p.contract AND ta.proposal_id = p.proposal_id
            ) t ON TRUE
            LEFT JOIN LATERAL (
                SELECT array_agg(x.choice ORDER BY x.choice) AS vote_choices,
                       array_agg(x.cast_count ORDER BY x.choice) AS vote_counts,
                       coalesce(sum(x.cast_count), 0) AS vote_total
                FROM (
                    SELECT choice, count(*) AS cast_count
                    FROM ${HistoricVoteRowMapping.TABLE} vo, unnest(vo.choices) AS choice
                    WHERE vo.contract = p.contract AND vo.proposal_id = p.proposal_id
                    GROUP BY choice
                ) x
            ) v ON TRUE
            """
                .trimIndent()
    }
}
