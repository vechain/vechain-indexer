package org.vechain.indexer.stargate.staking

import java.math.BigInteger
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.stargate.token.TokenLevelJson
import org.vechain.indexer.timeseries.TimeFrameColumns
import org.vechain.indexer.timeseries.TimeFrameRowMapping

/** A `stargate_staking.vet_staked_by_block` row to a [VetStakedByBlock] and back. */
object VetStakedRowMapping : TimeFrameRowMapping<VetStakedByBlock> {
    override val table = "stargate_staking.vet_staked_by_block"
    override val columns = listOf("total", "total_nft_count", "by_level", "nft_count_by_level")

    override fun bind(ps: PreparedStatement, from: Int, d: VetStakedByBlock) {
        ps.setBigDecimal(from, d.total.toBigDecimal())
        ps.setLong(from + 1, d.totalNftCount)
        ps.setObject(from + 2, TokenLevelJson.write(d.byLevel), Types.OTHER)
        ps.setObject(from + 3, TokenLevelJson.write(d.nftCountByLevel), Types.OTHER)
    }

    override fun read(rs: ResultSet): VetStakedByBlock =
        VetStakedByBlock(
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

/** A `stargate_staking.nft_holders_by_block` row to an [NftHoldersByBlock] and back. */
object NftHoldersRowMapping : TimeFrameRowMapping<NftHoldersByBlock> {
    override val table = "stargate_staking.nft_holders_by_block"
    override val columns = listOf("total", "by_level")

    override fun bind(ps: PreparedStatement, from: Int, d: NftHoldersByBlock) {
        ps.setLong(from, d.total)
        ps.setObject(from + 1, TokenLevelJson.write(d.byLevel), Types.OTHER)
    }

    override fun read(rs: ResultSet): NftHoldersByBlock =
        NftHoldersByBlock(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            total = rs.getLong("total"),
            byLevel = TokenLevelJson.read(rs.getString("by_level")).mapValues { it.value.toLong() },
            period = TimeFrameColumns.period(rs),
        )
}

/** A `stargate_staking.owner_balance` row to an [NftOwnerBalance] and back. */
object NftOwnerBalanceRowMapping {
    const val TABLE = "stargate_staking.owner_balance"

    /** The columns after `(owner, block_number)`, in the order [bind] sets them. */
    val COLUMNS = listOf("block_id", "block_timestamp", "total", "by_level")

    fun bind(ps: PreparedStatement, b: NftOwnerBalance) {
        ps.setBytes(1, bytes(b.owner))
        ps.setLong(2, b.blockNumber)
        ps.setBytes(3, bytes(b.blockId))
        ps.setLong(4, b.blockTimestamp)
        ps.setLong(5, b.total)
        ps.setObject(6, TokenLevelJson.write(b.byLevel), Types.OTHER)
    }

    fun read(rs: ResultSet): NftOwnerBalance =
        NftOwnerBalance(
            owner = hex(rs.getBytes("owner")),
            total = rs.getLong("total"),
            byLevel = TokenLevelJson.read(rs.getString("by_level")).mapValues { it.value.toLong() },
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
}
