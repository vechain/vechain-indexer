package org.vechain.indexer.b3tr.gm

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** A `b3tr_gm.state` row to a [GmNft] and back. */
object GmNftRowMapping {
    const val TABLE = "b3tr_gm.state"

    /** The columns after `(token_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "level",
            "attached_node_id",
            "b3tr_donated",
            "owner",
        )

    fun bind(ps: PreparedStatement, nft: GmNft) {
        ps.setBigDecimal(1, BigDecimal(nft.tokenId))
        ps.setLong(2, nft.blockNumber)
        ps.setBytes(3, bytes(nft.blockId))
        ps.setLong(4, nft.blockTimestamp)
        ps.setString(5, nft.level.name)
        ps.setBigDecimal(6, nft.attachedNodeId?.let(::BigDecimal))
        ps.setBigDecimal(7, nft.b3trDonated.toBigDecimal())
        ps.setBytes(8, bytes(nft.owner))
    }

    fun read(rs: ResultSet): GmNft =
        GmNft(
            tokenId = rs.getBigDecimal("token_id").toPlainString(),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            level = GmLevelName.valueOf(rs.getString("level")),
            attachedNodeId = rs.getBigDecimal("attached_node_id")?.toPlainString(),
            b3trDonated = rs.getBigDecimal("b3tr_donated").toBigIntegerExact(),
            owner = hex(rs.getBytes("owner")),
        )
}
