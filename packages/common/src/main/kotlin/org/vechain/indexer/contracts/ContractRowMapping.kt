package org.vechain.indexer.contracts

import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** A `contracts.state` row to a [Contract] and back. */
object ContractRowMapping {
    const val TABLE = "contracts.state"

    /** The columns after `(address, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "created_on",
            "deployment_tx_id",
            "deployment_clause_index",
            "master",
            "is_erc20",
            "is_erc721",
            "is_erc1155",
        )

    fun bind(ps: PreparedStatement, c: Contract) {
        ps.setBytes(1, bytes(c.address))
        ps.setLong(2, c.blockNumber)
        ps.setBytes(3, bytes(c.blockId))
        ps.setLong(4, c.blockTimestamp)
        ps.setLong(5, c.createdOn)
        ps.setBytes(6, bytes(c.deploymentTxId))
        ps.setLong(7, c.deploymentClauseIndex)
        ps.setBytes(8, bytes(c.master))
        ps.setObject(9, c.isErc20)
        ps.setObject(10, c.isErc721)
        ps.setObject(11, c.isErc1155)
    }

    fun read(rs: ResultSet): Contract =
        Contract(
            address = hex(rs.getBytes("address")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            createdOn = rs.getLong("created_on"),
            deploymentTxId = hex(rs.getBytes("deployment_tx_id")),
            deploymentClauseIndex = rs.getLong("deployment_clause_index"),
            master = hex(rs.getBytes("master")),
            isErc20 = rs.getBoolean("is_erc20").takeUnless { rs.wasNull() },
            isErc721 = rs.getBoolean("is_erc721").takeUnless { rs.wasNull() },
            isErc1155 = rs.getBoolean("is_erc1155").takeUnless { rs.wasNull() },
        )
}
