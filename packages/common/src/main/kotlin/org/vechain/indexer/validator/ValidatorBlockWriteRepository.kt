package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.timeseries.TimeSeriesResolution

/** `validator.slot`: append-only, a replayed block upserts in place; validator rolls it back. */
@Repository
@ConditionalOnPostgres
open class ValidatorBlockWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) {

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(records: List<ValidatorBlock>) {
        if (records.isEmpty()) return
        // The driver rewrites the batch into one INSERT, which no primary key may hit twice.
        val rows = records.associateBy { Triple(it.blockNumber, it.validator, it.status) }.values
        jdbc.batchUpdate(INSERT, rows.toList(), rows.size) { ps, v ->
            ValidatorBlockRowMapping.bind(ps, v)
        }
    }

    /** Each validator's newest VALIDATED block timestamp flagged for [resolution]. */
    open fun latestSampled(resolution: TimeSeriesResolution): Map<String, Long> {
        val flag = requireNotNull(ValidatorBlockRowMapping.sampleColumn(resolution))
        return jdbc
            .query(
                "SELECT DISTINCT ON (validator) validator, block_timestamp FROM validator.slot " +
                    "WHERE $flag AND status = 'VALIDATED' ORDER BY validator, block_timestamp DESC"
            ) { rs, _ ->
                PostgresHex.hex(rs.getBytes(1)) to rs.getLong(2)
            }
            .toMap()
    }

    companion object {
        private val INSERT =
            "INSERT INTO validator.slot (block_number, validator, status, " +
                ValidatorBlockRowMapping.COLUMNS.joinToString() +
                ") VALUES (?, ?, CAST(? AS validator.slot_status), " +
                ValidatorBlockRowMapping.COLUMNS.joinToString { "?" } +
                ") ON CONFLICT (block_number, validator, status) DO UPDATE SET " +
                ValidatorBlockRowMapping.COLUMNS.joinToString { "$it = EXCLUDED.$it" }
    }
}
