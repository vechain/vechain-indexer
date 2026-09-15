package org.vechain.indexer.b3tr.balance

import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** A `b3tr_balance.state` row to a [B3trBalance] and back. */
object B3trBalanceRowMapping {
    const val TABLE = "b3tr_balance.state"

    /** The columns after `(address, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf("block_id", "block_timestamp", "vot3_balance", "b3tr_balance", "total_balance")

    fun bind(ps: PreparedStatement, b: B3trBalance) {
        ps.setBytes(1, bytes(b.address))
        ps.setLong(2, b.blockNumber)
        ps.setBytes(3, bytes(b.blockId))
        ps.setLong(4, b.blockTimestamp)
        ps.setBigDecimal(5, b.vot3Balance)
        ps.setBigDecimal(6, b.b3trBalance)
        ps.setBigDecimal(7, b.totalBalance)
    }

    fun read(rs: ResultSet): B3trBalance =
        B3trBalance(
            address = hex(rs.getBytes("address")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            vot3Balance = rs.getBigDecimal("vot3_balance"),
            b3trBalance = rs.getBigDecimal("b3tr_balance"),
            totalBalance = rs.getBigDecimal("total_balance"),
        )
}
