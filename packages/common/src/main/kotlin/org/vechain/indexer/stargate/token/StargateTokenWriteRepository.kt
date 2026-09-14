package org.vechain.indexer.stargate.token

import java.math.BigDecimal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** `stargate_token.state` as a temporal table, plus the indexer's own reads of the current rows. */
@Repository
@ConditionalOnPostgres
open class StargateTokenWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Applies the snapshots block by block in ascending order, all in one transaction. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(tokens: List<StargateToken>) {
        tokens
            .groupBy { it.blockNumber }
            .toSortedMap()
            .forEach { (blockNumber, rows) -> saveBlock(blockNumber, rows) }
    }

    // `block_number < ?` keeps a replayed block from closing its own rows.
    private fun saveBlock(blockNumber: Long, tokens: List<StargateToken>) {
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = tokens.associateBy { it.tokenId }.values.toList()
        jdbc.batchUpdate(
            "UPDATE stargate_token.state SET superseded_at = ? " +
                "WHERE token_id = ? AND superseded_at IS NULL AND block_number < ?",
            rows,
            rows.size,
        ) { ps, t ->
            ps.setLong(1, blockNumber)
            ps.setBigDecimal(2, BigDecimal(t.tokenId))
            ps.setLong(3, blockNumber)
        }
        jdbc.batchUpdate(INSERT, rows, rows.size) { ps, t -> StargateTokenRowMapping.bind(ps, t) }
    }

    open fun findAllById(tokenIds: Set<String>): List<StargateToken> =
        query(
            "$CURRENT AND token_id = ANY(?) ORDER BY token_id",
            tokenIds.map(::BigDecimal).toTypedArray(),
        )

    open fun findByValidatorIdIn(validatorIds: Set<String>): List<StargateToken> =
        query(
            "$CURRENT AND validator_id = ANY(?) ORDER BY token_id",
            validatorIds.map(PostgresHex::bytes).toTypedArray(),
        )

    open fun findByDelegationNextPeriodAndDelegationStatusIn(
        blockNumbers: List<Long>,
        statuses: List<String>,
    ): List<StargateToken> =
        query(
            "$CURRENT AND delegation_next_period = ANY(?) " +
                "AND CAST(delegation_status AS text) = ANY(?) ORDER BY token_id",
            blockNumbers.toTypedArray(),
            statuses.toTypedArray(),
        )

    /** Every validator some current token points at; null for tokens never delegated. */
    open fun findAllDistinctValidatorIds(): List<String?> =
        jdbc.query(
            "SELECT DISTINCT validator_id FROM stargate_token.state WHERE superseded_at IS NULL"
        ) { rs, _ ->
            PostgresHex.hexOrNull(rs.getBytes(1))
        }

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM stargate_token.state WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "UPDATE stargate_token.state SET superseded_at = NULL WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE stargate_token.state")
    }

    /** The store records [before] once rows are gone and refuses any rollback below it. */
    override fun prune(before: Long): Int =
        jdbc.update("DELETE FROM stargate_token.state WHERE superseded_at < ?", before)

    private fun query(sql: String, vararg args: Any): List<StargateToken> =
        jdbc.query(sql, { rs, _ -> StargateTokenRowMapping.read(rs) }, *args)

    companion object {
        private const val CURRENT = "SELECT * FROM stargate_token.state WHERE superseded_at IS NULL"
        private val INSERT =
            "INSERT INTO stargate_token.state (token_id, block_number, " +
                StargateTokenRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                StargateTokenRowMapping.COLUMNS.joinToString {
                    if (it == "delegation_status") "CAST(? AS stargate_token.delegation_status)"
                    else "?"
                } +
                ") ON CONFLICT (token_id, block_number) DO UPDATE SET " +
                StargateTokenRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
