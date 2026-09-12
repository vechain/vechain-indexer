package org.vechain.indexer.history

import com.fasterxml.jackson.core.type.TypeReference
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.ResultSet
import org.vechain.indexer.b3tr.action.SustainabilityProofV2
import org.vechain.indexer.b3tr.voting.AppVote
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.postgres.PostgresHex.bareHex
import org.vechain.indexer.postgres.PostgresHex.bytes
import org.vechain.indexer.postgres.PostgresHex.bytesOrNull
import org.vechain.indexer.postgres.PostgresHex.hex
import org.vechain.indexer.postgres.PostgresHex.hexOrNull
import org.vechain.indexer.postgres.PostgresJson
import org.vechain.indexer.validator.Status

/** [IndexedHistoryEvent] to rows and back: BYTEA addresses, NUMERIC quantities, bare sha1 id. */
object HistoryRowMapping {

    private val proofType = object : TypeReference<SustainabilityProofV2>() {}
    private val appVotesType = object : TypeReference<List<AppVote>>() {}

    fun flatten(e: IndexedHistoryEvent): HistoryRows {
        val id = bytes(e.id)
        val row =
            HistoryEventRow(
                id = id,
                blockNumber = e.blockNumber,
                blockId = bytes(e.blockId),
                blockTimestamp = e.blockTimestamp,
                txId = bytes(e.txId),
                eventName = e.eventName,
                origin = bytesOrNull(e.origin),
                gasPayer = bytesOrNull(e.gasPayer),
                reverted = e.reverted,
                contractAddress = bytesOrNull(e.contractAddress),
                tokenId = numeric(e.tokenId),
                to = bytesOrNull(e.to),
                from = bytesOrNull(e.from),
                owner = bytesOrNull(e.owner),
                value = numeric(e.value),
                appId = bytesOrNull(e.appId),
                roundId = numeric(e.roundId)?.longValueExact(),
                proposalId = numeric(e.proposalId),
                support = e.support?.value?.toShort(),
                votePower = numeric(e.votePower),
                voteWeight = numeric(e.voteWeight),
                reason = e.reason,
                oldLevel = numeric(e.oldLevel)?.shortValueExact(),
                newLevel = numeric(e.newLevel)?.shortValueExact(),
                levelId = numeric(e.levelId)?.shortValueExact(),
                inputToken = bytesOrNull(e.inputToken),
                outputToken = bytesOrNull(e.outputToken),
                inputValue = numeric(e.inputValue),
                outputValue = numeric(e.outputValue),
                vetGeneratedVthoRewards = numeric(e.vetGeneratedVthoRewards),
                delegationRewards = numeric(e.delegationRewards),
                migrated = e.migrated,
                autorenew = e.autorenew,
                validator = bytesOrNull(e.validator),
                delegationId = numeric(e.delegationId),
                periodClaimed = e.periodClaimed,
                boostedBlocks = numeric(e.boostedBlocks),
                proof = PostgresJson.write(e.proof),
                appVotes = PostgresJson.write(e.appVotes),
                // Decoded as uint256 upstream, so the elements may be BigInteger behind the String
                // type.
                tokenIds = (e.tokenIds as List<*>?)?.map { numeric(it.toString())!! },
                lifecycleStatus = e.delegationLifecycleStatus?.ordinal?.toShort(),
                lifecycleNextCycle = e.delegationLifecycleNextCycle,
                lifecycleCycleLength = e.delegationLifecycleCycleLength,
                lifecycleForceExit = e.delegationLifecycleForceExit,
                lifecycleOrder = e.delegationLifecycleOrder,
            )
        val addresses =
            listOfNotNull(e.origin, e.gasPayer, e.to, e.from, e.owner)
                .map(::bytes)
                .distinctBy { hex(it) }
                .map {
                    HistoryEventAddressRow(it, e.blockTimestamp, id, e.eventName, e.blockNumber)
                }
        return HistoryRows(row, addresses)
    }

