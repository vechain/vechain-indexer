package org.vechain.indexer.stargate.token

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull

/** A `stargate_token.state` row to a [StargateToken] and back. */
object StargateTokenRowMapping {

    /** The columns after `(token_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "level",
            "owner",
            "manager",
            "total_rewards_claimed",
            "total_bootstrap_rewards_claimed",
            "vet_staked",
            "migrated",
            "boosted",
        )

    fun bind(ps: PreparedStatement, t: StargateToken) {
        ps.setBigDecimal(1, BigDecimal(t.tokenId))
        ps.setLong(2, t.blockNumber)
        ps.setBytes(3, bytes(t.blockId))
        ps.setLong(4, t.blockTimestamp)
        ps.setString(5, t.level.name)
        ps.setBytes(6, bytes(t.owner))
        ps.setBytes(7, bytesOrNull(t.manager))
        ps.setBigDecimal(8, t.totalRewardsClaimed.toBigDecimal())
        ps.setBigDecimal(9, t.totalBootstrapRewardsClaimed.toBigDecimal())
        ps.setBigDecimal(10, t.vetStaked.toBigDecimal())
        ps.setBoolean(11, t.migrated)
        ps.setBoolean(12, t.boosted)
    }

    fun read(rs: ResultSet): StargateToken =
        StargateToken(
            tokenId = rs.getBigDecimal("token_id").toPlainString(),
            level = TokenLevel.valueOf(rs.getString("level")),
            owner = hex(rs.getBytes("owner")),
            manager = hexOrNull(rs.getBytes("manager")),
            totalRewardsClaimed = rs.getBigDecimal("total_rewards_claimed").toBigIntegerExact(),
            totalBootstrapRewardsClaimed =
                rs.getBigDecimal("total_bootstrap_rewards_claimed").toBigIntegerExact(),
            vetStaked = rs.getBigDecimal("vet_staked").toBigIntegerExact(),
            migrated = rs.getBoolean("migrated"),
            boosted = rs.getBoolean("boosted"),
            blockNumber = rs.getLong("block_number"),
            blockId = hex(rs.getBytes("block_id")),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
}
