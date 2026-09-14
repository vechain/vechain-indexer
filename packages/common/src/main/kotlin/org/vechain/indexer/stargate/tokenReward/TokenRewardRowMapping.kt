package org.vechain.indexer.stargate.tokenReward

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** A `token_reward.state` row to a [TokenReward] and back. */
object TokenRewardRowMapping {

    /** The columns after `(id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "token_id",
            "cycle",
            "validator",
            "rewards",
            "effective_stake",
            "reward_period",
            "day_of_month",
            "week_of_year",
            "month",
            "year",
            "day_reward",
            "week_reward",
            "month_reward",
            "year_reward",
            "cycle_reward",
        )

    fun bind(ps: PreparedStatement, r: TokenReward) {
        ps.setString(1, r.id)
        ps.setLong(2, r.blockNumber)
        ps.setBytes(3, bytes(r.blockId))
        ps.setLong(4, r.blockTimestamp)
        ps.setBigDecimal(5, BigDecimal(r.tokenId))
        ps.setLong(6, r.cycle)
        ps.setBytes(7, bytes(r.validator))
        ps.setBigDecimal(8, r.rewards.toBigDecimal())
        ps.setBigDecimal(9, r.effectiveStake?.toBigDecimal())
        ps.setString(10, r.rewardPeriod.name)
        ps.setLong(11, r.dayOfMonth)
        ps.setLong(12, r.weekOfYear)
        ps.setLong(13, r.month)
        ps.setLong(14, r.year)
        ps.setBigDecimal(15, r.dayReward?.toBigDecimal())
        ps.setBigDecimal(16, r.weekReward?.toBigDecimal())
        ps.setBigDecimal(17, r.monthReward?.toBigDecimal())
        ps.setBigDecimal(18, r.yearReward?.toBigDecimal())
        ps.setBigDecimal(19, r.cycleReward?.toBigDecimal())
    }

    fun read(rs: ResultSet): TokenReward =
        TokenReward(
            id = rs.getString("id"),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            tokenId = rs.getBigDecimal("token_id").toPlainString(),
            cycle = rs.getLong("cycle"),
            validator = hex(rs.getBytes("validator")),
            rewards = rs.getBigDecimal("rewards").toBigIntegerExact(),
            effectiveStake = rs.getBigDecimal("effective_stake")?.toBigIntegerExact(),
            rewardPeriod = RewardPeriod.valueOf(rs.getString("reward_period")),
            dayOfMonth = rs.getLong("day_of_month"),
            weekOfYear = rs.getLong("week_of_year"),
            month = rs.getLong("month"),
            year = rs.getLong("year"),
            dayReward = rs.getBigDecimal("day_reward")?.toBigIntegerExact(),
            weekReward = rs.getBigDecimal("week_reward")?.toBigIntegerExact(),
            monthReward = rs.getBigDecimal("month_reward")?.toBigIntegerExact(),
            yearReward = rs.getBigDecimal("year_reward")?.toBigIntegerExact(),
            cycleReward = rs.getBigDecimal("cycle_reward")?.toBigIntegerExact(),
        )
}
