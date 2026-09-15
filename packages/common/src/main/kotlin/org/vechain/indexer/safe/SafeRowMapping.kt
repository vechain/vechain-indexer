package org.vechain.indexer.safe

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull
import org.vechain.indexer.postgres.PostgresText

/** A `safe.proxy` row to a [SafeProxy] and back. */
object SafeProxyRowMapping {
    const val TABLE = "safe.proxy"

    /** The columns after `address`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "singleton",
            "created_block",
            "created_timestamp",
            "vechain_tx_id",
            "block_number",
            "block_id",
            "block_timestamp",
        )

    fun bind(ps: PreparedStatement, proxy: SafeProxy) {
        ps.setBytes(1, bytes(proxy.address))
        ps.setBytes(2, bytes(proxy.singleton))
        ps.setLong(3, proxy.createdBlock)
        ps.setLong(4, proxy.createdTimestamp)
        ps.setBytes(5, bytes(proxy.vechainTxId))
        ps.setLong(6, proxy.blockNumber)
        ps.setBytes(7, bytes(proxy.blockId))
        ps.setLong(8, proxy.blockTimestamp)
    }

    fun read(rs: ResultSet): SafeProxy =
        SafeProxy(
            address = hex(rs.getBytes("address")),
            singleton = hex(rs.getBytes("singleton")),
            createdBlock = rs.getLong("created_block"),
            createdTimestamp = rs.getLong("created_timestamp"),
            vechainTxId = hex(rs.getBytes("vechain_tx_id")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
}

/** A `safe.membership` row to a [SafeMembership] and back. */
object SafeMembershipRowMapping {
    const val TABLE = "safe.membership"

    /** The columns after `(safe, owner, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "added_block",
            "added_timestamp",
            "removed_block",
            "removed_timestamp",
        )

    fun bind(ps: PreparedStatement, membership: SafeMembership) {
        ps.setBytes(1, bytes(membership.safe))
        ps.setBytes(2, bytes(membership.owner))
        ps.setLong(3, membership.blockNumber)
        ps.setBytes(4, bytes(membership.blockId))
        ps.setLong(5, membership.blockTimestamp)
        ps.setLong(6, membership.addedBlock)
        ps.setLong(7, membership.addedTimestamp)
        ps.setObject(8, membership.removedBlock, Types.BIGINT)
        ps.setObject(9, membership.removedTimestamp, Types.BIGINT)
    }

    fun read(rs: ResultSet): SafeMembership {
        val safe = hex(rs.getBytes("safe"))
        val owner = hex(rs.getBytes("owner"))
        return SafeMembership(
            id = SafeMembership.buildId(safe, owner),
            safe = safe,
            owner = owner,
            addedBlock = rs.getLong("added_block"),
            addedTimestamp = rs.getLong("added_timestamp"),
            removedBlock = rs.getObject("removed_block") as? Long,
            removedTimestamp = rs.getObject("removed_timestamp") as? Long,
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
    }
}

/** A `safe.tx_state` row, with its approvals, to a [SafeTxState] and back. */
object SafeTxStateRowMapping {
    const val TABLE = "safe.tx_state"
    const val APPROVAL_TABLE = "safe.tx_approval"

    /** The columns after `(safe, tx_hash, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "executed",
            "failed",
            "executor",
            "executed_block",
            "executed_timestamp",
            "vechain_tx_id",
        )

    fun bind(ps: PreparedStatement, state: SafeTxState) {
        ps.setBytes(1, bytes(state.safe))
        ps.setBytes(2, bytes(state.txHash))
        ps.setLong(3, state.blockNumber)
        ps.setBytes(4, bytes(state.blockId))
        ps.setLong(5, state.blockTimestamp)
        ps.setBoolean(6, state.executed)
        ps.setBoolean(7, state.failed)
        ps.setBytes(8, bytesOrNull(state.executor))
        ps.setObject(9, state.executedBlock, Types.BIGINT)
        ps.setObject(10, state.executedTimestamp, Types.BIGINT)
        ps.setBytes(11, bytesOrNull(state.vechainTxId))
    }

    fun bindApproval(ps: PreparedStatement, safe: String, txHash: String, a: SafeTxApproval) {
        ps.setBytes(1, bytes(safe))
        ps.setBytes(2, bytes(txHash))
        ps.setBytes(3, bytes(a.owner))
        ps.setLong(4, a.block)
        ps.setLong(5, a.blockTimestamp)
        ps.setBytes(6, bytes(a.vechainTxId))
    }

    /** Reads the state row; [approvals] is the aggregate the reader joined in, oldest first. */
    fun read(rs: ResultSet, approvals: List<SafeTxApproval> = emptyList()): SafeTxState {
        val safe = hex(rs.getBytes("safe"))
        val txHash = hex(rs.getBytes("tx_hash"))
        return SafeTxState(
            id = SafeTxState.buildId(safe, txHash),
            safe = safe,
            txHash = txHash,
            approvers = approvals,
            executed = rs.getBoolean("executed"),
            failed = rs.getBoolean("failed"),
            executor = hexOrNull(rs.getBytes("executor")),
            executedBlock = rs.getObject("executed_block") as? Long,
            executedTimestamp = rs.getObject("executed_timestamp") as? Long,
            vechainTxId = hexOrNull(rs.getBytes("vechain_tx_id")),
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
    }

    fun readApproval(rs: ResultSet): SafeTxApproval =
        SafeTxApproval(
            owner = hex(rs.getBytes("owner")),
            block = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            vechainTxId = hex(rs.getBytes("vechain_tx_id")),
        )
}

/** A `safe.tx_proposal` row, with its subcalls, to a [SafeTxProposal] and back. */
object SafeTxProposalRowMapping {
    const val TABLE = "safe.tx_proposal"
    const val SUBCALL_TABLE = "safe.tx_subcall"

