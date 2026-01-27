package org.vechain.indexer.accounts.repository

import java.sql.ResultSet
import org.springframework.context.annotation.Profile
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.VetBalance
import org.vechain.indexer.thor.model.BlockIdentifier

@Profile("accounts", "vet-balance")
@Repository
open class PostgresVetBalanceRepository(private val jdbcTemplate: JdbcTemplate) :
    VetBalanceRepository {

    private fun tableName(): String = "vet_balances"

    private fun mapRow(rs: ResultSet): VetBalance =
        VetBalance(
            id = rs.getString("id"),
            address = rs.getString("address"),
            blockId = rs.getString("block_id"),
            blockNumber = rs.getLong("block_number"),
            blockTimestamp = rs.getLong("block_timestamp"),
            balance = rs.getBigDecimal("balance").toBigInteger(),
        )

    @Transactional(rollbackFor = [Exception::class])
    override fun saveAll(records: List<VetBalance>) {
        if (records.isEmpty()) return

        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${tableName()} (id, address, block_id, block_number, block_timestamp, balance)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                address = EXCLUDED.address,
                block_id = EXCLUDED.block_id,
                block_number = EXCLUDED.block_number,
                block_timestamp = EXCLUDED.block_timestamp,
                balance = EXCLUDED.balance
            """
                .trimIndent(),
            records.map { record ->
                arrayOf<Any?>(
                    record.id,
                    record.address,
                    record.blockId,
                    record.blockNumber,
                    record.blockTimestamp,
                    record.balance,
                )
            },
        )
    }

    override fun count(): Long {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ${tableName()}", Long::class.java)
            ?: 0L
    }

    override fun findFirstByAddressOrderByBlockTimestampDesc(address: String): VetBalance? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT * FROM ${tableName()}
                WHERE address = ?
                ORDER BY block_timestamp DESC
                LIMIT 1
                """
                    .trimIndent(),
                { rs, _ -> mapRow(rs) },
                address,
            )
        } catch (_: EmptyResultDataAccessException) {
            null
        }
    }

    override fun findByAddressAndBlockTimestampBetweenOrderByBlockTimestampDesc(
        address: String,
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<VetBalance> {
        return jdbcTemplate.query(
            """
            SELECT * FROM ${tableName()}
            WHERE address = ? AND block_timestamp BETWEEN ? AND ?
            ORDER BY block_timestamp DESC
            """
                .trimIndent(),
            { rs, _ -> mapRow(rs) },
            address,
            startTimestamp,
            endTimestamp,
        )
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        jdbcTemplate.update(
            """
            DELETE FROM ${tableName()}
            WHERE block_number >= ?
            """
                .trimIndent(),
            blockNumber,
        )
    }

    override fun getLatestBlockIdentifier(): BlockIdentifier? {
        return try {
            jdbcTemplate.queryForObject(
                """
                SELECT block_number, block_id
                FROM ${tableName()}
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
