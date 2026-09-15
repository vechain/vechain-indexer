package org.vechain.indexer.safe

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** Everything one entry adds to the schema, in the order the foreign keys need it. */
data class SafeUpdate(
    val proxies: List<SafeProxy> = emptyList(),
    val memberships: List<SafeMembership> = emptyList(),
    val txStates: List<SafeTxState> = emptyList(),
    val proposals: List<SafeTxProposal> = emptyList(),
) {
    fun isEmpty(): Boolean =
        proxies.isEmpty() && memberships.isEmpty() && txStates.isEmpty() && proposals.isEmpty()
}

/** The `safe` schema: the Safes, their owners, and their transactions' state and proposals. */
@Repository
@ConditionalOnPostgres
open class SafeWriteRepository(@Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate) :
    PostgresIndexerTables {

    /** One transaction, so a Safe and the first thing it did cannot be split by a rollback. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(update: SafeUpdate) {
        saveProxies(update.proxies)
        update.memberships.groupBy { it.blockNumber }.toSortedMap().forEach(::saveMemberships)
        update.txStates.groupBy { it.blockNumber }.toSortedMap().forEach(::saveTxStates)
        update.proposals.groupBy { it.blockNumber }.toSortedMap().forEach(::saveProposals)
    }

    private fun saveProxies(proxies: List<SafeProxy>) {
        if (proxies.isEmpty()) return
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = proxies.associateBy { it.address }.values.toList()
        jdbc.batchUpdate(PROXY_INSERT, rows, rows.size) { ps, p -> SafeProxyRowMapping.bind(ps, p) }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows; upsert does the rest.
    private fun saveMemberships(blockNumber: Long, block: List<SafeMembership>) {
        val rows = block.associateBy { it.safe to it.owner }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $MEMBERSHIP_TABLE SET superseded_at = ? WHERE safe = ? AND owner = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, m ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(m.safe))
            ps.setBytes(3, bytes(m.owner))
            ps.setLong(4, blockNumber)
        }
        jdbc.batchUpdate(MEMBERSHIP_INSERT, rows, rows.size) { ps, m ->
            SafeMembershipRowMapping.bind(ps, m)
        }
    }

    private fun saveTxStates(blockNumber: Long, block: List<SafeTxState>) {
        val rows = block.associateBy { it.safe to it.txHash }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $TX_STATE_TABLE SET superseded_at = ? WHERE safe = ? AND tx_hash = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, s ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(s.safe))
            ps.setBytes(3, bytes(s.txHash))
            ps.setLong(4, blockNumber)
        }
        jdbc.batchUpdate(TX_STATE_INSERT, rows, rows.size) { ps, s ->
            SafeTxStateRowMapping.bind(ps, s)
        }
        // An owner approves once; the first approval stands, as the appended list did.
        val approvals = rows.flatMap { state -> state.approvers.map { state to it } }
        if (approvals.isEmpty()) return
        jdbc.batchUpdate(APPROVAL_INSERT, approvals, approvals.size) { ps, (state, approval) ->
            SafeTxStateRowMapping.bindApproval(ps, state.safe, state.txHash, approval)
        }
    }

    private fun saveProposals(blockNumber: Long, block: List<SafeTxProposal>) {
        val rows = block.associateBy { it.safe to it.txHash }.values.toList()
        jdbc.batchUpdate(
            "UPDATE $PROPOSAL_TABLE SET superseded_at = ? WHERE safe = ? AND tx_hash = ? " +
                "AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, p ->
            ps.setLong(1, blockNumber)
            ps.setBytes(2, bytes(p.safe))
            ps.setBytes(3, bytes(p.txHash))
            ps.setLong(4, blockNumber)
        }
        jdbc.batchUpdate(PROPOSAL_INSERT, rows, rows.size) { ps, p ->
            SafeTxProposalRowMapping.bind(ps, p)
        }
        val subcalls = rows.flatMap { proposal ->
            proposal.subcalls.orEmpty().mapIndexed { index, subcall ->
                Triple(proposal, index + 1, subcall)
            }
        }
        if (subcalls.isEmpty()) return
        jdbc.batchUpdate(SUBCALL_INSERT, subcalls, subcalls.size) { ps, (proposal, position, sub) ->
            SafeTxProposalRowMapping.bindSubcall(
                ps,
                proposal.safe,
                proposal.txHash,
                blockNumber,
                position,
                sub,
            )
        }
    }

    /** Which of [addresses] the factory deployed; everything else is not a Safe. */
    open fun knownSafes(addresses: Set<String>): Set<String> =
        if (addresses.isEmpty()) emptySet()
        else
            jdbc
                .query(
                    "SELECT address FROM $PROXY_TABLE WHERE address = ANY(?)",
                    { rs, _ -> hex(rs.getBytes("address")) },
                    addresses.map(::bytes).toTypedArray(),
                )
                .toSet()

    /** The current row of every (safe, owner) pair the batch touches. */
    open fun findCurrentMemberships(keys: Set<Pair<String, String>>): List<SafeMembership> =
        findCurrent(MEMBERSHIP_TABLE, "owner", keys) { SafeMembershipRowMapping.read(it) }

    /** The current row of every (safe, txHash) pair the batch touches, approvals included. */
    open fun findCurrentTxStates(keys: Set<Pair<String, String>>): List<SafeTxState> {
        val states = findCurrent(TX_STATE_TABLE, "tx_hash", keys) { SafeTxStateRowMapping.read(it) }
        if (states.isEmpty()) return states
        val approvals =
            jdbc
                .query(
                    "SELECT * FROM $APPROVAL_TABLE WHERE safe = ANY(?) AND tx_hash = ANY(?) " +
                        "ORDER BY block_number, owner",
                    { rs, _ ->
                        (hex(rs.getBytes("safe")) to hex(rs.getBytes("tx_hash"))) to
                            SafeTxStateRowMapping.readApproval(rs)
                    },
                    states.map { bytes(it.safe) }.toTypedArray(),
                    states.map { bytes(it.txHash) }.toTypedArray(),
                )
                .groupBy({ it.first }, { it.second })
        return states.map { it.copy(approvers = approvals[it.safe to it.txHash].orEmpty()) }
    }

    /** The current row of every (safe, txHash) pair the batch touches, subcalls included. */
    open fun findCurrentProposals(keys: Set<Pair<String, String>>): List<SafeTxProposal> {
        val proposals =
            findCurrent(PROPOSAL_TABLE, "tx_hash", keys) { SafeTxProposalRowMapping.read(it) }
        if (proposals.isEmpty()) return proposals
        val subcalls =
            jdbc
                .query(
                    "SELECT * FROM $SUBCALL_TABLE WHERE safe = ANY(?) AND tx_hash = ANY(?) " +
                        "ORDER BY position",
                    { rs, _ ->
                        (hex(rs.getBytes("safe")) to hex(rs.getBytes("tx_hash"))) to
                            SafeTxProposalRowMapping.readSubcall(rs)
                    },
                    proposals.map { bytes(it.safe) }.toTypedArray(),
                    proposals.map { bytes(it.txHash) }.toTypedArray(),
                )
                .groupBy({ it.first }, { it.second })
        return proposals.map {
            it.copy(subcalls = subcalls[it.safe to it.txHash]?.takeIf { s -> s.isNotEmpty() })
        }
    }

    /**
     * The query widens to the cross product of the two halves of the key, whose extra rows the
     * caller simply never looks up.
     */
    private fun <T> findCurrent(
        table: String,
        second: String,
        keys: Set<Pair<String, String>>,
        read: (java.sql.ResultSet) -> T,
    ): List<T> =
        if (keys.isEmpty()) emptyList()
        else
            jdbc.query(
                "SELECT * FROM $table WHERE safe = ANY(?) AND $second = ANY(?) " +
                    "AND superseded_at IS NULL",
                { rs, _ -> read(rs) },
                keys.map { bytes(it.first) }.distinct().toTypedArray(),
                keys.map { bytes(it.second) }.distinct().toTypedArray(),
            )

    override fun rollbackFrom(blockNumber: Long) {
        // A proxy's delete cascades to every row of the Safe it created.
        jdbc.update("DELETE FROM $PROXY_TABLE WHERE block_number >= ?", blockNumber)
        listOf(MEMBERSHIP_TABLE, TX_STATE_TABLE, PROPOSAL_TABLE).forEach { table ->
            jdbc.update("DELETE FROM $table WHERE block_number >= ?", blockNumber)
            jdbc.update(
                "UPDATE $table SET superseded_at = NULL WHERE superseded_at >= ?",
                blockNumber,
            )
        }
        jdbc.update("DELETE FROM $APPROVAL_TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update("DELETE FROM $SUBCALL_TABLE WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute(
            "TRUNCATE $PROXY_TABLE, $MEMBERSHIP_TABLE, $TX_STATE_TABLE, $APPROVAL_TABLE, " +
                "$PROPOSAL_TABLE, $SUBCALL_TABLE"
        )
    }

    override fun prune(before: Long): Int =
        listOf(MEMBERSHIP_TABLE, TX_STATE_TABLE, PROPOSAL_TABLE).sumOf { table ->
            jdbc.update("DELETE FROM $table WHERE superseded_at < ?", before)
        }

    companion object {
        private const val PROXY_TABLE = SafeProxyRowMapping.TABLE
        private const val MEMBERSHIP_TABLE = SafeMembershipRowMapping.TABLE
        private const val TX_STATE_TABLE = SafeTxStateRowMapping.TABLE
        private const val APPROVAL_TABLE = SafeTxStateRowMapping.APPROVAL_TABLE
        private const val PROPOSAL_TABLE = SafeTxProposalRowMapping.TABLE
        private const val SUBCALL_TABLE = SafeTxProposalRowMapping.SUBCALL_TABLE

        private fun upsert(table: String, key: List<String>, columns: List<String>) =
            "INSERT INTO $table (" +
                (key + columns).joinToString() +
                ") VALUES (" +
                (key + columns).joinToString { "?" } +
                ") ON CONFLICT (" +
                key.joinToString() +
                ") DO UPDATE SET " +
                columns.joinToString { "$it = EXCLUDED.$it" }

        private val PROXY_INSERT =
            upsert(PROXY_TABLE, listOf("address"), SafeProxyRowMapping.COLUMNS)

        private val MEMBERSHIP_INSERT =
            upsert(
                MEMBERSHIP_TABLE,
                listOf("safe", "owner", "block_number"),
                SafeMembershipRowMapping.COLUMNS,
            )

        private val TX_STATE_INSERT =
            upsert(
                TX_STATE_TABLE,
                listOf("safe", "tx_hash", "block_number"),
                SafeTxStateRowMapping.COLUMNS,
            )

        private val PROPOSAL_INSERT =
            upsert(
                PROPOSAL_TABLE,
                listOf("safe", "tx_hash", "block_number"),
                SafeTxProposalRowMapping.COLUMNS,
            )

        private const val APPROVAL_INSERT =
            "INSERT INTO $APPROVAL_TABLE (safe, tx_hash, owner, block_number, block_timestamp, " +
                "vechain_tx_id) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING"

        // A carried-forward subcall keeps the block it arrived in, so a rollback cannot orphan it.
        private const val SUBCALL_INSERT =
            "INSERT INTO $SUBCALL_TABLE AS s (safe, tx_hash, position, block_number, target, " +
                "value, data, operation, label) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (safe, tx_hash, position) DO UPDATE SET " +
                "block_number = LEAST(s.block_number, EXCLUDED.block_number), " +
                "target = EXCLUDED.target, " +
                "value = EXCLUDED.value, data = EXCLUDED.data, " +
                "operation = EXCLUDED.operation, label = EXCLUDED.label"
    }
}
