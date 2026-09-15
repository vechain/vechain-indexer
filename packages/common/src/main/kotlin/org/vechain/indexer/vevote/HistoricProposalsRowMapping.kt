package org.vechain.indexer.vevote

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull
import org.vechain.indexer.postgres.PostgresText

/**
 * A `vevote_historic.proposal` row, with its options and the votes cast, to a [HistoricProposals].
 */
object HistoricProposalRowMapping {
    const val TABLE = "vevote_historic.proposal"
    const val CHOICE_TABLE = "vevote_historic.proposal_choice"
    const val TALLY_TABLE = "vevote_historic.proposal_tally"
    const val DESCRIPTION_TABLE = "vevote_historic.description"

    /** The columns after `(contract, proposal_id)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_number",
            "block_id",
            "block_timestamp",
            "proposer",
            "title",
            "description",
            "proposal_type",
            "create_time",
            "voting_start_time",
            "voting_end_time",
            "test",
        )

    fun bind(ps: PreparedStatement, proposal: HistoricProposals) {
        ps.setBytes(1, bytes(proposal.contractAddress))
        ps.setBigDecimal(2, BigDecimal(proposal.proposalId))
        ps.setLong(3, proposal.blockNumber)
        ps.setBytes(4, bytes(proposal.blockId))
        ps.setLong(5, proposal.blockTimestamp)
        ps.setBytes(6, bytesOrNull(proposal.proposer))
        // A title is a chain string, which can carry the NUL Postgres rejects.
        ps.setString(7, proposal.title?.let(PostgresText::escape))
        ps.setString(8, proposal.description?.let(PostgresText::escape))
        ps.setObject(9, proposal.proposalType, Types.INTEGER)
        ps.setObject(10, proposal.createTime, Types.BIGINT)
        ps.setObject(11, proposal.votingStartTime, Types.BIGINT)
        ps.setObject(12, proposal.votingEndTime, Types.BIGINT)
        ps.setBoolean(13, proposal.test)
    }

    fun read(rs: ResultSet): HistoricProposals {
        val contract = hex(rs.getBytes("contract"))
        val proposalId = rs.getBigDecimal("proposal_id").toBigIntegerExact().toString()
        val labels = strings(rs, "labels")
        val voted = tallies(labels?.size, longs(rs, "vote_choices"), longs(rs, "vote_counts"))

        return HistoricProposals(
            id = "$contract-$proposalId",
            proposalId = proposalId,
            contractAddress = contract,
            createdDate = rs.getLong("block_timestamp").toString(),
            proposer = hexOrNull(rs.getBytes("proposer")),
            title = rs.getString("title")?.let(PostgresText::unescape),
            description =
                (rs.getString("ipfs_hash") ?: rs.getString("description"))?.let(
                    PostgresText::unescape
                ),
            proposalType = rs.getObject("proposal_type") as? Int,
            choices = labels,
            test = rs.getBoolean("test"),
            createTime = rs.getObject("create_time") as? Long,
            votingStartTime = rs.getObject("voting_start_time") as? Long,
            votingEndTime = rs.getObject("voting_end_time") as? Long,
            voteTallies = voted ?: longs(rs, "on_chain_tallies"),
            totalVotes =
                if (voted != null) rs.getLong("vote_total") else rs.getLong("on_chain_total"),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
    }

    /** One count per option, or null until a vote is cast; an option nobody picked counts zero. */
    private fun tallies(options: Int?, choices: List<Long>?, counts: List<Long>?): List<Long>? {
        if (choices.isNullOrEmpty() || counts == null) return null
        val byChoice = choices.map { it.toInt() }.zip(counts).toMap()
        return (1..(options ?: byChoice.keys.max())).map { byChoice[it] ?: 0L }
    }

    private fun strings(rs: ResultSet, column: String): List<String>? =
        (rs.getArray(column)?.array as? Array<*>)?.map {
            PostgresText.unescape(it as? String ?: "")
        }

    private fun longs(rs: ResultSet, column: String): List<Long>? =
        (rs.getArray(column)?.array as? Array<*>)?.map { (it as? Number)?.toLong() ?: 0L }
}

/** A [HistoricProposalsVote] to a `vevote_historic.vote` row. */
object HistoricVoteRowMapping {
    const val TABLE = "vevote_historic.vote"

    /** The columns after `(contract, proposal_id, voter)`, in the order [bind] sets them. */
    val COLUMNS = listOf("block_number", "block_id", "block_timestamp", "choices")

    fun bind(ps: PreparedStatement, vote: HistoricProposalsVote) {
        ps.setBytes(1, bytes(vote.contract))
        ps.setBigDecimal(2, BigDecimal(vote.proposalId))
        ps.setBytes(3, bytes(vote.voter))
        ps.setLong(4, vote.blockNumber)
        ps.setBytes(5, bytes(vote.blockId))
        ps.setLong(6, vote.blockTimestamp)
        ps.setArray(7, ps.connection.createArrayOf("INT", vote.choices.toTypedArray()))
    }
}
