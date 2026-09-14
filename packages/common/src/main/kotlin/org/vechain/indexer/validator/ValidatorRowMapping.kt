package org.vechain.indexer.validator

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull

/** A `validator.state` row to a [Validator] and back; `read(bind(v))` is `v` again. */
object ValidatorRowMapping {

    /** The columns after `(id, block_number)`, in the order [bind] sets them. */
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "endorser",
            "beneficiary",
            "status",
            "cycle_period_length",
            "start_block",
            "exit_block",
            "completed_periods",
            "validator_vet_staked",
            "validator_locked_weight",
            "delegator_vet_staked",
            "vet_staked",
            "validator_queued_vet_staked",
            "queued_vet_staked",
            "exiting_vet_staked",
            "validator_exiting_vet_staked",
            "total_next_period_weight",
            "queue_position",
            "available_start_block",
            "scheduled_slots",
            "proposed_blocks",
            "missed_slots",
            "last_proposed_block_number",
            "last_missed_block_number",
            "offline_block",
        )

    fun bind(ps: PreparedStatement, v: Validator) {
        ps.setBytes(1, bytes(v.id))
        ps.setLong(2, v.blockNumber)
        ps.setBytes(3, bytes(v.blockId))
        ps.setLong(4, v.blockTimestamp)
        ps.setBytes(5, bytesOrNull(v.endorser))
        ps.setBytes(6, bytesOrNull(v.beneficiary))
        ps.setString(7, v.status?.name)
        ps.setObject(8, v.cyclePeriodLength, Types.BIGINT)
        ps.setObject(9, v.startBlock, Types.BIGINT)
        ps.setObject(10, v.exitBlock, Types.BIGINT)
        ps.setObject(11, v.completedPeriods, Types.BIGINT)
        ps.setBigDecimal(12, v.validatorVetStaked)
        ps.setBigDecimal(13, v.validatorLockedWeight)
        ps.setBigDecimal(14, v.delegatorVetStaked)
        ps.setBigDecimal(15, v.vetStaked)
        ps.setBigDecimal(16, v.validatorQueuedVetStaked)
        ps.setBigDecimal(17, v.queuedVetStaked)
        ps.setBigDecimal(18, v.exitingVetStaked)
        ps.setBigDecimal(19, v.validatorExitingVetStaked)
        ps.setBigDecimal(20, v.totalNextPeriodWeight)
        ps.setObject(21, v.queuePosition, Types.BIGINT)
        ps.setObject(22, v.availableStartBlock, Types.BIGINT)
        ps.setLong(23, v.scheduledSlots)
        ps.setLong(24, v.proposedBlocks)
        ps.setLong(25, v.missedSlots)
        ps.setObject(26, v.lastProposedBlockNumber, Types.BIGINT)
        ps.setObject(27, v.lastMissedBlockNumber, Types.BIGINT)
        ps.setObject(28, v.offlineBlock, Types.BIGINT)
    }

    fun read(rs: ResultSet): Validator =
        Validator(
            id = hex(rs.getBytes("id")),
            blockNumber = rs.getLong("block_number"),
            blockId = hex(rs.getBytes("block_id")),
            blockTimestamp = rs.getLong("block_timestamp"),
            endorser = hexOrNull(rs.getBytes("endorser")),
            beneficiary = hexOrNull(rs.getBytes("beneficiary")),
            status = rs.getString("status")?.let(Status::valueOf),
            cyclePeriodLength = rs.getObject("cycle_period_length", Long::class.javaObjectType),
            startBlock = rs.getObject("start_block", Long::class.javaObjectType),
            exitBlock = rs.getObject("exit_block", Long::class.javaObjectType),
            completedPeriods = rs.getObject("completed_periods", Long::class.javaObjectType),
            validatorVetStaked = rs.getBigDecimal("validator_vet_staked"),
            validatorLockedWeight = rs.getBigDecimal("validator_locked_weight"),
            delegatorVetStaked = rs.getBigDecimal("delegator_vet_staked"),
            vetStaked = rs.getBigDecimal("vet_staked"),
            validatorQueuedVetStaked = rs.getBigDecimal("validator_queued_vet_staked"),
            queuedVetStaked = rs.getBigDecimal("queued_vet_staked"),
            exitingVetStaked = rs.getBigDecimal("exiting_vet_staked"),
            validatorExitingVetStaked = rs.getBigDecimal("validator_exiting_vet_staked"),
            totalNextPeriodWeight = rs.getBigDecimal("total_next_period_weight"),
            queuePosition = rs.getObject("queue_position", Long::class.javaObjectType),
            availableStartBlock = rs.getObject("available_start_block", Long::class.javaObjectType),
            scheduledSlots = rs.getLong("scheduled_slots"),
            proposedBlocks = rs.getLong("proposed_blocks"),
            missedSlots = rs.getLong("missed_slots"),
            lastProposedBlockNumber =
                rs.getObject("last_proposed_block_number", Long::class.javaObjectType),
            lastMissedBlockNumber =
                rs.getObject("last_missed_block_number", Long::class.javaObjectType),
            offlineBlock = rs.getObject("offline_block", Long::class.javaObjectType),
        )
}
