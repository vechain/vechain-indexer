package org.vechain.indexer.b3tr.navigator

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bareHex
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresText

/** A `b3tr_navigator.navigator` row to a [Navigator] and back. */
object NavigatorRowMapping {
    const val TABLE = "b3tr_navigator.navigator"
    const val STATUS_TYPE = "b3tr_navigator.status"

    /** The columns after `(address, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "status",
            "stake",
            "citizen_count",
            "total_delegated",
            "metadata_uri",
            "registered_at",
            "exit_announced_round",
            "exit_effective_deadline_block",
            "last_report_round",
            "last_report_uri",
        )

    fun bind(ps: PreparedStatement, n: Navigator) {
        ps.setBytes(1, bytes(n.address))
        ps.setLong(2, n.blockNumber)
        ps.setBytes(3, bytes(n.blockId))
        ps.setLong(4, n.blockTimestamp)
        ps.setString(5, n.status.name)
        ps.setBigDecimal(6, n.stake)
        ps.setInt(7, n.citizenCount)
        ps.setBigDecimal(8, n.totalDelegated)
        // URIs are chain strings, which can carry NUL.
        ps.setString(9, n.metadataURI?.let(PostgresText::escape))
        ps.setLong(10, n.registeredAt)
        ps.setObject(11, n.exitAnnouncedRound, Types.BIGINT)
        ps.setObject(12, n.exitEffectiveDeadlineBlock, Types.BIGINT)
        ps.setObject(13, n.lastReportRound, Types.BIGINT)
        ps.setString(14, n.lastReportURI?.let(PostgresText::escape))
    }

    fun read(rs: ResultSet): Navigator =
        Navigator(
            address = hex(rs.getBytes("address")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            status = NavigatorStatus.valueOf(rs.getString("status")),
            stake = rs.getBigDecimal("stake"),
            citizenCount = rs.getInt("citizen_count"),
            totalDelegated = rs.getBigDecimal("total_delegated"),
            metadataURI = rs.getString("metadata_uri")?.let(PostgresText::unescape),
            registeredAt = rs.getLong("registered_at"),
            exitAnnouncedRound = rs.getObject("exit_announced_round") as? Long,
            exitEffectiveDeadlineBlock = rs.getObject("exit_effective_deadline_block") as? Long,
            lastReportRound = rs.getObject("last_report_round") as? Long,
            lastReportURI = rs.getString("last_report_uri")?.let(PostgresText::unescape),
        )
}

/** A `b3tr_navigator.citizen` row to a [NavigatorCitizen] and back. */
object NavigatorCitizenRowMapping {
    const val TABLE = "b3tr_navigator.citizen"

    /** The columns after `(address, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf("block_id", "block_timestamp", "navigator", "amount", "delegated_at", "active")

    fun bind(ps: PreparedStatement, c: NavigatorCitizen) {
        ps.setBytes(1, bytes(c.address))
        ps.setLong(2, c.blockNumber)
        ps.setBytes(3, bytes(c.blockId))
        ps.setLong(4, c.blockTimestamp)
        ps.setBytes(5, bytes(c.navigator))
        ps.setBigDecimal(6, c.amount)
        ps.setLong(7, c.delegatedAt)
        ps.setBoolean(8, c.active)
    }

    fun read(rs: ResultSet): NavigatorCitizen =
        NavigatorCitizen(
            address = hex(rs.getBytes("address")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            navigator = hex(rs.getBytes("navigator")),
            amount = rs.getBigDecimal("amount"),
            delegatedAt = rs.getLong("delegated_at"),
            active = rs.getBoolean("active"),
        )
}

/** A `b3tr_navigator.delegation_event` row to a [NavigatorDelegationEvent] and back. */
object NavigatorDelegationEventRowMapping {
    const val TABLE = "b3tr_navigator.delegation_event"

    /** The columns after `id`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_number",
            "block_id",
            "block_timestamp",
            "tx_id",
            "navigator",
            "citizen",
            "event_type",
            "amount",
            "delta",
        )

    fun bind(ps: PreparedStatement, e: NavigatorDelegationEvent) {
        ps.setBytes(1, bytes(e.id))
        ps.setLong(2, e.blockNumber)
        ps.setBytes(3, bytes(e.blockId))
        ps.setLong(4, e.blockTimestamp)
        ps.setBytes(5, bytes(e.txId))
        ps.setBytes(6, bytes(e.navigator))
        ps.setBytes(7, bytes(e.citizen))
        ps.setString(8, e.eventType)
        ps.setBigDecimal(9, e.amount)
        ps.setBigDecimal(10, e.delta)
    }

    fun read(rs: ResultSet): NavigatorDelegationEvent =
        NavigatorDelegationEvent(
            id = bareHex(rs.getBytes("id")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            txId = hex(rs.getBytes("tx_id")),
            navigator = hex(rs.getBytes("navigator")),
            citizen = hex(rs.getBytes("citizen")),
            eventType = rs.getString("event_type"),
            amount = rs.getBigDecimal("amount"),
            delta = rs.getBigDecimal("delta"),
        )
}

/** A `b3tr_navigator.fee` row to a [NavigatorFee] and back. */
object NavigatorFeeRowMapping {
    const val TABLE = "b3tr_navigator.fee"

    /** The columns after `(navigator, round_id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "total_deposited",
            "claimed_amount",
            "claimed_at",
            "deposited_at",
            "unlock_round",
        )

    fun bind(ps: PreparedStatement, f: NavigatorFee) {
        ps.setBytes(1, bytes(f.navigator))
        ps.setInt(2, f.roundId)
        ps.setLong(3, f.blockNumber)
        ps.setBytes(4, bytes(f.blockId))
        ps.setLong(5, f.blockTimestamp)
        ps.setBigDecimal(6, f.totalDeposited)
        ps.setBigDecimal(7, f.claimedAmount)
        ps.setObject(8, f.claimedAt, Types.BIGINT)
        ps.setLong(9, f.depositedAt)
        ps.setLong(10, f.unlockRound)
    }

    fun read(rs: ResultSet): NavigatorFee =
        NavigatorFee(
            navigator = hex(rs.getBytes("navigator")),
            roundId = rs.getInt("round_id"),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            totalDeposited = rs.getBigDecimal("total_deposited"),
            claimedAmount = rs.getBigDecimal("claimed_amount"),
            claimedAt = rs.getObject("claimed_at") as? Long,
            depositedAt = rs.getLong("deposited_at"),
            unlockRound = rs.getLong("unlock_round"),
        )
}
