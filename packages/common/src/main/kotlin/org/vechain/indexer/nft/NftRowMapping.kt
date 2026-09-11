package org.vechain.indexer.nft

import java.math.BigDecimal
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bareHex
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

class NftRow(
    val contractAddress: ByteArray,
    val tokenId: BigDecimal,
    val blockNumber: Long,
    val id: ByteArray,
    val owner: ByteArray,
    val txId: ByteArray,
    val blockId: ByteArray,
    val blockTimestamp: Long,
)

/** [IndexedNft] to a row and back: `assemble(flatten(nft))` is `nft` again. */
object NftRowMapping {

    fun flatten(nft: IndexedNft): NftRow =
        NftRow(
            contractAddress = bytes(nft.contractAddress),
            tokenId = BigDecimal(nft.tokenId),
            blockNumber = nft.blockNumber,
            id = bytes(nft.id),
            owner = bytes(nft.owner),
            txId = bytes(nft.txId),
            blockId = bytes(nft.blockId),
            blockTimestamp = nft.blockTimestamp,
        )

    fun assemble(row: NftRow): IndexedNft =
        IndexedNft(
            id = bareHex(row.id),
            tokenId = row.tokenId.toPlainString(),
            contractAddress = hex(row.contractAddress),
            owner = hex(row.owner),
            txId = hex(row.txId),
            blockNumber = row.blockNumber,
            blockId = hex(row.blockId),
            blockTimestamp = row.blockTimestamp,
        )

    fun row(rs: ResultSet): NftRow =
        NftRow(
            contractAddress = rs.getBytes("contract_address"),
            tokenId = rs.getBigDecimal("token_id"),
            blockNumber = rs.getLong("block_number"),
            id = rs.getBytes("id"),
            owner = rs.getBytes("owner"),
            txId = rs.getBytes("tx_id"),
            blockId = rs.getBytes("block_id"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
}
