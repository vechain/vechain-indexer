package org.vechain.indexer.validator

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.stargate.token.TokenLevel

/** A `delegation.state` row to a [Delegation] and back; ids and amounts are base-10 strings. */
object DelegationRowMapping {

    /** The columns after `(id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "validator",
            "token_id",
            "owner",
            "status",
            "token_level",
            "staked_amount",
            "total_rewards_claimed",
            "tx_id",
            "transition_at_block",
            "initiated_at_block",
        )

    fun bind(ps: PreparedStatement, d: Delegation) {
        ps.setBigDecimal(1, BigDecimal(d.id))
        ps.setLong(2, d.blockNumber)
        ps.setBytes(3, bytes(d.blockId))
        ps.setLong(4, d.blockTimestamp)
        ps.setBytes(5, bytes(d.validator))
        ps.setBigDecimal(6, BigDecimal(d.tokenId))
        ps.setBytes(7, bytes(d.owner))
        ps.setString(8, d.status.name)
        ps.setString(9, d.tokenLevel.name)
        ps.setBigDecimal(10, BigDecimal(d.stakedAmount))
        ps.setBigDecimal(11, d.totalRewardsClaimed.toBigDecimal())
        ps.setBytes(12, bytes(d.txId))
        ps.setObject(13, d.transitionAtBlock, Types.BIGINT)
        ps.setObject(14, d.initiatedAtBlock, Types.BIGINT)
    }

    fun read(rs: ResultSet): Delegation =
        Delegation(
            id = rs.getBigDecimal("id").toPlainString(),
            validator = hex(rs.getBytes("validator")),
            tokenId = rs.getBigDecimal("token_id").toPlainString(),
            owner = hex(rs.getBytes("owner")),
            status = DelegationStatus.valueOf(rs.getString("status")),
            tokenLevel = TokenLevel.valueOf(rs.getString("token_level")),
            stakedAmount = rs.getBigDecimal("staked_amount").toPlainString(),
            totalRewardsClaimed = rs.getBigDecimal("total_rewards_claimed").toBigIntegerExact(),
            txId = hex(rs.getBytes("tx_id")),
            transitionAtBlock = rs.getObject("transition_at_block", Long::class.javaObjectType),
            initiatedAtBlock = rs.getObject("initiated_at_block", Long::class.javaObjectType),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
}
