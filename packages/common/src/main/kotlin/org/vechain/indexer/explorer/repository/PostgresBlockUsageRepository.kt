package org.vechain.indexer.explorer.repository

import java.math.BigInteger
import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.explorer.BlockUsage
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("explorer", "block-usage")
@Repository
open class PostgresBlockUsageRepository(private val jdbcTemplate: JdbcTemplate) :
    BlockUsageRepository {

    private fun tableName(): String = "block_usage"

    private fun mapRow(rs: ResultSet): BlockUsage =
        BlockUsage(
            blockNumber = rs.getLong("block_number"),
            blockId = rs.getString("block_id"),
            blockTimestamp = rs.getLong("block_timestamp"),
            cumulativeGasLimit = BigInteger(rs.getString("cumulative_gas_limit")),
            cumulativeGasUsed = BigInteger(rs.getString("cumulative_gas_used")),
            cumulativeBaseFeePerGas =
                rs.getString("cumulative_base_fee_per_gas")?.let { BigInteger(it) },
            cumulativeNumTransactions = BigInteger(rs.getString("cumulative_num_transactions")),
            cumulativeNumClauses = BigInteger(rs.getString("cumulative_num_clauses")),
            isHourly = rs.getObject("is_hourly") as? Boolean,
            isDaily = rs.getObject("is_daily") as? Boolean,
            isWeekly = rs.getObject("is_weekly") as? Boolean,
            isMonthly = rs.getObject("is_monthly") as? Boolean,
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun save(blockUsage: BlockUsage) {
        jdbcTemplate.update(
            """
            INSERT INTO ${tableName()} (
                block_number, block_id, block_timestamp,
                cumulative_gas_limit, cumulative_gas_used, cumulative_base_fee_per_gas,
                cumulative_num_transactions, cumulative_num_clauses,
                is_hourly, is_daily, is_weekly, is_monthly
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (block_number) DO UPDATE SET
                block_id = EXCLUDED.block_id,
                block_timestamp = EXCLUDED.block_timestamp,
                cumulative_gas_limit = EXCLUDED.cumulative_gas_limit,
                cumulative_gas_used = EXCLUDED.cumulative_gas_used,
                cumulative_base_fee_per_gas = EXCLUDED.cumulative_base_fee_per_gas,
                cumulative_num_transactions = EXCLUDED.cumulative_num_transactions,
                cumulative_num_clauses = EXCLUDED.cumulative_num_clauses,
                is_hourly = EXCLUDED.is_hourly,
                is_daily = EXCLUDED.is_daily,
                is_weekly = EXCLUDED.is_weekly,
                is_monthly = EXCLUDED.is_monthly
            """
                .trimIndent(),
            blockUsage.blockNumber,
            blockUsage.blockId,
            blockUsage.blockTimestamp,
            blockUsage.cumulativeGasLimit.toString(),
            blockUsage.cumulativeGasUsed.toString(),
            blockUsage.cumulativeBaseFeePerGas?.toString(),
            blockUsage.cumulativeNumTransactions.toString(),
            blockUsage.cumulativeNumClauses.toString(),
            blockUsage.isHourly,
            blockUsage.isDaily,
            blockUsage.isWeekly,
            blockUsage.isMonthly,
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(blockUsages: List<BlockUsage>) {
        if (blockUsages.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (
                block_number, block_id, block_timestamp,
                cumulative_gas_limit, cumulative_gas_used, cumulative_base_fee_per_gas,
                cumulative_num_transactions, cumulative_num_clauses,
                is_hourly, is_daily, is_weekly, is_monthly
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (block_number) DO UPDATE SET
                block_id = EXCLUDED.block_id,
                block_timestamp = EXCLUDED.block_timestamp,
                cumulative_gas_limit = EXCLUDED.cumulative_gas_limit,
                cumulative_gas_used = EXCLUDED.cumulative_gas_used,
                cumulative_base_fee_per_gas = EXCLUDED.cumulative_base_fee_per_gas,
                cumulative_num_transactions = EXCLUDED.cumulative_num_transactions,
                cumulative_num_clauses = EXCLUDED.cumulative_num_clauses,
                is_hourly = EXCLUDED.is_hourly,
                is_daily = EXCLUDED.is_daily,
                is_weekly = EXCLUDED.is_weekly,
                is_monthly = EXCLUDED.is_monthly
            """
                .trimIndent(),
            blockUsages.map { blockUsage ->
                arrayOf(
                    blockUsage.blockNumber,
                    blockUsage.blockId,
                    blockUsage.blockTimestamp,
                    blockUsage.cumulativeGasLimit.toString(),
                    blockUsage.cumulativeGasUsed.toString(),
                    blockUsage.cumulativeBaseFeePerGas?.toString(),
                    blockUsage.cumulativeNumTransactions.toString(),
                    blockUsage.cumulativeNumClauses.toString(),
                    blockUsage.isHourly,
                    blockUsage.isDaily,
                    blockUsage.isWeekly,
                    blockUsage.isMonthly,
                )
            },
        )
    }

    override fun findByBlockNumber(blockNumber: Long): BlockUsage? {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT * FROM ${tableName()} WHERE block_number = ?",
                { rs, _ -> mapRow(rs) },
                blockNumber,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findAllInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<BlockUsage> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
        )
    }

    override fun findHourlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<BlockUsage> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
                AND (is_hourly = true OR block_timestamp = ? OR block_timestamp = ?)
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            startTimestamp,
            endTimestamp,
        )
    }

    override fun findDailyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<BlockUsage> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
                AND (is_daily = true OR block_timestamp = ? OR block_timestamp = ?)
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            startTimestamp,
            endTimestamp,
        )
    }

    override fun findWeeklyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<BlockUsage> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
                AND (is_weekly = true OR block_timestamp = ? OR block_timestamp = ?)
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            startTimestamp,
            endTimestamp,
        )
    }

    override fun findMonthlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<BlockUsage> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
                AND (is_monthly = true OR block_timestamp = ? OR block_timestamp = ?)
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            startTimestamp,
            endTimestamp,
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        jdbcTemplate.update("DELETE FROM ${tableName()} WHERE block_number >= ?", blockNumber)
    }

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id FROM ${tableName()}
                ORDER BY block_number DESC
                LIMIT 1
                """
                    .trimIndent()
            ) { rs, _ ->
                BlockIdentifier(number = rs.getLong("block_number"), id = rs.getString("block_id"))
            }
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }
}
