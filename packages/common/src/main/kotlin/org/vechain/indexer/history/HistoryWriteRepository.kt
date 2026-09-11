package org.vechain.indexer.history

import java.sql.PreparedStatement
import java.sql.Types
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.PostgresIndexerTables

/**
 * Append-only: a block's rows in one transaction, rollback by block on both tables, replay is a
 * no-op.
 */
@Repository
@ConditionalOnPostgres
open class HistoryWriteRepository(
    @Qualifier("postgresJdbcTemplate") private val jdbc: JdbcTemplate
) : PostgresIndexerTables {

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(events: List<IndexedHistoryEvent>) {
        val rows = events.map(HistoryRowMapping::flatten)
        insertEvents(rows.map { it.event })
        insertAddresses(rows.flatMap { it.addresses })
    }

    override fun rollbackFrom(blockNumber: Long) {
        jdbc.update("DELETE FROM history.event_address WHERE block_number >= ?", blockNumber)
        jdbc.update("DELETE FROM history.event WHERE block_number >= ?", blockNumber)
    }

    override fun truncate() {
        jdbc.execute("TRUNCATE history.event, history.event_address")
    }

    private fun insertEvents(rows: List<HistoryEventRow>) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(
            """
            INSERT INTO history.event (id, block_number, block_id, block_timestamp, tx_id, event_name,
              origin, gas_payer, reverted, contract_address, token_id, to_address, from_address, owner,
              value, app_id, round_id, proposal_id, support, vote_power, vote_weight, reason,
              old_level, new_level, level_id, input_token, output_token, input_value, output_value,
              vet_generated_vtho_rewards, delegation_rewards, migrated, autorenew, validator,
              delegation_id, period_claimed, boosted_blocks, proof, app_votes, token_ids,
              lifecycle_status, lifecycle_next_cycle, lifecycle_cycle_length, lifecycle_force_exit,
              lifecycle_order)
            VALUES (?, ?, ?, ?, ?, CAST(? AS history.event_name), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb),
              ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """
                .trimIndent(),
            rows,
            rows.size,
        ) { ps, e ->
            ps.setBytes(1, e.id)
            ps.setLong(2, e.blockNumber)
            ps.setBytes(3, e.blockId)
            ps.setLong(4, e.blockTimestamp)
            ps.setBytes(5, e.txId)
            ps.setString(6, e.eventName.name)
            ps.setBytes(7, e.origin)
            ps.setBytes(8, e.gasPayer)
            ps.setObject(9, e.reverted, Types.BOOLEAN)
            ps.setBytes(10, e.contractAddress)
            ps.setBigDecimal(11, e.tokenId)
            ps.setBytes(12, e.to)
            ps.setBytes(13, e.from)
            ps.setBytes(14, e.owner)
            ps.setBigDecimal(15, e.value)
            ps.setBytes(16, e.appId)
            ps.setObject(17, e.roundId, Types.BIGINT)
            ps.setBigDecimal(18, e.proposalId)
            ps.setObject(19, e.support, Types.SMALLINT)
            ps.setBigDecimal(20, e.votePower)
            ps.setBigDecimal(21, e.voteWeight)
            ps.setString(22, e.reason)
            ps.setObject(23, e.oldLevel, Types.SMALLINT)
            ps.setObject(24, e.newLevel, Types.SMALLINT)
            ps.setObject(25, e.levelId, Types.SMALLINT)
            ps.setBytes(26, e.inputToken)
            ps.setBytes(27, e.outputToken)
            ps.setBigDecimal(28, e.inputValue)
            ps.setBigDecimal(29, e.outputValue)
            ps.setBigDecimal(30, e.vetGeneratedVthoRewards)
            ps.setBigDecimal(31, e.delegationRewards)
            ps.setObject(32, e.migrated, Types.BOOLEAN)
            ps.setObject(33, e.autorenew, Types.BOOLEAN)
            ps.setBytes(34, e.validator)
            ps.setBigDecimal(35, e.delegationId)
            ps.setObject(36, e.periodClaimed, Types.BIGINT)
            ps.setBigDecimal(37, e.boostedBlocks)
            ps.setString(38, e.proof)
            ps.setString(39, e.appVotes)
            ps.setNumericArray(40, e.tokenIds)
            ps.setObject(41, e.lifecycleStatus, Types.SMALLINT)
            ps.setObject(42, e.lifecycleNextCycle, Types.BIGINT)
            ps.setObject(43, e.lifecycleCycleLength, Types.BIGINT)
            ps.setObject(44, e.lifecycleForceExit, Types.BOOLEAN)
            ps.setObject(45, e.lifecycleOrder, Types.INTEGER)
        }
    }

    private fun insertAddresses(rows: List<HistoryEventAddressRow>) {
        if (rows.isEmpty()) return
        jdbc.batchUpdate(
            "INSERT INTO history.event_address (address, block_timestamp, event_id, event_name, " +
                "block_number) VALUES (?, ?, ?, CAST(? AS history.event_name), ?) ON CONFLICT DO NOTHING",
            rows,
            rows.size,
        ) { ps, a ->
            ps.setBytes(1, a.address)
            ps.setLong(2, a.blockTimestamp)
            ps.setBytes(3, a.eventId)
            ps.setString(4, a.eventName.name)
            ps.setLong(5, a.blockNumber)
        }
    }

    private fun PreparedStatement.setNumericArray(index: Int, values: List<java.math.BigDecimal>?) {
        if (values == null) setNull(index, Types.ARRAY)
        else setArray(index, connection.createArrayOf("numeric", values.toTypedArray()))
    }
}
