package org.vechain.indexer.stargate.vetDelegated

import com.fasterxml.jackson.core.type.TypeReference
import java.math.BigInteger
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresJson
import org.vechain.indexer.stargate.token.TokenLevel

/** A `vet_delegated.total_by_block` row to a [VetDelegatedByBlock] and back. */
object VetDelegatedRowMapping {

    private val AMOUNTS = object : TypeReference<Map<String, String>>() {}

    /** The columns after `block_number`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "total",
            "total_nft_count",
            "hour_of_day",
            "day_of_month",
            "week_of_year",
            "month",
            "year",
            "time_frames",
            "block_total",
            "hour_total",
            "day_total",
            "week_total",
            "month_total",
            "year_total",
            "by_level",
            "nft_count_by_level",
        )

    fun bind(ps: PreparedStatement, d: VetDelegatedByBlock) {
        ps.setLong(1, d.blockNumber)
        ps.setBytes(2, bytes(d.blockId))
        ps.setLong(3, d.blockTimestamp)
        ps.setBigDecimal(4, d.total.toBigDecimal())
        ps.setLong(5, d.totalNftCount)
        ps.setLong(6, d.hourOfDay)
        ps.setLong(7, d.dayOfMonth)
        ps.setLong(8, d.weekOfYear)
        ps.setLong(9, d.month)
        ps.setLong(10, d.year)
        ps.setArray(
            11,
            ps.connection.createArrayOf(
                "vet_delegated.time_frame",
                d.timeFrames.map { it.name }.toTypedArray(),
            ),
        )
        ps.setBigDecimal(12, d.blockTotal?.toBigDecimal())
        ps.setBigDecimal(13, d.hourTotal?.toBigDecimal())
        ps.setBigDecimal(14, d.dayTotal?.toBigDecimal())
        ps.setBigDecimal(15, d.weekTotal?.toBigDecimal())
        ps.setBigDecimal(16, d.monthTotal?.toBigDecimal())
        ps.setBigDecimal(17, d.yearTotal?.toBigDecimal())
        ps.setObject(18, PostgresJson.write(d.byLevel.mapKeys { it.key.name }), Types.OTHER)
        ps.setObject(19, PostgresJson.write(d.nftCountByLevel.mapKeys { it.key.name }), Types.OTHER)
    }

    fun read(rs: ResultSet): VetDelegatedByBlock =
        VetDelegatedByBlock(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            total = rs.getBigDecimal("total").toBigIntegerExact(),
            byLevel = levels(rs.getString("by_level")).mapValues { BigInteger(it.value) },
            totalNftCount = rs.getLong("total_nft_count"),
            nftCountByLevel =
                levels(rs.getString("nft_count_by_level")).mapValues { it.value.toLong() },
            hourOfDay = rs.getLong("hour_of_day"),
            dayOfMonth = rs.getLong("day_of_month"),
            weekOfYear = rs.getLong("week_of_year"),
            month = rs.getLong("month"),
            year = rs.getLong("year"),
            timeFrames =
                (rs.getArray("time_frames").array as Array<*>).map {
                    TimeFrame.valueOf(it as String)
                },
            blockTotal = rs.getBigDecimal("block_total")?.toBigIntegerExact(),
            hourTotal = rs.getBigDecimal("hour_total")?.toBigIntegerExact(),
            dayTotal = rs.getBigDecimal("day_total")?.toBigIntegerExact(),
            weekTotal = rs.getBigDecimal("week_total")?.toBigIntegerExact(),
            monthTotal = rs.getBigDecimal("month_total")?.toBigIntegerExact(),
            yearTotal = rs.getBigDecimal("year_total")?.toBigIntegerExact(),
        )

    private fun levels(json: String?): Map<TokenLevel, String> =
        PostgresJson.read(json, AMOUNTS).orEmpty().mapKeys { TokenLevel.valueOf(it.key) }
}
