package org.vechain.indexer.explorer

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** An `explorer.block_usage` row to a [BlockUsage] and back. */
object BlockUsageRowMapping {
    const val TABLE = "explorer.block_usage"
    const val FRAME_TYPE = "explorer.time_frame"

    /** The columns after `block_number`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "cumulative_gas_limit",
            "cumulative_gas_used",
            "cumulative_base_fee_per_gas",
            "cumulative_num_transactions",
            "cumulative_num_clauses",
            "time_frames",
        )

    fun bind(ps: PreparedStatement, u: BlockUsage) {
        ps.setLong(1, u.blockNumber)
        ps.setBytes(2, bytes(u.blockId))
        ps.setLong(3, u.blockTimestamp)
        ps.setBigDecimal(4, u.cumulativeGasLimit.toBigDecimal())
        ps.setBigDecimal(5, u.cumulativeGasUsed.toBigDecimal())
        ps.setObject(6, u.cumulativeBaseFeePerGas?.toBigDecimal(), Types.NUMERIC)
        ps.setBigDecimal(7, u.cumulativeNumTransactions.toBigDecimal())
        ps.setBigDecimal(8, u.cumulativeNumClauses.toBigDecimal())
        ps.setArray(
            9,
            ps.connection.createArrayOf(FRAME_TYPE, u.timeFrames.map { it.name }.toTypedArray()),
        )
    }

    fun read(rs: ResultSet): BlockUsage =
        BlockUsage(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            cumulativeGasLimit = rs.getBigDecimal("cumulative_gas_limit").toBigIntegerExact(),
            cumulativeGasUsed = rs.getBigDecimal("cumulative_gas_used").toBigIntegerExact(),
            cumulativeBaseFeePerGas =
                rs.getBigDecimal("cumulative_base_fee_per_gas")?.toBigIntegerExact(),
            cumulativeNumTransactions =
                rs.getBigDecimal("cumulative_num_transactions").toBigIntegerExact(),
            cumulativeNumClauses = rs.getBigDecimal("cumulative_num_clauses").toBigIntegerExact(),
            timeFrames =
                (rs.getArray("time_frames").array as Array<*>).map {
                    TimeFrame.valueOf(it as String)
                },
        )
}

/** An `explorer.daily_fees` row to an [AverageFeesPerUser] and back. */
object AverageFeesPerUserRowMapping {
    const val TABLE = "explorer.daily_fees"

    /** The columns after `(day_start_timestamp, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "date",
            "total_fees_paid",
            "daily_active_users",
            "average_fees_per_user",
        )

    fun bind(ps: PreparedStatement, f: AverageFeesPerUser) {
        ps.setLong(1, f.dayStartTimestamp)
        ps.setLong(2, f.blockNumber)
        ps.setBytes(3, bytes(f.blockId))
        ps.setLong(4, f.blockTimestamp)
        ps.setString(5, f.date)
        ps.setBigDecimal(6, f.totalFeesPaid)
        ps.setLong(7, f.dailyActiveUsers)
        ps.setBigDecimal(8, f.averageFeesPerUser)
    }

    fun read(rs: ResultSet): AverageFeesPerUser =
        AverageFeesPerUser(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            date = rs.getString("date"),
            dayStartTimestamp = rs.getLong("day_start_timestamp"),
            totalFeesPaid = rs.getBigDecimal("total_fees_paid"),
            dailyActiveUsers = rs.getLong("daily_active_users"),
            averageFeesPerUser = rs.getBigDecimal("average_fees_per_user"),
        )
}
