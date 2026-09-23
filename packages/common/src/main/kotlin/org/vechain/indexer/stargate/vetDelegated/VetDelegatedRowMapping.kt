package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.stargate.token.TokenLevelJson
import org.vechain.indexer.timeseries.TimeFrameColumns
import org.vechain.indexer.timeseries.TimeFrameRowMapping

/** A `delegation.total_by_block` row to a [VetDelegatedByBlock] and back. */
object VetDelegatedRowMapping : TimeFrameRowMapping<VetDelegatedByBlock> {
    override val table = "delegation.total_by_block"
    override val columns = listOf("total", "total_nft_count", "by_level", "nft_count_by_level")

    override fun bind(ps: PreparedStatement, from: Int, d: VetDelegatedByBlock) {
        ps.setBigDecimal(from, d.total.toBigDecimal())
        ps.setLong(from + 1, d.totalNftCount)
        ps.setObject(from + 2, TokenLevelJson.write(d.byLevel), Types.OTHER)
        ps.setObject(from + 3, TokenLevelJson.write(d.nftCountByLevel), Types.OTHER)
    }

    override fun read(rs: ResultSet): VetDelegatedByBlock =
        VetDelegatedByBlock(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            total = rs.getBigDecimal("total").toBigIntegerExact(),
            byLevel =
                TokenLevelJson.read(rs.getString("by_level")).mapValues { BigInteger(it.value) },
            totalNftCount = rs.getLong("total_nft_count"),
            nftCountByLevel =
                TokenLevelJson.read(rs.getString("nft_count_by_level")).mapValues {
                    it.value.toLong()
                },
            period = TimeFrameColumns.period(rs),
        )
}
