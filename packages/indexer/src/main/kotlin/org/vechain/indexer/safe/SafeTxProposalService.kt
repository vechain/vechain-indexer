package org.vechain.indexer.safe

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.safe.SafeEventUtils.PROPOSAL_EVENTS
import org.vechain.indexer.safe.SafeEventUtils.addressParam
import org.vechain.indexer.safe.SafeEventUtils.safeOf
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString

/** Folds the three emitter events into one row; the emitter and the `safe` it names are checked. */
@Profile("safe")
@Service
open class SafeTxProposalService(
    private val repository: SafeWriteRepository,
    @param:Value("\${business-event.substitutions.SAFE_EMITTER_CONTRACT}")
    private val emitterAddress: String,
) {

    companion object {
        const val SAFE_TX_PROPOSED = "SafeTxProposed"
        const val SAFE_TX_HASH_FIELDS = "SafeTxHashFields"
        const val SAFE_BATCH_TX_PROPOSED = "SafeBatchTxProposed"
    }

    /** The new row of each (safe, txHash) pair touched in each block, in ascending block order. */
    open fun processEvents(
        events: List<IndexedEvent>,
        knownSafes: Set<String>,
    ): List<SafeTxProposal> {
        // The signature proves nothing: any contract can name someone else's Safe in one.
        val relevant = events.filter {
            it.eventType in PROPOSAL_EVENTS && isEmitter(it.address) && safeOf(it) in knownSafes
        }
        if (relevant.isEmpty()) return emptyList()

        val keys = relevant.mapNotNull(::keyOf).toSet()
        // Carries each row across the entry's blocks, so a later block builds on the earlier one.
        val current =
            repository
                .findCurrentProposals(keys)
                .associateBy { it.safe to it.txHash }
                .toMutableMap()
        val rows = linkedMapOf<Triple<Long, String, String>, SafeTxProposal>()

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
        existing: SafeTxProposal?,
        block: BlockDetails,
    ): SafeTxProposal {
        val base =
            existing?.copy(
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
            )
                ?: SafeTxProposal(
                    id = SafeTxProposal.buildId(safe, txHash),
                    safe = safe,
                    txHash = txHash,
                    blockId = block.blockId,
                    blockNumber = block.blockNumber,
                    blockTimestamp = block.blockTimestamp,
                )

        return when (event.eventType) {
            SAFE_TX_PROPOSED ->
                base.copy(
                    proposer = addressParam(event, "proposer"),
                    proposedBlock = block.blockNumber,
                    proposedTimestamp = block.blockTimestamp,
                    proposedVechainTxId = HexUtils.normalise(event.txId),
                    to = addressParam(event, "to"),
                    value = event.params.getAsBigInteger("value"),
                    data = event.params.getAsString("data"),
                    operation = event.params.getAsInt("operation"),
                    nonce = event.params.getAsBigInteger("nonce"),
                    description =
                        event.params
                            .getAsString("description")
                            ?.take(SafeTxProposal.DESCRIPTION_MAX_LENGTH),
                )
            SAFE_TX_HASH_FIELDS ->
                base.copy(
                    safeTxGas = event.params.getAsBigInteger("safeTxGas"),
                    baseGas = event.params.getAsBigInteger("baseGas"),
                    gasPrice = event.params.getAsBigInteger("gasPrice"),
                    gasToken = addressParam(event, "gasToken"),
                    refundReceiver = addressParam(event, "refundReceiver"),
                )
            else -> base.copy(subcalls = subcalls(event) ?: base.subcalls)
        }
    }

    private fun subcalls(event: IndexedEvent): List<SafeSubcall>? {
        val targets = event.params.params["targets"] as? List<*> ?: return null
        val values = event.params.params["values"] as? List<*> ?: return null
        val datas = event.params.params["datas"] as? List<*> ?: return null
        val operations = event.params.params["operations"] as? List<*> ?: return null
        val labels = event.params.params["labels"] as? List<*> ?: return null
        val len = targets.size
        if (
            values.size != len || datas.size != len || operations.size != len || labels.size != len
        ) {
            return null
        }
        return (0 until len).map { i ->
            SafeSubcall(
                target = HexUtils.normalise(targets[i].toString()),
                value = toBigInteger(values[i]),
                data = datas[i].toString(),
                operation = toInt(operations[i]),
                label = labels[i].toString(),
            )
        }
    }

    private fun toBigInteger(v: Any?): BigInteger =
        when (v) {
            is BigInteger -> v
            is Number -> BigInteger.valueOf(v.toLong())
            is String -> v.toBigIntegerOrNull() ?: BigInteger.ZERO
            else -> BigInteger.ZERO
        }

    private fun toInt(v: Any?): Int =
        when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: 0
            else -> 0
        }

    private fun isEmitter(address: String?): Boolean =
        address != null && address.equals(emitterAddress, ignoreCase = true)

    private fun keyOf(event: IndexedEvent): Pair<String, String>? {
        val safe = safeOf(event) ?: return null
        val txHash = addressParam(event, "txHash") ?: return null
        return safe to txHash
    }
}