    /** The columns after `(safe, tx_hash, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "proposer",
            "proposed_block",
            "proposed_timestamp",
            "proposed_vechain_tx_id",
            "\"to\"",
            "value",
            "data",
            "operation",
            "nonce",
            "description",
            "safe_tx_gas",
            "base_gas",
            "gas_price",
            "gas_token",
            "refund_receiver",
        )

    fun bind(ps: PreparedStatement, proposal: SafeTxProposal) {
        ps.setBytes(1, bytes(proposal.safe))
        ps.setBytes(2, bytes(proposal.txHash))
        ps.setLong(3, proposal.blockNumber)
        ps.setBytes(4, bytes(proposal.blockId))
        ps.setLong(5, proposal.blockTimestamp)
        ps.setBytes(6, bytesOrNull(proposal.proposer))
        ps.setObject(7, proposal.proposedBlock, Types.BIGINT)
        ps.setObject(8, proposal.proposedTimestamp, Types.BIGINT)
        ps.setBytes(9, bytesOrNull(proposal.proposedVechainTxId))
        ps.setBytes(10, bytesOrNull(proposal.to))
        ps.setObject(11, proposal.value?.let(::BigDecimal), Types.NUMERIC)
        // Call data and a proposer's description are chain strings, which can carry NUL.
        ps.setString(12, proposal.data?.let(PostgresText::escape))
        ps.setObject(13, proposal.operation, Types.INTEGER)
        ps.setObject(14, proposal.nonce?.let(::BigDecimal), Types.NUMERIC)
        ps.setString(15, proposal.description?.let(PostgresText::escape))
        ps.setObject(16, proposal.safeTxGas?.let(::BigDecimal), Types.NUMERIC)
        ps.setObject(17, proposal.baseGas?.let(::BigDecimal), Types.NUMERIC)
        ps.setObject(18, proposal.gasPrice?.let(::BigDecimal), Types.NUMERIC)
        ps.setBytes(19, bytesOrNull(proposal.gasToken))
        ps.setBytes(20, bytesOrNull(proposal.refundReceiver))
    }

    fun bindSubcall(
        ps: PreparedStatement,
        safe: String,
        txHash: String,
        blockNumber: Long,
        position: Int,
        subcall: SafeSubcall,
    ) {
        ps.setBytes(1, bytes(safe))
        ps.setBytes(2, bytes(txHash))
        ps.setInt(3, position)
        ps.setLong(4, blockNumber)
        ps.setBytes(5, bytes(subcall.target))
        ps.setBigDecimal(6, BigDecimal(subcall.value))
        ps.setString(7, PostgresText.escape(subcall.data))
        ps.setInt(8, subcall.operation)
        ps.setString(9, PostgresText.escape(subcall.label))
    }

    /** Reads the proposal row; [subcalls] is the aggregate the reader joined in, in order. */
    fun read(rs: ResultSet, subcalls: List<SafeSubcall>? = null): SafeTxProposal {
        val safe = hex(rs.getBytes("safe"))
        val txHash = hex(rs.getBytes("tx_hash"))
        return SafeTxProposal(
            id = SafeTxProposal.buildId(safe, txHash),
            safe = safe,
            txHash = txHash,
            proposer = hexOrNull(rs.getBytes("proposer")),
            proposedBlock = rs.getObject("proposed_block") as? Long,
            proposedTimestamp = rs.getObject("proposed_timestamp") as? Long,
            proposedVechainTxId = hexOrNull(rs.getBytes("proposed_vechain_tx_id")),
            to = hexOrNull(rs.getBytes("to")),
            value = rs.getBigDecimal("value")?.toBigIntegerExact(),
            data = rs.getString("data")?.let(PostgresText::unescape),
            operation = rs.getObject("operation") as? Int,
            nonce = rs.getBigDecimal("nonce")?.toBigIntegerExact(),
            description = rs.getString("description")?.let(PostgresText::unescape),
            safeTxGas = rs.getBigDecimal("safe_tx_gas")?.toBigIntegerExact(),
            baseGas = rs.getBigDecimal("base_gas")?.toBigIntegerExact(),
            gasPrice = rs.getBigDecimal("gas_price")?.toBigIntegerExact(),
            gasToken = hexOrNull(rs.getBytes("gas_token")),
            refundReceiver = hexOrNull(rs.getBytes("refund_receiver")),
            subcalls = subcalls?.takeIf { it.isNotEmpty() },
            blockId = hex(rs.getBytes("block_id")),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
        )
    }

    fun readSubcall(rs: ResultSet): SafeSubcall =
        SafeSubcall(
            target = hex(rs.getBytes("target")),
            value = rs.getBigDecimal("value").toBigIntegerExact(),
            data = PostgresText.unescape(rs.getString("data")),
            operation = rs.getInt("operation"),
            label = PostgresText.unescape(rs.getString("label")),
        )
}
