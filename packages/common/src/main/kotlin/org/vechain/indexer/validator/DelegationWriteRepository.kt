package org.vechain.indexer.validator

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `delegation.state` as a temporal table, plus the indexer's own reads of the current rows. */
@Repository
@ConditionalOnPostgres
open class DelegationWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the states block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(delegations: List<Delegation>) {
        delegations
            .groupBy { it.blockNumber }
            .toSortedMap()
            .forEach { (blockNumber, rows) -> saveBlock(blockNumber, rows) }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows.
    private fun saveBlock(blockNumber: Long, delegations: List<Delegation>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = delegations.associateBy { it.id }.values.toList()
        jdbc.batchUpdate(
            "UPDATE delegation.state SET superseded_at = ? " +
                "WHERE id = ? AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, d ->
            ps.setLong(1, blockNumber)
            ps.setBigDecimal(2, BigDecimal(d.id))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, d -> DelegationRowMapping.bind(ps, d) }
    }

    open fun findByTransitionAtBlockAndStatusIn(
        blockNumber: Long,
        statuses: List<DelegationStatus>,
    ): List<Delegation> =
        query(
            "$CURRENT AND transition_at_block = ? AND status = ANY(CAST(? AS delegation.status[])) " +
                "ORDER BY id",
            blockNumber,
            statuses.map { it.name }.toTypedArray(),
        )

    open fun findByTransitionAtBlockIsNullAndStatusIn(
        statuses: List<DelegationStatus>
    ): List<Delegation> =
        query(
            "$CURRENT AND transition_at_block IS NULL " +
                "AND status = ANY(CAST(? AS delegation.status[])) ORDER BY id",
            statuses.map { it.name }.toTypedArray(),
        )

    open fun findByTokenIdIn(tokenIds: List<String>): List<Delegation> =
        query(
            "$CURRENT AND token_id = ANY(?) ORDER BY id",
            tokenIds.map(::BigDecimal).toTypedArray(),
        )

    open fun findByValidatorIn(validators: List<String>): List<Delegation> =
        query(
            "$CURRENT AND validator = ANY(?) ORDER BY id",
            validators.map(PostgresHex::bytes).toTypedArray(),
        )

    /** The ACTIVE and EXITING delegations, which the VET-delegated series totals. */
    open fun findActive(): List<Delegation> =
        query(
            "$CURRENT AND status = ANY(CAST(? AS delegation.status[])) ORDER BY id",
            arrayOf(DelegationStatus.ACTIVE.name, DelegationStatus.EXITING.name),
        )

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM delegation.total_by_block WHERE block_number >= ?", blockNumber)
        jdbc.update("DELETE FROM delegation.state WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "UPDATE delegation.state SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE delegation.state, delegation.total_by_block")
    }

    /** The store records [before] once rows are gone and refuses any rollback below it. */
    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM delegation.state WHERE superseded_at < ?", before)

    private fun query(sql: String, vararg args: Any): List<Delegation> =
        jdbc.query(sql, { rs, _ -> DelegationRowMapping.read(rs) }, *args)

    companion object {
        private const val CURRENT = "SELECT * FROM delegation.state WHERE superseded_at IS NULL"
        private val INSERT =
            "INSERT INTO delegation.state (id, block_number, " +
                DelegationRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                DelegationRowMapping.COLUMNS.joinToString {
                    if (it == "status") "CAST(? AS delegation.status)" else "?"
                } +
                ") ON CONFLICT (id, block_number) DO UPDATE SET " +
                DelegationRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
