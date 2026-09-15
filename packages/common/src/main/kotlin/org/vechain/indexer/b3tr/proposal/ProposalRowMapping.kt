package org.vechain.indexer.b3tr.proposal

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresText

/** A `b3tr_proposal.result` row to a [ProposalResult] and back. */
object ProposalResultRowMapping {
    const val TABLE = "b3tr_proposal.result"

    /** The columns after `(proposal_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "created_at_block_number",
            "start_round_id",
            "state",
            "description",
            "for_voters",
            "for_weight",
            "for_power",
            "against_voters",
            "against_weight",
            "against_power",
            "abstain_voters",
            "abstain_weight",
            "abstain_power",
        )

    fun bind(ps: PreparedStatement, r: ProposalResult) {
        ps.setBigDecimal(1, BigDecimal(r.proposalId))
        ps.setLong(2, r.blockNumber)
        ps.setBytes(3, bytes(r.blockId))
        ps.setLong(4, r.blockTimestamp)
        ps.setLong(5, r.createdAtBlockNumber)
        ps.setInt(6, r.startRoundId)
        ps.setString(7, r.state.name)
        // A description is a chain string, which can carry the NUL Postgres rejects.
        ps.setString(8, PostgresText.escape(r.description))
        bindResult(ps, 9, r.results?.forResult)
        bindResult(ps, 12, r.results?.againstResult)
        bindResult(ps, 15, r.results?.abstainResult)
    }

    fun read(rs: ResultSet): ProposalResult =
        ProposalResult(
            proposalId = rs.getBigDecimal("proposal_id").toBigIntegerExact().toString(),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            createdAtBlockNumber = rs.getLong("created_at_block_number"),
            startRoundId = rs.getInt("start_round_id"),
            state = ProposalState.valueOf(rs.getString("state")),
            results = readResults(rs),
            description = PostgresText.unescape(rs.getString("description")),
        )

    private fun bindResult(ps: PreparedStatement, at: Int, result: Result?) {
        ps.setObject(at, result?.voters, Types.BIGINT)
        ps.setObject(at + 1, result?.totalWeight?.let(::BigDecimal), Types.NUMERIC)
        ps.setObject(at + 2, result?.totalPower?.let(::BigDecimal), Types.NUMERIC)
    }

    // All nine are written together, so the FOR voters standing in for the trio is enough.
    private fun readResults(rs: ResultSet): VoteResults? {
        rs.getLong("for_voters")
        if (rs.wasNull()) return null
        return VoteResults(
            forResult = readResult(rs, "for"),
            againstResult = readResult(rs, "against"),
            abstainResult = readResult(rs, "abstain"),
        )
    }

    private fun readResult(rs: ResultSet, prefix: String) =
        Result(
            voters = rs.getLong("${prefix}_voters"),
            totalWeight = rs.getBigDecimal("${prefix}_weight").toBigIntegerExact(),
            totalPower = rs.getBigDecimal("${prefix}_power").toBigIntegerExact(),
        )
}

/** A `b3tr_proposal.comment` row to a [ProposalComment] and back. */
object ProposalCommentRowMapping {
    const val TABLE = "b3tr_proposal.comment"

    /** The columns after `(proposal_id, voter)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_number",
            "block_id",
            "block_timestamp",
            "support",
            "weight",
            "power",
            "reason",
        )

    fun bind(ps: PreparedStatement, c: ProposalComment) {
        ps.setBigDecimal(1, BigDecimal(c.proposalId))
        ps.setBytes(2, bytes(c.voter))
        ps.setLong(3, c.blockNumber)
        ps.setBytes(4, bytes(c.blockId))
        ps.setLong(5, c.blockTimestamp)
        ps.setString(6, c.support.name)
        ps.setBigDecimal(7, BigDecimal(c.weight))
        ps.setBigDecimal(8, BigDecimal(c.power))
        ps.setString(9, PostgresText.escape(c.reason))
    }

    fun read(rs: ResultSet): ProposalComment =
        ProposalComment(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            voter = hex(rs.getBytes("voter")),
            proposalId = rs.getBigDecimal("proposal_id").toBigIntegerExact().toString(),
            support = Support.valueOf(rs.getString("support")),
            weight = rs.getBigDecimal("weight").toBigIntegerExact(),
            power = rs.getBigDecimal("power").toBigIntegerExact(),
            reason = PostgresText.unescape(rs.getString("reason")),
        )
}
