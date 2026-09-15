package org.vechain.indexer.vevote

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bareHex
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresText

/** A `vevote.comment` row to a [VeVoteProposalComment] and back. */
object VeVoteCommentRowMapping {
    const val TABLE = "vevote.comment"

    /** The columns after `id`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_number",
            "block_id",
            "block_timestamp",
            "voter",
            "proposal_id",
            "support",
            "weight",
            "reason",
        )

    fun bind(ps: PreparedStatement, comment: VeVoteProposalComment) {
        ps.setBytes(1, bytes(comment.id))
        ps.setLong(2, comment.blockNumber)
        ps.setBytes(3, bytes(comment.blockId))
        ps.setLong(4, comment.blockTimestamp)
        ps.setBytes(5, bytes(comment.voter))
        ps.setBigDecimal(6, BigDecimal(comment.proposalId))
        ps.setString(7, comment.support.name)
        ps.setBigDecimal(8, BigDecimal(comment.weight))
        // A voter's own text, so it can carry the NUL Postgres rejects.
        ps.setString(9, PostgresText.escape(comment.reason))
    }

    fun read(rs: ResultSet): VeVoteProposalComment =
        VeVoteProposalComment(
            id = bareHex(rs.getBytes("id")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            voter = hex(rs.getBytes("voter")),
            proposalId = rs.getBigDecimal("proposal_id").toBigIntegerExact().toString(),
            support = Support.valueOf(rs.getString("support")),
            weight = rs.getBigDecimal("weight").toBigIntegerExact(),
            reason = PostgresText.unescape(rs.getString("reason")),
        )
}

/** A `vevote.result` row to a [VeVoteProposalResult] and back. */
object VeVoteResultRowMapping {
    const val TABLE = "vevote.result"

    /** The columns after `(proposal_id, support, block_number)`, in the order [bind] sets them. */
    val COLUMNS = listOf("block_id", "block_timestamp", "total_weight", "total_voters")

    fun bind(ps: PreparedStatement, result: VeVoteProposalResult) {
        ps.setBigDecimal(1, BigDecimal(result.proposalId))
        ps.setString(2, result.support.name)
        ps.setLong(3, result.blockNumber)
        ps.setBytes(4, bytes(result.blockId))
        ps.setLong(5, result.blockTimestamp)
        ps.setBigDecimal(6, result.totalWeight)
        ps.setInt(7, result.totalVoters)
    }

    fun read(rs: ResultSet): VeVoteProposalResult =
        VeVoteProposalResult(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            proposalId = rs.getBigDecimal("proposal_id").toBigIntegerExact().toString(),
            support = Support.valueOf(rs.getString("support")),
            totalWeight = rs.getBigDecimal("total_weight"),
            totalVoters = rs.getInt("total_voters"),
        )
}
