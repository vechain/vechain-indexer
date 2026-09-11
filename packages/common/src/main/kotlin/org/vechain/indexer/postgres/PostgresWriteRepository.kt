package org.vechain.indexer.postgres

import java.sql.PreparedStatement
import java.sql.Types
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.blocks.IndexedBlock
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.transaction.IndexedTransaction

/** One transaction per block; rollback cascades from the block table; resync truncates. */
@Repository
@ConditionalOnPostgres
open class PostgresWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    open fun lastSynced(): BlockIdentifier? =
        jdbc
            .query("SELECT number, id FROM block ORDER BY number DESC LIMIT 1") { rs, _ ->
                BlockIdentifier(rs.getLong("number"), PostgresHex.hex(rs.getBytes("id")))
            }
            .firstOrNull()

    /** Totals on the newest block, the base for the next block's running totals. */
    open fun newestTotals(): BlockTotals? =
        jdbc
            .query(
                "SELECT total_transactions, total_clauses, total_reverted_transactions, " +
                    "total_reverted_clauses FROM block ORDER BY number DESC LIMIT 1"
            ) { rs, _ ->
                PostgresRowMappers.totals(rs)
            }
            .firstOrNull()

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun insert(
        block: IndexedBlock,
        transactions: List<IndexedTransaction>,
        totals: BlockTotals,
    ) {
        insertBlock(PostgresRowMapping.flattenBlock(block, totals))
        val rows = transactions.map(PostgresRowMapping::flatten)
        insertTransactions(rows.map { it.transaction })
        insertClauses(rows.flatMap { it.clauses })
        insertEvents(rows.flatMap { it.events })
        insertTransfers(rows.flatMap { it.transfers })
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM block WHERE number >= ?", blockNumber)
    }

    open fun storedVersion(): Int? =
        jdbc
            .query(
                "SELECT version FROM indexer_state WHERE name = ?",
                { rs, _ -> rs.getInt(1) },
                NAME,
            )
            .firstOrNull()

    /** Empties every table and records [version]; the next run backfills from the start block. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun resync(version: Int) {
        jdbc.execute("TRUNCATE block, transaction, clause, event, transfer")
        recordVersion(version)
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun recordVersion(version: Int) {
        jdbc.update(
            "INSERT INTO indexer_state (name, version) VALUES (?, ?) " +
                "ON CONFLICT (name) DO UPDATE SET version = EXCLUDED.version",
            NAME,
            version,
        )
    }

    private fun insertBlock(b: BlockRow) {
        jdbc.update(
            """
            INSERT INTO block (number, id, parent_id, timestamp, size, gas_limit, gas_used,
              beneficiary, signer, total_score, txs_root, txs_features, state_root, receipts_root, com,
              base_fee_per_gas, clause_count, total_vtho_paid, total_transactions, total_clauses,
              total_reverted_transactions, total_reverted_clauses)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
                .trimIndent(),
            b.number,
            b.id,
            b.parentId,
            b.timestamp,
            b.size,
            b.gasLimit,
            b.gasUsed,
            b.beneficiary,
            b.signer,
            b.totalScore,
            b.txsRoot,
            b.txsFeatures,
            b.stateRoot,
            b.receiptsRoot,
            b.com,
            b.baseFeePerGas,
            b.clauseCount,
            b.totalVthoPaid,
            b.totals.totalTransactions,
            b.totals.totalClauses,
            b.totals.totalRevertedTransactions,
            b.totals.totalRevertedClauses,
        )
    }

    private fun insertTransactions(rows: List<TransactionRow>) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(
            """
            INSERT INTO transaction (id, block_number, tx_index, type, size, chain_tag, block_ref,
              expiration, gas_price_coef, gas, max_fee_per_gas, max_priority_fee_per_gas, depends_on,
              nonce, gas_used, gas_payer, paid, reward, reverted, origin, output_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
                .trimIndent(),
            rows,
            rows.size,
        ) { ps, t ->
            ps.setBytes(1, t.id)
            ps.setLong(2, t.blockNumber)
            ps.setInt(3, t.txIndex)
            ps.setShortOrNull(4, t.type)
            ps.setInt(5, t.size)
            ps.setShort(6, t.chainTag)
            ps.setBytes(7, t.blockRef)
            ps.setLong(8, t.expiration)
            ps.setShortOrNull(9, t.gasPriceCoef)
            ps.setLong(10, t.gas)
            ps.setBigDecimal(11, t.maxFeePerGas)
            ps.setBigDecimal(12, t.maxPriorityFeePerGas)
            ps.setBytes(13, t.dependsOn)
            ps.setBigDecimal(14, t.nonce)
            ps.setLong(15, t.gasUsed)
            ps.setBytes(16, t.gasPayer)
            ps.setBigDecimal(17, t.paid)
            ps.setBigDecimal(18, t.reward)
            ps.setBoolean(19, t.reverted)
            ps.setBytes(20, t.origin)
            ps.setShort(21, t.outputCount)
        }
    }

    private fun insertClauses(rows: List<ClauseRow>) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(
            "INSERT INTO clause (tx_id, clause_index, block_number, to_address, value, data) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            rows,
            rows.size,
        ) { ps, c ->
            ps.setBytes(1, c.txId)
            ps.setInt(2, c.clauseIndex)
            ps.setLong(3, c.blockNumber)
            ps.setBytes(4, c.toAddress)
            ps.setBigDecimal(5, c.value)
            ps.setBytes(6, c.data)
        }
    }

    private fun insertEvents(rows: List<EventRow>) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(
            "INSERT INTO event (tx_id, clause_index, event_index, address, topic0, topic1, " +
                "topic2, topic3, topic4, data, name, params) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
            rows,
            rows.size,
        ) { ps, e ->
            ps.setBytes(1, e.txId)
            ps.setInt(2, e.clauseIndex)
            ps.setInt(3, e.eventIndex)
            ps.setBytes(4, e.address)
            for (i in 0 until EventRow.MAX_TOPICS) ps.setBytes(5 + i, e.topics.getOrNull(i))
            ps.setBytes(10, e.data)
            ps.setString(11, e.name)
            ps.setString(12, e.params)
        }
    }

    private fun insertTransfers(rows: List<TransferRow>) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(
            "INSERT INTO transfer (tx_id, clause_index, transfer_index, sender, recipient, amount) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            rows,
            rows.size,
        ) { ps, t ->
            ps.setBytes(1, t.txId)
            ps.setInt(2, t.clauseIndex)
            ps.setInt(3, t.transferIndex)
            ps.setBytes(4, t.sender)
            ps.setBytes(5, t.recipient)
            ps.setBigDecimal(6, t.amount)
        }
    }

    private fun PreparedStatement.setShortOrNull(index: Int, value: Short?) {
        if (value == null) setNull(index, Types.SMALLINT) else setShort(index, value)
    }

    companion object {
        const val NAME = "postgres"
    }
}
