package org.vechain.indexer.vevote

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.postgres.PostgresText

/** The `vevote_historic` schema: the proposals, the options they offered and the votes cast. */
@Repository
@ConditionalOnPostgres
open class HistoricProposalsWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Everything one entry adds, written together so a rollback cannot split them. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(
        proposals: List<HistoricProposals>,
        descriptions: List<HistoricProposalDescription>,
        votes: List<HistoricProposalsVote>,
    ) {
        saveProposals(proposals)
        saveDescriptions(descriptions)
        saveVotes(votes)
    }

    private fun saveProposals(proposals: List<HistoricProposals>) {
        if (proposals.isEmpty()) return
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = proposals.associateBy { it.contractAddress to it.proposalId }.values.toList()
        jdbc.batchUpdate(PROPOSAL_INSERT, rows, rows.size) { ps, p ->
            HistoricProposalRowMapping.bind(ps, p)
        }
        rows.forEach { proposal ->
            val key = arrayOf<Any>(bytes(proposal.contractAddress), BigDecimal(proposal.proposalId))
            // Re-indexing a proposal replaces the options and the tally it was created with.
            jdbc.update("DELETE FROM $CHOICE_TABLE WHERE contract = ? AND proposal_id = ?", *key)
            jdbc.update("DELETE FROM $TALLY_TABLE WHERE contract = ? AND proposal_id = ?", *key)
            proposal.choices?.forEachIndexed { index, label ->
                jdbc.update(
                    "INSERT INTO $CHOICE_TABLE (contract, proposal_id, position, label) " +
                        "VALUES (?, ?, ?, ?)",
                    *key,
                    index + 1,
                    PostgresText.escape(label),
                )
            }
            proposal.voteTallies?.forEachIndexed { index, votes ->
                jdbc.update(
                    "INSERT INTO $TALLY_TABLE (contract, proposal_id, position, votes) " +
                        "VALUES (?, ?, ?, ?)",
                    *key,
                    index + 1,
                    votes,
                )
            }
        }
    }

    // A description for a proposal this indexer never saw is dropped, as the Mongo lookup did.
    private fun saveDescriptions(descriptions: List<HistoricProposalDescription>) {
        descriptions.forEach {
            val contract = bytes(it.contractAddress)
            val proposalId = BigDecimal(it.proposalId)
            jdbc.update(
                DESCRIPTION_INSERT,
                contract,
                proposalId,
                it.blockNumber,
                PostgresText.escape(it.description),
                contract,
                proposalId,
            )
        }
    }

    private fun saveVotes(votes: List<HistoricProposalsVote>) {
        if (votes.isEmpty()) return
        val rows =
            votes.associateBy { Triple(it.contract, it.proposalId, it.voter) }.values.toList()
        jdbc.batchUpdate(VOTE_INSERT, rows, rows.size) { ps, v ->
            HistoricVoteRowMapping.bind(ps, v)
        }
    }

    override fun rollbackFrom(blockNumber: Long) {
        // The options and the tally hang off the proposal, so its delete cascades to them.
        jdbc.update("DELETE FROM $TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update("DELETE FROM $DESCRIPTION_TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "DELETE FROM ${HistoricVoteRowMapping.TABLE} WHERE block_number >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute(
            "TRUNCATE $TABLE, $CHOICE_TABLE, $TALLY_TABLE, $DESCRIPTION_TABLE, " +
                HistoricVoteRowMapping.TABLE
        )
    }

    companion object {
        private const val TABLE = HistoricProposalRowMapping.TABLE
        private const val CHOICE_TABLE = HistoricProposalRowMapping.CHOICE_TABLE
        private const val TALLY_TABLE = HistoricProposalRowMapping.TALLY_TABLE
        private const val DESCRIPTION_TABLE = HistoricProposalRowMapping.DESCRIPTION_TABLE

        private val DESCRIPTION_INSERT =
            "INSERT INTO $DESCRIPTION_TABLE (contract, proposal_id, block_number, ipfs_hash) " +
                "SELECT ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM $TABLE p " +
                "WHERE p.contract = ? AND p.proposal_id = ?) " +
                "ON CONFLICT (contract, proposal_id, block_number) DO UPDATE " +
                "SET ipfs_hash = EXCLUDED.ipfs_hash"

        private val PROPOSAL_INSERT =
            "INSERT INTO $TABLE (contract, proposal_id, " +
                HistoricProposalRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                HistoricProposalRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (contract, proposal_id) DO UPDATE SET " +
                HistoricProposalRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }

        private val VOTE_INSERT =
            "INSERT INTO ${HistoricVoteRowMapping.TABLE} (contract, proposal_id, voter, " +
                HistoricVoteRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, ?, " +
                HistoricVoteRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (contract, proposal_id, voter) DO UPDATE SET " +
                HistoricVoteRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
