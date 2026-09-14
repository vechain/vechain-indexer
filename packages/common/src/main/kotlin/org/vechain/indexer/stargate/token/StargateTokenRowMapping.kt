package org.vechain.indexer.stargate.token

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull
import org.vechain.indexer.validator.Status

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
            "delegation_status",
            "validator_id",
            "total_rewards_claimed",
            "total_bootstrap_rewards_claimed",
            "vet_staked",
            "migrated",
            "boosted",
            "delegation_next_period",
            "delegation_period_length",
            "validator_exiting",
        )

    fun bind(ps: PreparedStatement, t: StargateToken) {
        ps.setBigDecimal(1, BigDecimal(t.tokenId))
        ps.setLong(2, t.blockNumber)
        ps.setBytes(3, bytes(t.blockId))
        ps.setLong(4, t.blockTimestamp)
        ps.setString(5, t.level.name)
        ps.setBytes(6, bytes(t.owner))
        ps.setBytes(7, bytesOrNull(t.manager))
        ps.setString(8, t.delegationStatus.name)
        ps.setBytes(9, bytesOrNull(t.validatorId))
        ps.setBigDecimal(10, t.totalRewardsClaimed.toBigDecimal())
        ps.setBigDecimal(11, t.totalBootstrapRewardsClaimed.toBigDecimal())
        ps.setBigDecimal(12, t.vetStaked.toBigDecimal())
        ps.setBoolean(13, t.migrated)
        ps.setBoolean(14, t.boosted)
        ps.setObject(15, t.delegationNextPeriod, Types.BIGINT)
        ps.setObject(16, t.delegationPeriodLength, Types.BIGINT)
        ps.setObject(17, t.validatorExiting, Types.BOOLEAN)
    }

    fun read(rs: ResultSet): StargateToken =
        StargateToken(
            tokenId = rs.getBigDecimal("token_id").toPlainString(),
            level = TokenLevel.valueOf(rs.getString("level")),
            owner = hex(rs.getBytes("owner")),
            manager = hexOrNull(rs.getBytes("manager")),
            delegationStatus = Status.valueOf(rs.getString("delegation_status")),
            validatorId = hexOrNull(rs.getBytes("validator_id")),
            totalRewardsClaimed = rs.getBigDecimal("total_rewards_claimed").toBigIntegerExact(),
            totalBootstrapRewardsClaimed =
                rs.getBigDecimal("total_bootstrap_rewards_claimed").toBigIntegerExact(),
            vetStaked = rs.getBigDecimal("vet_staked").toBigIntegerExact(),
            migrated = rs.getBoolean("migrated"),
            boosted = rs.getBoolean("boosted"),
            blockNumber = rs.getLong("block_number"),
            blockId = hex(rs.getBytes("block_id")),
            blockTimestamp = rs.getLong("block_timestamp"),
            delegationNextPeriod =
                rs.getObject("delegation_next_period", Long::class.javaObjectType),
            delegationPeriodLength =
                rs.getObject("delegation_period_length", Long::class.javaObjectType),
            validatorExiting = rs.getObject("validator_exiting", Boolean::class.javaObjectType),
        )
}
