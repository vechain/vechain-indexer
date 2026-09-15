package org.vechain.indexer.accounts

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex

/** An `accounts.overview` row to an [AccountOverview] and back. */
object AccountOverviewRowMapping {
    const val TABLE = "accounts.overview"

    /** The columns after `(address, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "first_seen",
            "last_seen",
            "transactions_sent",
            "clauses_sent",
            "vtho_burned",
            "vtho_delegated",
            "gas_used",
            "vet_sent",
            "vet_received",
            "vet_balance",
            "vtho_block_rewards",
            "vtho_passive_generation",
            "last_vtho_settlement",
        )

    fun bind(ps: PreparedStatement, a: AccountOverview) {
        ps.setBytes(1, bytes(a.address))
        ps.setLong(2, a.blockNumber)
        ps.setBytes(3, bytes(a.blockId))
        ps.setLong(4, a.blockTimestamp)
        ps.setLong(5, a.firstSeen)
        ps.setLong(6, a.lastSeen)
        ps.setLong(7, a.transactionsSent)
        ps.setLong(8, a.clausesSent)
        ps.setBigDecimal(9, a.vthoBurned.toBigDecimal())
        ps.setBigDecimal(10, a.vthoDelegated.toBigDecimal())
        ps.setBigDecimal(11, a.gasUsed.toBigDecimal())
        ps.setBigDecimal(12, a.vetSent.toBigDecimal())
        ps.setBigDecimal(13, a.vetReceived.toBigDecimal())
        ps.setBigDecimal(14, a.vetBalance.toBigDecimal())
        ps.setBigDecimal(15, a.vthoBlockRewards.toBigDecimal())
        ps.setBigDecimal(16, a.vthoPassiveGeneration.toBigDecimal())
        ps.setObject(17, a.lastVthoSettlement, Types.BIGINT)
    }

    fun read(rs: ResultSet): AccountOverview =
        AccountOverview(
            address = hex(rs.getBytes("address")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            firstSeen = rs.getLong("first_seen"),
            lastSeen = rs.getLong("last_seen"),
            transactionsSent = rs.getLong("transactions_sent"),
            clausesSent = rs.getLong("clauses_sent"),
            vthoBurned = rs.getBigDecimal("vtho_burned").toBigIntegerExact(),
            vthoDelegated = rs.getBigDecimal("vtho_delegated").toBigIntegerExact(),
            gasUsed = rs.getBigDecimal("gas_used").toBigIntegerExact(),
            vetSent = rs.getBigDecimal("vet_sent").toBigIntegerExact(),
            vetReceived = rs.getBigDecimal("vet_received").toBigIntegerExact(),
            vetBalance = rs.getBigDecimal("vet_balance").toBigIntegerExact(),
            vthoBlockRewards = rs.getBigDecimal("vtho_block_rewards").toBigIntegerExact(),
            vthoPassiveGeneration = rs.getBigDecimal("vtho_passive_generation").toBigIntegerExact(),
            lastVthoSettlement = rs.getLong("last_vtho_settlement").takeUnless { rs.wasNull() },
        )
}

/** An `accounts.vet_balance` row to a [VetBalance] and back. */
object VetBalanceRowMapping {
    const val TABLE = "accounts.vet_balance"

    /** The columns after `(address, block_timestamp)`, in the order [bind] sets them. */
    val COLUMNS = listOf("block_number", "block_id", "balance")

    fun bind(ps: PreparedStatement, b: VetBalance) {
        ps.setBytes(1, bytes(b.address))
        ps.setLong(2, b.blockTimestamp)
        ps.setLong(3, b.blockNumber)
        ps.setBytes(4, bytes(b.blockId))
        ps.setBigDecimal(5, b.balance.toBigDecimal())
    }

    fun read(rs: ResultSet): VetBalance =
        VetBalance(
            address = hex(rs.getBytes("address")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            balance = rs.getBigDecimal("balance").toBigIntegerExact(),
        )
}

/** An `accounts.totals` row to an [AccountTotalsSeries] and back. */
object AccountTotalsRowMapping {
    const val TABLE = "accounts.totals"
    const val FRAME_TYPE = "accounts.time_frame"

    /** The columns after `block_number`, in the order [bind] sets them. */
    val COLUMNS = listOf("block_id", "block_timestamp", "total_accounts", "time_frames")

    fun bind(ps: PreparedStatement, t: AccountTotalsSeries) {
        ps.setLong(1, t.blockNumber)
        ps.setBytes(2, bytes(t.blockId))
        ps.setLong(3, t.blockTimestamp)
        ps.setLong(4, t.totalAccounts)
        ps.setArray(
            5,
            ps.connection.createArrayOf(FRAME_TYPE, t.timeFrames.map { it.name }.toTypedArray()),
        )
    }

    fun read(rs: ResultSet): AccountTotalsSeries =
        AccountTotalsSeries(
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            totalAccounts = rs.getLong("total_accounts"),
            timeFrames =
                (rs.getArray("time_frames").array as Array<*>).map {
                    TimeFrame.valueOf(it as String)
                },
        )
}
