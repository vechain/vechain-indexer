package org.vechain.indexer.explorer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresIndexerTables

/** The `explorer` schema: per-block usage, the daily fee rollup and the origins it counts. */
@Repository
@ConditionalOnPostgres
open class ExplorerWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    /** Everything one block adds, written together so a rollback cannot split them. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(
        usage: BlockUsage,
        fees: AverageFeesPerUser?,
        origins: List<DailyActiveOrigin>,
    ) {
        jdbc.update(USAGE_INSERT) { ps -> BlockUsageRowMapping.bind(ps, usage) }
        if (origins.isNotEmpty()) {
            jdbc.batchUpdate(ORIGIN_INSERT, origins, origins.size) { ps, o ->
                ps.setLong(1, o.dayStartTimestamp)
                ps.setBytes(2, bytes(o.origin))
                ps.setLong(3, o.blockNumber)
            }
        }
        if (fees != null) saveFees(fees)
    }

    // `block_number < ?` keeps a replayed block from closing its own row.
    private fun saveFees(fees: AverageFeesPerUser) {
        jdbc.update(
            "UPDATE ${AverageFeesPerUserRowMapping.TABLE} SET superseded_at = ? " +
                "WHERE day_start_timestamp = ? AND superseded_at IS NULL AND block_number < ?",
            fees.blockNumber,
            fees.dayStartTimestamp,
            fees.blockNumber,
        )
        jdbc.update(FEES_INSERT) { ps -> AverageFeesPerUserRowMapping.bind(ps, fees) }
    }

    /** The cumulative totals the next block adds to; null before the genesis block is written. */
    open fun findUsageAt(blockNumber: Long): BlockUsage? =
        jdbc
            .query(
                "SELECT * FROM ${BlockUsageRowMapping.TABLE} WHERE block_number = ?",
                { rs, _ -> BlockUsageRowMapping.read(rs) },
                blockNumber,
            )
            .firstOrNull()

    /** The day's rollup so far, which the block's fees and new origins extend. */
    open fun findCurrentFees(dayStartTimestamp: Long): AverageFeesPerUser? =
        jdbc
            .query(
                "SELECT * FROM ${AverageFeesPerUserRowMapping.TABLE} " +
                    "WHERE day_start_timestamp = ? AND superseded_at IS NULL",
                { rs, _ -> AverageFeesPerUserRowMapping.read(rs) },
                dayStartTimestamp,
            )
            .firstOrNull()

    /** Which of [origins] have already paid a fee on [dayStartTimestamp]. */
    open fun findKnownOrigins(dayStartTimestamp: Long, origins: Set<String>): Set<String> =
        if (origins.isEmpty()) emptySet()
        else
            jdbc
                .query(
                    "SELECT origin FROM $ORIGIN_TABLE WHERE day_start_timestamp = ? " +
                        "AND origin = ANY(?)",
                    { rs, _ -> hex(rs.getBytes(1)) },
                    dayStartTimestamp,
                    origins.map(::bytes).toTypedArray(),
                )
                .toSet()

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update(
            "DELETE FROM ${BlockUsageRowMapping.TABLE} WHERE block_number >= ?",
            blockNumber,
        )
        jdbc.update("DELETE FROM $ORIGIN_TABLE WHERE block_number >= ?", blockNumber)
        jdbc.update(
            "DELETE FROM ${AverageFeesPerUserRowMapping.TABLE} WHERE block_number >= ?",
            blockNumber,
        )
        jdbc.update(
            "UPDATE ${AverageFeesPerUserRowMapping.TABLE} SET superseded_at = NULL " +
                "WHERE superseded_at >= ?",
            blockNumber,
        )
    }

    override fun truncate() {
        jdbc.execute(
            "TRUNCATE ${BlockUsageRowMapping.TABLE}, ${AverageFeesPerUserRowMapping.TABLE}, " +
                ORIGIN_TABLE
        )
    }

    override fun prune(before: Long): Int =
        jdbc.update(
            "DELETE FROM ${AverageFeesPerUserRowMapping.TABLE} WHERE superseded_at < ?",
            before,
        )

    companion object {
        private const val ORIGIN_TABLE = "explorer.daily_origin"

        private val USAGE_INSERT =
            "INSERT INTO ${BlockUsageRowMapping.TABLE} (block_number, " +
                BlockUsageRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, " +
                BlockUsageRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (block_number) DO UPDATE SET " +
                BlockUsageRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }

        private val FEES_INSERT =
            "INSERT INTO ${AverageFeesPerUserRowMapping.TABLE} " +
                "(day_start_timestamp, block_number, " +
                AverageFeesPerUserRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, " +
                AverageFeesPerUserRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (day_start_timestamp, block_number) DO UPDATE SET " +
                AverageFeesPerUserRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }

        private const val ORIGIN_INSERT =
            "INSERT INTO $ORIGIN_TABLE (day_start_timestamp, origin, block_number) " +
                "VALUES (?, ?, ?) ON CONFLICT (day_start_timestamp, origin) DO NOTHING"
    }
}
