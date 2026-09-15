package org.vechain.indexer.b3tr.treasury

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bareHex
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** A `b3tr_treasury.transfer` row to a [TreasuryTransfer] and back. */
object TreasuryTransferRowMapping {
    const val TABLE = "b3tr_treasury.transfer"

    /** The columns after `id`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_number",
            "block_id",
            "block_timestamp",
            "tx_id",
            "from_address",
            "to_address",
            "value",
            "category",
            "label",
            "counterparty_name",
        )

    fun bind(ps: PreparedStatement, t: TreasuryTransfer) {
        ps.setBytes(1, bytes(t.id))
        ps.setLong(2, t.blockNumber)
        ps.setBytes(3, bytes(t.blockId))
        ps.setLong(4, t.blockTimestamp)
        ps.setBytes(5, bytes(t.txId))
        ps.setBytes(6, bytes(t.from))
        ps.setBytes(7, bytes(t.to))
        ps.setBigDecimal(8, BigDecimal(t.value))
        ps.setString(9, t.category.name)
        ps.setString(10, t.label)
        ps.setString(11, t.counterpartyName)
    }

    fun read(rs: ResultSet): TreasuryTransfer =
        TreasuryTransfer(
            id = bareHex(rs.getBytes("id")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            txId = hex(rs.getBytes("tx_id")),
            from = hex(rs.getBytes("from_address")),
            to = hex(rs.getBytes("to_address")),
            value = rs.getBigDecimal("value").toPlainString(),
            category = TreasuryTransferCategory.valueOf(rs.getString("category")),
            label = rs.getString("label"),
            counterpartyName = rs.getString("counterparty_name"),
        )
}
