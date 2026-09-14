package org.vechain.indexer.validator

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.timeseries.TimeSeriesResolution

/** A `validator_block.slot` row to a [ValidatorBlock] and back; the id is rebuilt from the key. */
object ValidatorBlockRowMapping {

    /** The columns after `(block_number, validator, status)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "block_reward",
            "priority_reward",
            "total",
            "delegator_rewards",
            "validator_rewards",
            "is_hourly",
            "is_daily",
            "is_weekly",
            "is_monthly",
        )

    /** The flag column a sampled resolution reads; RAW has none. */
    fun sampleColumn(resolution: TimeSeriesResolution): String? =
        when (resolution) {
            TimeSeriesResolution.RAW -> null
            TimeSeriesResolution.HOURLY -> "is_hourly"
            TimeSeriesResolution.DAILY -> "is_daily"
            TimeSeriesResolution.WEEKLY -> "is_weekly"
            TimeSeriesResolution.MONTHLY -> "is_monthly"
        }

    fun bind(ps: PreparedStatement, v: ValidatorBlock) {
        ps.setLong(1, v.blockNumber)
        ps.setBytes(2, bytes(v.validator))
        ps.setString(3, v.status.name)
        ps.setBytes(4, bytes(v.blockId))
        ps.setLong(5, v.blockTimestamp)
        ps.setBigDecimal(6, v.blockReward?.toBigDecimal())
        ps.setBigDecimal(7, v.priorityReward?.toBigDecimal())
        ps.setBigDecimal(8, v.total?.toBigDecimal())
        ps.setBigDecimal(9, v.delegatorRewards?.toBigDecimal())
        ps.setBigDecimal(10, v.validatorRewards?.toBigDecimal())
        ps.setObject(11, v.isHourly, Types.BOOLEAN)
        ps.setObject(12, v.isDaily, Types.BOOLEAN)
        ps.setObject(13, v.isWeekly, Types.BOOLEAN)
        ps.setObject(14, v.isMonthly, Types.BOOLEAN)
    }

    fun read(rs: ResultSet): ValidatorBlock {
        val blockNumber = rs.getLong("block_number")
        val validator = hex(rs.getBytes("validator"))
        val status = BlockStatus.valueOf(rs.getString("status"))
        return ValidatorBlock(
            id = id(blockNumber, validator, status),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = blockNumber,
            blockTimestamp = rs.getLong("block_timestamp"),
            validator = validator,
            blockReward = rs.getBigDecimal("block_reward")?.toBigIntegerExact(),
            priorityReward = rs.getBigDecimal("priority_reward")?.toBigIntegerExact(),
            total = rs.getBigDecimal("total")?.toBigIntegerExact(),
            status = status,
            delegatorRewards = rs.getBigDecimal("delegator_rewards")?.toBigIntegerExact(),
            validatorRewards = rs.getBigDecimal("validator_rewards")?.toBigIntegerExact(),
            isHourly = rs.getObject("is_hourly", Boolean::class.javaObjectType),
            isDaily = rs.getObject("is_daily", Boolean::class.javaObjectType),
            isWeekly = rs.getObject("is_weekly", Boolean::class.javaObjectType),
            isMonthly = rs.getObject("is_monthly", Boolean::class.javaObjectType),
        )
    }

    /** The Mongo-era id, kept so callers that key on it are unchanged. */
    fun id(blockNumber: Long, validator: String, status: BlockStatus): String =
        if (status == BlockStatus.MISSED) "$blockNumber-$validator-MISSED"
        else "$blockNumber-$validator"
}
