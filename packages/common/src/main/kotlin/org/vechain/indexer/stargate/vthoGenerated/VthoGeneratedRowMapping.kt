package org.vechain.indexer.stargate.vthoGenerated

import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.timeseries.TimeFrameColumns
import org.vechain.indexer.timeseries.TimeFrameRowMapping

/** A `stargate_vtho_generated.total_by_block` row to a [VthoGeneratedByBlock] and back. */
object VthoGeneratedRowMapping : TimeFrameRowMapping<VthoGeneratedByBlock> {
    override val table = "stargate_vtho_generated.total_by_block"
    override val columns = listOf("total")

    override fun bind(ps: PreparedStatement, from: Int, d: VthoGeneratedByBlock) {
        ps.setBigDecimal(from, d.total.toBigDecimal())
    }

    override fun read(rs: ResultSet): VthoGeneratedByBlock =
        VthoGeneratedByBlock(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            total = rs.getBigDecimal("total").toBigIntegerExact(),
            period = TimeFrameColumns.period(rs),
        )
}
