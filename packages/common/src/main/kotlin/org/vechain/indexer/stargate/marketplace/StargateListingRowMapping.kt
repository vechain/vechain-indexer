package org.vechain.indexer.stargate.marketplace

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull

/** A `stargate_marketplace.listing` row to a [StargateListing] and back. */
object StargateListingRowMapping {
    const val TABLE = "stargate_marketplace.listing"

    /** The columns after `(sale_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "tx_id",
            "token_id",
            "seller",
            "price",
            "payment_token",
            "status",
            "buyer",
        )

    fun bind(ps: PreparedStatement, l: StargateListing) {
        ps.setBigDecimal(1, BigDecimal(l.saleId))
        ps.setLong(2, l.blockNumber)
        ps.setBytes(3, bytes(l.blockId))
        ps.setLong(4, l.blockTimestamp)
        ps.setBytes(5, bytes(l.txId))
        ps.setBigDecimal(6, BigDecimal(l.tokenId))
        ps.setBytes(7, bytes(l.seller))
        ps.setBigDecimal(8, BigDecimal(l.price))
        ps.setBytes(9, bytes(l.paymentToken))
        ps.setString(10, l.status.name)
        ps.setBytes(11, bytesOrNull(l.buyer))
    }

    fun read(rs: ResultSet): StargateListing =
        StargateListing(
            saleId = rs.getBigDecimal("sale_id").toBigIntegerExact(),
            blockNumber = rs.getLong("block_number"),
            blockId = hex(rs.getBytes("block_id")),
            blockTimestamp = rs.getLong("block_timestamp"),
            txId = hex(rs.getBytes("tx_id")),
            tokenId = rs.getBigDecimal("token_id").toBigIntegerExact(),
            seller = hex(rs.getBytes("seller")),
            price = rs.getBigDecimal("price").toBigIntegerExact(),
            paymentToken = hex(rs.getBytes("payment_token")),
            status = ListingStatus.valueOf(rs.getString("status")),
            buyer = hexOrNull(rs.getBytes("buyer")),
        )
}
