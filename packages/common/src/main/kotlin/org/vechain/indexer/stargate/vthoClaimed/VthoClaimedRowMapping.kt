package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.timeseries.TimeFrameColumns
import org.vechain.indexer.timeseries.TimeFrameRowMapping

/** A `stargate_vtho_claimed.total_by_block` row to a [VthoClaimedByBlock] and back. */
object VthoClaimedRowMapping : TimeFrameRowMapping<VthoClaimedByBlock> {
    override val table = "stargate_vtho_claimed.total_by_block"
    override val columns = listOf("total", "legacy_rewards")

    override fun bind(ps: PreparedStatement, from: Int, d: VthoClaimedByBlock) {
        ps.setBigDecimal(from, d.total.toBigDecimal())
        ps.setBigDecimal(from + 1, d.legacyRewards.toBigDecimal())
    }

    override fun read(rs: ResultSet): VthoClaimedByBlock =
        VthoClaimedByBlock(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            total = rs.getBigDecimal("total").toBigIntegerExact(),
            legacyRewards = rs.getBigDecimal("legacy_rewards").toBigIntegerExact(),
            period = TimeFrameColumns.period(rs),
        )
}

/** A `stargate_vtho_claimed.claimed_by_token` row to a [VthoClaimedByToken] and back. */
object VthoClaimedByTokenRowMapping {
    const val TABLE = "stargate_vtho_claimed.claimed_by_token"

    /** The columns after `(account, token_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS = listOf("block_id", "block_timestamp", "legacy_rewards", "delegation_rewards")

    fun bind(ps: PreparedStatement, t: VthoClaimedByToken) {
        ps.setBytes(1, bytes(t.account))
        ps.setBigDecimal(2, BigDecimal(t.tokenId))
        ps.setLong(3, t.blockNumber)
        ps.setBytes(4, bytes(t.blockId))
        ps.setLong(5, t.blockTimestamp)
        ps.setBigDecimal(6, t.legacyRewards.toBigDecimal())
        ps.setBigDecimal(7, t.delegationRewards.toBigDecimal())
    }

    fun read(rs: ResultSet): VthoClaimedByToken =
        VthoClaimedByToken(
            account = hex(rs.getBytes("account")),
            tokenId = rs.getBigDecimal("token_id").toPlainString(),
            legacyRewards = rs.getBigDecimal("legacy_rewards").toBigIntegerExact(),
            delegationRewards = rs.getBigDecimal("delegation_rewards").toBigIntegerExact(),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
}
