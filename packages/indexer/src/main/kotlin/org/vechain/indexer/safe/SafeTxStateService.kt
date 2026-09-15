package org.vechain.indexer.safe

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.safe.SafeEventUtils.TX_STATE_EVENTS
import org.vechain.indexer.safe.SafeEventUtils.addressParam
import org.vechain.indexer.safe.SafeEventUtils.safeOf
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock

/** One row per `(safe, txHash)`; all three events come from the Safe, so its log address is it. */
@Profile("safe")
@Service
open class SafeTxStateService(private val repository: SafeWriteRepository) {

    companion object {
        const val APPROVE_HASH = "ApproveHash"
        const val EXECUTION_SUCCESS = "ExecutionSuccess"
        const val EXECUTION_FAILURE = "ExecutionFailure"
    }

    /** The new row of each (safe, txHash) pair touched in each block, in ascending block order. */
    open fun processEvents(events: List<IndexedEvent>, knownSafes: Set<String>): List<SafeTxState> {
        val relevant = events.filter { it.eventType in TX_STATE_EVENTS && safeOf(it) in knownSafes }
        if (relevant.isEmpty()) return emptyList()

        val keys = relevant.mapNotNull(::keyOf).toSet()
        // Carries each state across the entry's blocks, so a later block builds on the earlier one.
        val current =
            repository.findCurrentTxStates(keys).associateBy { it.safe to it.txHash }.toMutableMap()
        val rows = linkedMapOf<Triple<Long, String, String>, SafeTxState>()

        groupByBlock(relevant).forEach { (blockDetails, blockEvents) ->
            blockEvents.forEach { event ->
                val (safe, txHash) = keyOf(event) ?: return@forEach
                val updated = apply(event, safe, txHash, current[safe to txHash], blockDetails)
                current[safe to txHash] = updated
                rows[Triple(blockDetails.blockNumber, safe, txHash)] = updated
            }
        }
        return rows.values.toList()
    }

    private fun apply(
        event: IndexedEvent,
        safe: String,
        txHash: String,
        existing: SafeTxState?,
        block: BlockDetails,
    ): SafeTxState {
        val base =
            existing?.copy(
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
            )
                ?: SafeTxState(
                    id = SafeTxState.buildId(safe, txHash),
                    safe = safe,
                    txHash = txHash,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                )

        return when (event.eventType) {
            APPROVE_HASH -> {
                val owner = addressParam(event, "owner") ?: return base
                if (base.approvers.any { it.owner == owner }) base
                else
                    base.copy(
                        approvers =
                            base.approvers +
                                SafeTxApproval(
                                    owner = owner,
                                    block = block.blockNumber,
                                    blockTimestamp = block.blockTimestamp,
                                    vechainTxId = HexUtils.normalise(event.txId),
                                )
                    )
            }
            else ->
                base.copy(
                    executed = true,
                    failed = event.eventType == EXECUTION_FAILURE,
                    executedBlock = block.blockNumber,
                    executedTimestamp = block.blockTimestamp,
                    executor = event.origin?.let { HexUtils.normalise(it) },
                    vechainTxId = HexUtils.normalise(event.txId),
                )
        }
    }

    private fun keyOf(event: IndexedEvent): Pair<String, String>? {
        val safe = safeOf(event) ?: return null
        val txHash =
            when (event.eventType) {
                APPROVE_HASH -> addressParam(event, "approvedHash")
                else -> addressParam(event, "txHash")
            } ?: return null
        return safe to txHash
    }
}
