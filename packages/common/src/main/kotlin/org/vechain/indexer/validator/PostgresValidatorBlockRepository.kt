package org.vechain.indexer.validator

import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("validator", "validator-reward")
@Repository
open class PostgresValidatorBlockRepository(private val jdbcTemplate: JdbcTemplate) :
    ValidatorBlockRepository {

    private fun tableName(): String = "validator_block_rewards"

    private fun mapRow(rs: ResultSet): ValidatorBlock {
        return ValidatorBlock(
            id = rs.getString("id"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            validator = rs.getString("validator"),
            blockReward = rs.getBigDecimal("block_reward")?.toBigInteger(),
            priorityReward = rs.getBigDecimal("priority_reward")?.toBigInteger(),
            total = rs.getBigDecimal("total")?.toBigInteger(),
            status = BlockStatus.valueOf(rs.getString("status")),
            delegatorRewards = rs.getBigDecimal("delegator_rewards")?.toBigInteger(),
            validatorRewards = rs.getBigDecimal("validator_rewards")?.toBigInteger(),
            blocksOffline = rs.getLongOrNull("blocks_offline"),
            onlineBlock = rs.getLongOrNull("online_block"),
            isHourly = rs.getObject("is_hourly") as? Boolean,
            isDaily = rs.getObject("is_daily") as? Boolean,
            isWeekly = rs.getObject("is_weekly") as? Boolean,
            isMonthly = rs.getObject("is_monthly") as? Boolean,
        )
    }

    private fun ResultSet.getLongOrNull(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private fun insertColumns(): String =
        """
        id, block_id, block_number, block_timestamp, validator, block_reward, priority_reward,
        total, status, delegator_rewards, validator_rewards, blocks_offline, online_block,
        is_hourly, is_daily, is_weekly, is_monthly
        """
            .trimIndent()

    private fun insertPlaceholders(): String = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?"

    private fun insertParams(record: ValidatorBlock): Array<Any?> =
        arrayOf(
            record.id,
            record.blockId,
            record.blockNumber,
            record.blockTimestamp,
            record.validator,
            record.blockReward?.let { java.math.BigDecimal(it) },
            record.priorityReward?.let { java.math.BigDecimal(it) },
            record.total?.let { java.math.BigDecimal(it) },
            record.status.name,
            record.delegatorRewards?.let { java.math.BigDecimal(it) },
            record.validatorRewards?.let { java.math.BigDecimal(it) },
            record.blocksOffline,
            record.onlineBlock,
            record.isHourly,
            record.isDaily,
            record.isWeekly,
            record.isMonthly,
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(records: List<ValidatorBlock>) {
        if (records.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (${insertColumns()})
            VALUES (${insertPlaceholders()})
            ON CONFLICT (id) DO UPDATE SET
                block_id = EXCLUDED.block_id,
                block_number = EXCLUDED.block_number,
                block_timestamp = EXCLUDED.block_timestamp,
                validator = EXCLUDED.validator,
                block_reward = EXCLUDED.block_reward,
                priority_reward = EXCLUDED.priority_reward,
                total = EXCLUDED.total,
                status = EXCLUDED.status,
                delegator_rewards = EXCLUDED.delegator_rewards,
                validator_rewards = EXCLUDED.validator_rewards,
                blocks_offline = EXCLUDED.blocks_offline,
                online_block = EXCLUDED.online_block,
                is_hourly = EXCLUDED.is_hourly,
                is_daily = EXCLUDED.is_daily,
                is_weekly = EXCLUDED.is_weekly,
                is_monthly = EXCLUDED.is_monthly
            """
                .trimIndent(),
            records.map { insertParams(it) },
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

    override fun findById(id: String): ValidatorBlock? {
        return try {
            jdbcTemplate.queryForObject(
                "SELECT * FROM ${tableName()} WHERE id = ?",
                { rs, _ -> mapRow(rs) },
                id,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findLatestHourly(): List<ValidatorLatestBlockResult> {
        return jdbcTemplate.query(
            """
            SELECT validator, MAX(block_timestamp) as block_timestamp
            FROM ${tableName()}
            WHERE is_daily = true AND status = 'VALIDATED'
            GROUP BY validator
            ORDER BY validator
            """
                .trimIndent()
        ) { rs, _ ->
            ValidatorLatestBlockResult(
                _id = ValidatorId(rs.getString("validator")),
                blockTimestamp = rs.getLong("block_timestamp"),
            )
        }
    }

    override fun findLatestDaily(): List<ValidatorLatestBlockResult> {
        return jdbcTemplate.query(
            """
            SELECT validator, MAX(block_timestamp) as block_timestamp
            FROM ${tableName()}
            WHERE is_daily = true AND status = 'VALIDATED'
            GROUP BY validator
            ORDER BY validator
            """
                .trimIndent()
        ) { rs, _ ->
            ValidatorLatestBlockResult(
                _id = ValidatorId(rs.getString("validator")),
                blockTimestamp = rs.getLong("block_timestamp"),
            )
        }
    }

    override fun findLatestWeekly(): List<ValidatorLatestBlockResult> {
        return jdbcTemplate.query(
            """
            SELECT validator, MAX(block_timestamp) as block_timestamp
            FROM ${tableName()}
            WHERE is_daily = true AND status = 'VALIDATED'
            GROUP BY validator
            ORDER BY validator
            """
                .trimIndent()
        ) { rs, _ ->
            ValidatorLatestBlockResult(
                _id = ValidatorId(rs.getString("validator")),
                blockTimestamp = rs.getLong("block_timestamp"),
            )
        }
    }

    override fun findLatestMonthly(): List<ValidatorLatestBlockResult> {
        return jdbcTemplate.query(
            """
            SELECT validator, MAX(block_timestamp) as block_timestamp
            FROM ${tableName()}
            WHERE is_daily = true AND status = 'VALIDATED'
            GROUP BY validator
            ORDER BY validator
            """
                .trimIndent()
        ) { rs, _ ->
            ValidatorLatestBlockResult(
                _id = ValidatorId(rs.getString("validator")),
                blockTimestamp = rs.getLong("block_timestamp"),
            )
        }
    }

    override fun findAllInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
              AND status = 'VALIDATED' AND validator = ?
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            validator,
        )
    }

    override fun findHourlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
              AND status = 'VALIDATED' AND validator = ?
              AND (is_hourly = true OR block_timestamp = ? OR block_timestamp = ?)
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            validator,
            startTimestamp,
            endTimestamp,
        )
    }

    override fun findDailyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
              AND status = 'VALIDATED' AND validator = ?
              AND (is_daily = true OR block_timestamp = ? OR block_timestamp = ?)
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            validator,
            startTimestamp,
            endTimestamp,
        )
    }

    override fun findWeeklyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
              AND status = 'VALIDATED' AND validator = ?
              AND (is_weekly = true OR block_timestamp = ? OR block_timestamp = ?)
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            validator,
            startTimestamp,
            endTimestamp,
        )
    }

    override fun findMonthlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE block_timestamp >= ? AND block_timestamp <= ?
              AND status = 'VALIDATED' AND validator = ?
              AND (is_monthly = true OR block_timestamp = ? OR block_timestamp = ?)
            ORDER BY block_timestamp ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            startTimestamp,
            endTimestamp,
            validator,
            startTimestamp,
            endTimestamp,
        )
    }

    override fun findLatestMissed(): List<ValidatorBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE status = 'MISSED' AND blocks_offline IS NULL
            """
                .trimIndent()
        ) { rs, _ ->
            mapRow(rs)
        }
    }

    override fun findMissedInRange(
        validator: String,
        startBlock: Long,
        endBlock: Long,
    ): List<ValidatorBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE validator = ? AND status = 'MISSED'
              AND block_number <= ?
              AND (online_block >= ? OR online_block IS NULL)
            ORDER BY block_number ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            validator,
            endBlock,
            startBlock,
        )
    }

    override fun findAllMissedInRange(startBlock: Long, endBlock: Long): List<ValidatorBlock> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE status = 'MISSED'
              AND block_number <= ?
              AND (online_block >= ? OR online_block IS NULL)
            ORDER BY validator ASC, block_number ASC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            endBlock,
            startBlock,
        )
    }
}