    fun assemble(r: HistoryEventRow): IndexedHistoryEvent =
        IndexedHistoryEvent(
            id = bareHex(r.id),
            blockId = hex(r.blockId),
            blockNumber = r.blockNumber,
            blockTimestamp = r.blockTimestamp,
            txId = hex(r.txId),
            origin = hexOrNull(r.origin),
            gasPayer = hexOrNull(r.gasPayer),
            reverted = r.reverted,
            contractAddress = hexOrNull(r.contractAddress),
            tokenId = decimal(r.tokenId),
            eventName = r.eventName,
            to = hexOrNull(r.to),
            from = hexOrNull(r.from),
            value = decimal(r.value),
            appId = hexOrNull(r.appId),
            proof = PostgresJson.read(r.proof, proofType),
            roundId = r.roundId?.toString(),
            appVotes = PostgresJson.read(r.appVotes, appVotesType),
            support = r.support?.let { Support.fromValue(it.toInt()) },
            votePower = decimal(r.votePower),
            voteWeight = decimal(r.voteWeight),
            reason = r.reason,
            proposalId = decimal(r.proposalId),
            oldLevel = r.oldLevel?.toString(),
            newLevel = r.newLevel?.toString(),
            inputToken = hexOrNull(r.inputToken),
            outputToken = hexOrNull(r.outputToken),
            inputValue = decimal(r.inputValue),
            outputValue = decimal(r.outputValue),
            levelId = r.levelId?.toString(),
            owner = hexOrNull(r.owner),
            vetGeneratedVthoRewards = decimal(r.vetGeneratedVthoRewards),
            delegationRewards = decimal(r.delegationRewards),
            migrated = r.migrated,
            autorenew = r.autorenew,
            tokenIds = r.tokenIds?.map { it.toPlainString() },
            validator = hexOrNull(r.validator),
            delegationId = decimal(r.delegationId),
            periodClaimed = r.periodClaimed,
            boostedBlocks = decimal(r.boostedBlocks),
            delegationLifecycleStatus = r.lifecycleStatus?.let { Status.entries[it.toInt()] },
            delegationLifecycleNextCycle = r.lifecycleNextCycle,
            delegationLifecycleCycleLength = r.lifecycleCycleLength,
            delegationLifecycleForceExit = r.lifecycleForceExit,
            delegationLifecycleOrder = r.lifecycleOrder,
        )

    fun row(rs: ResultSet): HistoryEventRow =
        HistoryEventRow(
            id = rs.getBytes("id"),
            blockNumber = rs.getLong("block_number"),
            blockId = rs.getBytes("block_id"),
            blockTimestamp = rs.getLong("block_timestamp"),
            txId = rs.getBytes("tx_id"),
            eventName = HistoryEventName.valueOf(rs.getString("event_name")),
            origin = rs.getBytes("origin"),
            gasPayer = rs.getBytes("gas_payer"),
            reverted = rs.getObject("reverted", Boolean::class.javaObjectType),
            contractAddress = rs.getBytes("contract_address"),
            tokenId = rs.getBigDecimal("token_id"),
            to = rs.getBytes("to_address"),
            from = rs.getBytes("from_address"),
            owner = rs.getBytes("owner"),
            value = rs.getBigDecimal("value"),
            appId = rs.getBytes("app_id"),
            roundId = rs.getObject("round_id", Long::class.javaObjectType),
            proposalId = rs.getBigDecimal("proposal_id"),
            support = rs.getObject("support", Short::class.javaObjectType),
            votePower = rs.getBigDecimal("vote_power"),
            voteWeight = rs.getBigDecimal("vote_weight"),
            reason = rs.getString("reason"),
            oldLevel = rs.getObject("old_level", Short::class.javaObjectType),
            newLevel = rs.getObject("new_level", Short::class.javaObjectType),
            levelId = rs.getObject("level_id", Short::class.javaObjectType),
            inputToken = rs.getBytes("input_token"),
            outputToken = rs.getBytes("output_token"),
            inputValue = rs.getBigDecimal("input_value"),
            outputValue = rs.getBigDecimal("output_value"),
            vetGeneratedVthoRewards = rs.getBigDecimal("vet_generated_vtho_rewards"),
            delegationRewards = rs.getBigDecimal("delegation_rewards"),
            migrated = rs.getObject("migrated", Boolean::class.javaObjectType),
            autorenew = rs.getObject("autorenew", Boolean::class.javaObjectType),
            validator = rs.getBytes("validator"),
            delegationId = rs.getBigDecimal("delegation_id"),
            periodClaimed = rs.getObject("period_claimed", Long::class.javaObjectType),
            boostedBlocks = rs.getBigDecimal("boosted_blocks"),
            proof = rs.getString("proof"),
            appVotes = rs.getString("app_votes"),
            tokenIds = (rs.getArray("token_ids")?.array as Array<*>?)?.map { it as BigDecimal },
            lifecycleStatus = rs.getObject("lifecycle_status", Short::class.javaObjectType),
            lifecycleNextCycle = rs.getObject("lifecycle_next_cycle", Long::class.javaObjectType),
            lifecycleCycleLength =
                rs.getObject("lifecycle_cycle_length", Long::class.javaObjectType),
            lifecycleForceExit =
                rs.getObject("lifecycle_force_exit", Boolean::class.javaObjectType),
            lifecycleOrder = rs.getObject("lifecycle_order", Int::class.javaObjectType),
        )

    /**
     * Quantities arrive in base 10 from the ABI decoder; a Thor `0x` amount decodes the same way.
     */
    fun numeric(s: String?): BigDecimal? = s?.let {
        if (it.startsWith("0x")) BigDecimal(BigInteger(it.substring(2), 16)) else BigDecimal(it)
    }

    fun decimal(n: BigDecimal?): String? = n?.toPlainString()
}
