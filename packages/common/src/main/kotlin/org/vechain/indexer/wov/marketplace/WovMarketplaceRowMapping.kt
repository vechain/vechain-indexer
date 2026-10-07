package org.vechain.indexer.wov.marketplace

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bareHex
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** A `wov_marketplace.sale` row to a [WovSale] and back. */
object WovSaleRowMapping {
    const val TABLE = "wov_marketplace.sale"

    /** The columns after `id`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_number",
            "block_id",
            "block_timestamp",
            "tx_id",
            "contract_address",
            "mechanism",
            "market_id",
            "nft",
            "token_id",
            "buyer",
            "payment_token",
            "price",
        )

    fun bind(ps: PreparedStatement, sale: WovSale) {
        ps.setBytes(1, bytes(sale.id))
        ps.setLong(2, sale.blockNumber)
        ps.setBytes(3, bytes(sale.blockId))
        ps.setLong(4, sale.blockTimestamp)
        ps.setBytes(5, bytes(sale.txId))
        ps.setBytes(6, bytes(sale.contractAddress))
        ps.setString(7, sale.mechanism.name)
        ps.setBigDecimal(8, BigDecimal(sale.marketId))
        ps.setBytes(9, bytes(sale.nft))
        ps.setBigDecimal(10, BigDecimal(sale.tokenId))
        ps.setBytes(11, bytes(sale.buyer))
        ps.setBytes(12, bytes(sale.paymentToken))
        ps.setBigDecimal(13, BigDecimal(sale.price))
    }

    fun read(rs: ResultSet): WovSale =
        WovSale(
            id = bareHex(rs.getBytes("id")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            txId = hex(rs.getBytes("tx_id")),
            contractAddress = hex(rs.getBytes("contract_address")),
            mechanism = WovMechanism.valueOf(rs.getString("mechanism")),
            marketId = rs.getBigDecimal("market_id").toBigIntegerExact(),
            nft = hex(rs.getBytes("nft")),
            tokenId = rs.getBigDecimal("token_id").toBigIntegerExact(),
            buyer = hex(rs.getBytes("buyer")),
            paymentToken = hex(rs.getBytes("payment_token")),
            price = rs.getBigDecimal("price").toBigIntegerExact(),
        )
}

/** A `wov_marketplace.terms` row to a [WovSaleTerms] and back. */
object WovTermsRowMapping {
    const val TABLE = "wov_marketplace.terms"

    val COLUMNS = listOf("contract_address", "market_id", "block_number", "payment_token", "price")

    fun bind(ps: PreparedStatement, terms: WovSaleTerms) {
        ps.setBytes(1, bytes(terms.contractAddress))
        ps.setBigDecimal(2, BigDecimal(terms.marketId))
        ps.setLong(3, terms.blockNumber)
        ps.setBytes(4, bytes(terms.paymentToken))
        ps.setBigDecimal(5, BigDecimal(terms.price))
    }

    fun read(rs: ResultSet): WovSaleTerms =
        WovSaleTerms(
            contractAddress = hex(rs.getBytes("contract_address")),
            marketId = rs.getBigDecimal("market_id").toBigIntegerExact(),
            blockNumber = rs.getLong("block_number"),
            paymentToken = hex(rs.getBytes("payment_token")),
            price = rs.getBigDecimal("price").toBigIntegerExact(),
        )
}

/** A `wov_marketplace.buyer_running` row to a [WovBuyerRunning] and back. */
object WovBuyerRunningRowMapping {
    const val TABLE = "wov_marketplace.buyer_running"

    val COLUMNS =
        listOf("buyer", "payment_token", "block_number", "block_timestamp", "items", "spend")

    fun bind(ps: PreparedStatement, row: WovBuyerRunning) {
        ps.setBytes(1, bytes(row.buyer))
        ps.setBytes(2, bytes(row.paymentToken))
        ps.setLong(3, row.blockNumber)
        ps.setLong(4, row.blockTimestamp)
        ps.setLong(5, row.items)
        ps.setBigDecimal(6, BigDecimal(row.spend))
    }

    fun read(rs: ResultSet): WovBuyerRunning =
        WovBuyerRunning(
            buyer = hex(rs.getBytes("buyer")),
            paymentToken = hex(rs.getBytes("payment_token")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            items = rs.getLong("items"),
            spend = rs.getBigDecimal("spend").toBigIntegerExact(),
        )
}

/** A `wov_marketplace.buyer` row to a [WovBuyer] and back. */
object WovBuyerRowMapping {
    const val TABLE = "wov_marketplace.buyer"

    val COLUMNS = listOf("address", "first_block_number", "first_block_timestamp")

    fun bind(ps: PreparedStatement, buyer: WovBuyer) {
        ps.setBytes(1, bytes(buyer.address))
        ps.setLong(2, buyer.firstBlockNumber)
        ps.setLong(3, buyer.firstBlockTimestamp)
    }

    fun read(rs: ResultSet): WovBuyer =
        WovBuyer(
            address = hex(rs.getBytes("address")),
            firstBlockNumber = rs.getLong("first_block_number"),
            firstBlockTimestamp = rs.getLong("first_block_timestamp"),
        )
}
