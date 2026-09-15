package org.vechain.indexer.transfer

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.postgres.PostgresHex.bareHex
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull

/** The two `transfers` tables to their models and back. */
object TransferRowMapping {
    const val TABLE = "transfers.transfer"
    const val INTERACTION_TABLE = "transfers.token_interaction"

    /** The columns after `id`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_number",
            "block_id",
            "block_timestamp",
            "transfer_index",
            "tx_id",
            "from_address",
            "to_address",
            "value",
            "token_address",
            "token_id",
            "topics",
            "event_type",
        )

    val INTERACTION_COLUMNS =
        listOf("wallet_address", "contract_address", "block_number", "block_id", "block_timestamp")

    fun bind(ps: PreparedStatement, t: IndexedTransferEvent) {
        ps.setBytes(1, bytes(t.id))
        ps.setLong(2, t.blockNumber)
        ps.setBytes(3, bytes(t.blockId))
        ps.setLong(4, t.blockTimestamp)
        ps.setInt(5, t.transferIndex.toInt())
        ps.setBytes(6, bytes(t.txId))
        ps.setBytes(7, bytes(t.from))
        ps.setBytes(8, bytes(t.to))
        ps.setBigDecimal(9, quantity(t.value))
        ps.setBytes(10, bytesOrNull(t.tokenAddress))
        ps.setBigDecimal(11, t.tokenId?.let(::quantity))
        ps.setArray(12, ps.connection.createArrayOf("bytea", t.topics.map(::bytes).toTypedArray()))
        ps.setString(13, t.eventType.name)
    }

    fun bind(ps: PreparedStatement, i: FungibleTokenInteraction) {
        ps.setBytes(1, bytes(i.walletAddress))
        ps.setBytes(2, bytes(i.contractAddress))
        ps.setLong(3, i.blockNumber)
        ps.setBytes(4, bytes(i.blockId))
        ps.setLong(5, i.blockTimestamp)
    }

    fun read(rs: ResultSet): IndexedTransferEvent =
        IndexedTransferEvent(
            id = bareHex(rs.getBytes("id")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            transferIndex = rs.getLong("transfer_index"),
            txId = hex(rs.getBytes("tx_id")),
            from = hex(rs.getBytes("from_address")),
            to = hex(rs.getBytes("to_address")),
            value = rs.getBigDecimal("value").toPlainString(),
            tokenAddress = hexOrNull(rs.getBytes("token_address")),
            tokenId = rs.getBigDecimal("token_id")?.toPlainString(),
            topics = (rs.getArray("topics").array as Array<*>).map { hex(it as ByteArray) },
            eventType = TransferEventType.valueOf(rs.getString("event_type")),
        )

    fun readInteraction(rs: ResultSet): FungibleTokenInteraction =
        FungibleTokenInteraction(
            contractAddress = hex(rs.getBytes("contract_address")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            walletAddress = hex(rs.getBytes("wallet_address")),
        )

    private fun quantity(s: String): BigDecimal =
        if (s.startsWith("0x")) PostgresHex.quantity(s) else BigDecimal(s)
}
