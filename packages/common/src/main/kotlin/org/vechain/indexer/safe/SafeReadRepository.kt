package org.vechain.indexer.safe

import java.math.BigInteger
import java.sql.ResultSet
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Sort.Direction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresText

/** The current rows behind the `/safes` endpoints, each read in one query. */
@Repository
@ConditionalOnPostgres
open class SafeReadRepository(@Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate) {

    /** An owner's Safes, narrowed to the memberships that are current or that have ended. */
    open fun findMembershipsByOwner(
        owner: String,
        scope: SafeMembershipScope,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<SafeMembership> {
        val filter =
            when (scope) {
                SafeMembershipScope.ALL -> ""
                SafeMembershipScope.CURRENT -> "AND removed_block IS NULL"
                SafeMembershipScope.PAST -> "AND removed_block IS NOT NULL"
            }
        val order = direction.name
        return jdbc.query(
            "SELECT * FROM ${SafeMembershipRowMapping.TABLE} " +
                "WHERE superseded_at IS NULL AND owner = ? $filter " +
                "ORDER BY added_block $order, safe $order OFFSET ? LIMIT ?",
            { rs, _ -> SafeMembershipRowMapping.read(rs) },
            bytes(owner),
            offset,
            limit,
        )
    }

    /** A Safe's proposed transactions, newest first, with the subcalls of each batch. */
    open fun findProposalsBySafe(
        safe: String,
        offset: Long,
        limit: Int,
        direction: Direction,
    ): List<SafeTxProposal> {
        val order = direction.name
        return jdbc.query(
            "$PROPOSAL_SELECT WHERE p.superseded_at IS NULL AND p.safe = ? " +
                "ORDER BY p.block_number $order, p.tx_hash $order OFFSET ? LIMIT ?",
            { rs, _ -> SafeTxProposalRowMapping.read(rs, subcalls(rs)) },
            bytes(safe),
            offset,
            limit,
        )
    }

    /** One transaction's approvals and execution status. */
    open fun findTxState(safe: String, txHash: String): SafeTxState? =
        jdbc
            .query(
                "$TX_STATE_SELECT WHERE s.superseded_at IS NULL AND s.safe = ? AND s.tx_hash = ?",
                { rs, _ -> SafeTxStateRowMapping.read(rs, approvals(rs)) },
                bytes(safe),
                bytes(txHash),
            )
            .firstOrNull()

    /** A Safe's events outlive its deployment, so the head is the newest of the four tables. */
    open fun latestBlockNumber(): Long =
        jdbc.queryForObject(
            listOf(
                    SafeProxyRowMapping.TABLE,
                    SafeMembershipRowMapping.TABLE,
                    SafeTxStateRowMapping.TABLE,
                    SafeTxProposalRowMapping.TABLE,
                )
                .joinToString(
                    prefix = "SELECT GREATEST(",
                    postfix = ")",
                    transform = { "(SELECT coalesce(max(block_number), 0) FROM $it)" },
                ),
            Long::class.java,
        )!!

    private fun approvals(rs: ResultSet): List<SafeTxApproval> {
        val owners = hexes(rs, "approver_owners") ?: return emptyList()
        val blocks = longs(rs, "approver_blocks")
        val timestamps = longs(rs, "approver_timestamps")
        val txIds = hexes(rs, "approver_tx_ids")
        return owners.mapIndexed { index, owner ->
            SafeTxApproval(
                owner = owner,
                block = blocks?.getOrNull(index) ?: 0L,
                blockTimestamp = timestamps?.getOrNull(index) ?: 0L,
                vechainTxId = txIds?.getOrNull(index) ?: "",
            )
        }
    }

    private fun subcalls(rs: ResultSet): List<SafeSubcall> {
        val targets = hexes(rs, "subcall_targets") ?: return emptyList()
        val values = numbers(rs, "subcall_values")
        val datas = strings(rs, "subcall_datas")
        val operations = longs(rs, "subcall_operations")
        val labels = strings(rs, "subcall_labels")
        return targets.mapIndexed { index, target ->
            SafeSubcall(
                target = target,
                value = values?.getOrNull(index) ?: BigInteger.ZERO,
                data = datas?.getOrNull(index) ?: "",
                operation = operations?.getOrNull(index)?.toInt() ?: 0,
                label = labels?.getOrNull(index) ?: "",
            )
        }
    }

    private fun hexes(rs: ResultSet, column: String): List<String>? =
        (rs.getArray(column)?.array as? Array<*>)?.mapNotNull { (it as? ByteArray)?.let(::hex) }

    private fun longs(rs: ResultSet, column: String): List<Long>? =
        (rs.getArray(column)?.array as? Array<*>)?.map { (it as? Number)?.toLong() ?: 0L }

    private fun numbers(rs: ResultSet, column: String): List<BigInteger>? =
        (rs.getArray(column)?.array as? Array<*>)?.map {
            (it as? java.math.BigDecimal)?.toBigIntegerExact() ?: BigInteger.ZERO
        }

    private fun strings(rs: ResultSet, column: String): List<String>? =
        (rs.getArray(column)?.array as? Array<*>)?.map {
            PostgresText.unescape(it as? String ?: "")
        }

    companion object {
        private val TX_STATE_SELECT =
            """
            SELECT s.*, a.approver_owners, a.approver_blocks, a.approver_timestamps,
                   a.approver_tx_ids
            FROM ${SafeTxStateRowMapping.TABLE} s
            LEFT JOIN LATERAL (
                SELECT array_agg(owner ORDER BY block_number, owner) AS approver_owners,
                       array_agg(block_number ORDER BY block_number, owner) AS approver_blocks,
                       array_agg(block_timestamp ORDER BY block_number, owner)
                           AS approver_timestamps,
                       array_agg(vechain_tx_id ORDER BY block_number, owner) AS approver_tx_ids
                FROM ${SafeTxStateRowMapping.APPROVAL_TABLE} ap
                WHERE ap.safe = s.safe AND ap.tx_hash = s.tx_hash
            ) a ON TRUE
            """
                .trimIndent()

        private val PROPOSAL_SELECT =
            """
            SELECT p.*, c.subcall_targets, c.subcall_values, c.subcall_datas,
                   c.subcall_operations, c.subcall_labels
            FROM ${SafeTxProposalRowMapping.TABLE} p
            LEFT JOIN LATERAL (
                SELECT array_agg(target ORDER BY position) AS subcall_targets,
                       array_agg(value ORDER BY position) AS subcall_values,
                       array_agg(data ORDER BY position) AS subcall_datas,
                       array_agg(operation ORDER BY position) AS subcall_operations,
                       array_agg(label ORDER BY position) AS subcall_labels
                FROM ${SafeTxProposalRowMapping.SUBCALL_TABLE} sc
                WHERE sc.safe = p.safe AND sc.tx_hash = p.tx_hash
            ) c ON TRUE
            """
                .trimIndent()
    }
}
