package org.vechain.indexer.b3tr.proposal

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables

/** The `b3tr_proposal` schema: a proposal's state through time and the comments cast on it. */
@Repository
@ConditionalOnPostgres
open class ProposalWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Everything one entry adds, written together so a rollback cannot split them. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(results: List<ProposalResult>, comments: List<ProposalComment>) {
        results.groupBy { it.blockNumber }.toSortedMap().forEach(::saveBlock)
        saveComments(comments)
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveBlock(blockNumber: Long, block: List<ProposalResult>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = block.associateBy { it.proposalId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TABLE SET superseded_at = ? WHERE proposal_id = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, r ->
            ps.setLong(1, blockNumber)
            ps.setBigDecimal(2, BigDecimal(r.proposalId))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, r -> ProposalResultRowMapping.bind(ps, r) }
    }

    private fun saveComments(comments: List<ProposalComment>) {
        if (comments.isEmpty()) return
        val rows = comments.associateBy { it.proposalId to it.voter }.values.toList()
        jdbc.batchUpdate(COMMENT_INSERT, rows, rows.size) { ps, c ->
            ProposalCommentRowMapping.bind(ps, c)
        }
    }

    /** The current row of every proposal the batch touches, which its votes add to. */
    open fun findCurrent(proposalIds: Set<String>): List<ProposalResult> =
        if (proposalIds.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $TABLE WHERE proposal_id = ANY(?) AND superseded_at IS NULL",
                { rs, _ -> ProposalResultRowMapping.read(rs) },
                proposalIds.map(::BigDecimal).toTypedArray(),
            )

    /** The proposals whose state the governor can still move, refreshed at the chain head. */
    open fun findCurrentByStates(states: List<ProposalState>): List<ProposalResult> =
        jdbc.query(
            "SELECT * FROM $TABLE WHERE state = ANY(CAST(? AS b3tr_proposal.state[])) " +
                "AND superseded_at IS NULL",
            { rs, _ -> ProposalResultRowMapping.read(rs) },
            states.joinToString(",", "{", "}") { it.name },
        )

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM $TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update("UPDATE $TABLE SET superseded_at = NULL WHERE superseded_at >= ?", blockNumber)
        jdbc.update("DELETE FROM $COMMENT_TABLE WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE $TABLE, $COMMENT_TABLE")
    }

    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM $TABLE WHERE superseded_at < ?", before)

    companion object {
        private const val TABLE = ProposalResultRowMapping.TABLE
        private const val COMMENT_TABLE = ProposalCommentRowMapping.TABLE

        private val INSERT =
            "INSERT INTO $TABLE (proposal_id, block_number, " +
                ProposalResultRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                ProposalResultRowMapping.COLUMNS.joinToString {
                    if (it == "state") "CAST(? AS b3tr_proposal.state)" else "?"
                } +
                ") ON CONFLICT (proposal_id, block_number) DO UPDATE SET " +
                ProposalResultRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }

        private val COMMENT_INSERT =
            "INSERT INTO $COMMENT_TABLE (proposal_id, voter, " +
                ProposalCommentRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                ProposalCommentRowMapping.COLUMNS.joinToString {
                    if (it == "support") "CAST(? AS b3tr_proposal.support)" else "?"
                } +
                ") ON CONFLICT (proposal_id, voter) DO UPDATE SET " +
                ProposalCommentRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
