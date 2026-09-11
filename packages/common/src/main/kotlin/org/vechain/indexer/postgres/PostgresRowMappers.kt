package org.vechain.indexer.postgres

import java.sql.ResultSet

/** `ResultSet` → row, one per table, for readers that `SELECT *` from the Postgres tables. */
object PostgresRowMappers {

    fun block(rs: ResultSet): BlockRow =
        BlockRow(
            number = rs.getLong("number"),
            id = rs.getBytes("id"),
            parentId = rs.getBytes("parent_id"),
            timestamp = rs.getLong("timestamp"),
            size = rs.getInt("size"),
            gasLimit = rs.getLong("gas_limit"),
            gasUsed = rs.getLong("gas_used"),
            beneficiary = rs.getBytes("beneficiary"),
            signer = rs.getBytes("signer"),
            totalScore = rs.getLong("total_score"),
            txsRoot = rs.getBytes("txs_root"),
            txsFeatures = rs.getShort("txs_features"),
            stateRoot = rs.getBytes("state_root"),
            receiptsRoot = rs.getBytes("receipts_root"),
            com = rs.getBoolean("com"),
            baseFeePerGas = rs.getBigDecimal("base_fee_per_gas"),
            clauseCount = rs.getInt("clause_count"),
            totalVthoPaid = rs.getBigDecimal("total_vtho_paid"),
            totals = totals(rs),
        )

    fun totals(rs: ResultSet): BlockTotals =
        BlockTotals(
            totalTransactions = rs.getLong("total_transactions"),
            totalClauses = rs.getLong("total_clauses"),
            totalRevertedTransactions = rs.getLong("total_reverted_transactions"),
            totalRevertedClauses = rs.getLong("total_reverted_clauses"),
        )

    fun transaction(rs: ResultSet): TransactionRow =
        TransactionRow(
            id = rs.getBytes("id"),
            blockNumber = rs.getLong("block_number"),
            txIndex = rs.getInt("tx_index"),
            type = rs.getObject("type", Integer::class.java)?.toShort(),
            size = rs.getInt("size"),
            chainTag = rs.getShort("chain_tag"),
            blockRef = rs.getBytes("block_ref"),
            expiration = rs.getLong("expiration"),
            gasPriceCoef = rs.getObject("gas_price_coef", Integer::class.java)?.toShort(),
            gas = rs.getLong("gas"),
            maxFeePerGas = rs.getBigDecimal("max_fee_per_gas"),
            maxPriorityFeePerGas = rs.getBigDecimal("max_priority_fee_per_gas"),
            dependsOn = rs.getBytes("depends_on"),
            nonce = rs.getBigDecimal("nonce"),
            gasUsed = rs.getLong("gas_used"),
            gasPayer = rs.getBytes("gas_payer"),
            paid = rs.getBigDecimal("paid"),
            reward = rs.getBigDecimal("reward"),
            reverted = rs.getBoolean("reverted"),
            origin = rs.getBytes("origin"),
            outputCount = rs.getShort("output_count"),
        )

    fun clause(rs: ResultSet): ClauseRow =
        ClauseRow(
            txId = rs.getBytes("tx_id"),
            clauseIndex = rs.getInt("clause_index"),
            blockNumber = rs.getLong("block_number"),
            toAddress = rs.getBytes("to_address"),
            value = rs.getBigDecimal("value"),
            data = rs.getBytes("data"),
        )

    fun event(rs: ResultSet): EventRow =
        EventRow(
            txId = rs.getBytes("tx_id"),
            clauseIndex = rs.getInt("clause_index"),
            eventIndex = rs.getInt("event_index"),
            address = rs.getBytes("address"),
            topics = (0 until EventRow.MAX_TOPICS).mapNotNull { rs.getBytes("topic$it") },
            data = rs.getBytes("data"),
            name = rs.getString("name"),
            params = rs.getString("params"),
        )

    fun transfer(rs: ResultSet): TransferRow =
        TransferRow(
            txId = rs.getBytes("tx_id"),
            clauseIndex = rs.getInt("clause_index"),
            transferIndex = rs.getInt("transfer_index"),
            sender = rs.getBytes("sender"),
            recipient = rs.getBytes("recipient"),
            amount = rs.getBigDecimal("amount"),
        )
}
