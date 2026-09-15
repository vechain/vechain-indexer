package org.vechain.indexer.b3tr.xAlloc

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** A `b3tr_x_alloc.result` row to an [XAllocResult] and back. */
object XAllocResultRowMapping {
    const val TABLE = "b3tr_x_alloc.result"

    /** The columns after `(round_id, app_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "voters",
            "votes_received",
            "total_amount",
            "unallocated_amount",
            "team_allocation_amount",
            "rewards_allocation_amount",
        )

    fun bind(ps: PreparedStatement, r: XAllocResult) {
        ps.setInt(1, r.roundId)
        ps.setBytes(2, bytes(r.appId))
        ps.setLong(3, r.blockNumber)
        ps.setBytes(4, bytes(r.blockId))
        ps.setLong(5, r.blockTimestamp)
        ps.setLong(6, r.voters)
        ps.setBigDecimal(7, r.votesReceived.toBigDecimal())
        ps.setObject(8, r.totalAmount, Types.NUMERIC)
        ps.setObject(9, r.unallocatedAmount, Types.NUMERIC)
        ps.setObject(10, r.teamAllocationAmount, Types.NUMERIC)
        ps.setObject(11, r.rewardsAllocationAmount, Types.NUMERIC)
    }

    fun read(rs: ResultSet): XAllocResult =
        XAllocResult(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            roundId = rs.getInt("round_id"),
            appId = hex(rs.getBytes("app_id")),
            voters = rs.getLong("voters"),
            votesReceived = rs.getBigDecimal("votes_received").toBigIntegerExact(),
            totalAmount = rs.getBigDecimal("total_amount"),
            unallocatedAmount = rs.getBigDecimal("unallocated_amount"),
            teamAllocationAmount = rs.getBigDecimal("team_allocation_amount"),
            rewardsAllocationAmount = rs.getBigDecimal("rewards_allocation_amount"),
        )
}
