package org.vechain.indexer.vevote

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables

/** The `vevote` schema: the comments a block cast and the results they moved. */
@Repository
@ConditionalOnPostgres
open class VeVoteWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Everything one entry adds, written together so a rollback cannot split them. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(comments: List<VeVoteProposalComment>, results: List<VeVoteProposalResult>) {
        saveComments(comments)
        results.groupBy { it.blockNumber }.toSortedMap().forEach { (_, block) -> saveBlock(block) }
    }

    // The id is sha1(proposal, reason), so a duplicate comment keeps the first one cast.
    private fun saveComments(comments: List<VeVoteProposalComment>) {
        if (comments.isEmpty()) return
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = comments.distinctBy { it.id }
        jdbc.batchUpdate(COMMENT_INSERT, rows, rows.size) { ps, c ->
            VeVoteCommentRowMapping.bind(ps, c)
        }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveBlock(block: List<VeVoteProposalResult>) {
        val rows = block.associateBy { it.proposalId to it.support }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $RESULT_TABLE SET superseded_at = ? WHERE proposal_id = ? AND support = " +
                "CAST(? AS vevote.support) AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, r ->
            ps.setLong(1, r.blockNumber)
            ps.setBigDecimal(2, BigDecimal(r.proposalId))
            ps.setString(3, r.support.name)
            ps.setLong(4, r.blockNumber)
        }
        jdbc.batchUpdate(RESULT_INSERT, rows, rows.size) { ps, r ->
            VeVoteResultRowMapping.bind(ps, r)
        }
    }

    /** The current row of every support of [proposalIds], which the next vote adds to. */
    open fun findCurrentResults(proposalIds: Set<String>): List<VeVoteProposalResult> =
        if (proposalIds.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $RESULT_TABLE WHERE proposal_id = ANY(?) AND superseded_at IS NULL",
                { rs, _ -> VeVoteResultRowMapping.read(rs) },
                proposalIds.map(::BigDecimal).toTypedArray(),
            )

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update(
            "DELETE FROM ${VeVoteCommentRowMapping.TABLE} WHERE block_number >= ?",
            blockNumber,
        )
        jdbc.update("DELETE FROM $RESULT_TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "UPDATE $RESULT_TABLE SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE ${VeVoteCommentRowMapping.TABLE}, $RESULT_TABLE")
    }

    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM $RESULT_TABLE WHERE superseded_at < ?", before)

    companion object {
        private const val RESULT_TABLE = VeVoteResultRowMapping.TABLE

        private val COMMENT_INSERT =
            "INSERT INTO ${VeVoteCommentRowMapping.TABLE} (id, " +
                VeVoteCommentRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, " +
                VeVoteCommentRowMapping.COLUMNS.joinToString {
                    if (it == "support") "CAST(? AS vevote.support)" else "?"
                } +
                ") ON CONFLICT (id) DO NOTHING"

        private val RESULT_INSERT =
            "INSERT INTO $RESULT_TABLE (proposal_id, support, block_number, " +
                VeVoteResultRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, CAST(? AS vevote.support), ?, " +
                VeVoteResultRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (proposal_id, support, block_number) DO UPDATE SET " +
                VeVoteResultRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
