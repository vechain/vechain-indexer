package org.vechain.indexer.stargate

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasAbiData

@Profile("stargate", "vtho-generated-by-block")
@Service
open class VthoGeneratedByBlockService(private val repository: VthoGeneratedByBlockRepository) {
    /**
     * Process a single block and build a cumulative VTHO record.
     *
     * Behavior:
     * - Fails fast if the block number is <= the last persisted block (no reprocessing allowed).
     * - Starts from the latest persisted totals (or zero if none).
     * - Adds the current VTHO balance from call responses.
     * - Iterates through all events in the block, summing their required "value" fields.
     * - Produces one [VthoGeneratedByBlock] record that represents the running totals at this
     *   block.
     *
     * Returns null if both the event list is empty and no ABI data is available from call
     * responses.
     */
    open fun processBlock(
        events: List<IndexedEvent>,
        block: Block,
        callResponses: List<InspectionResult>,
    ): VthoGeneratedByBlock? {
        if (events.isEmpty() && !callResponses[0].hasAbiData()) return null

        val latestRecord = repository.getLatestRecord()
        val lastPersistedBlockNumber = latestRecord?.blockNumber

        // Fail fast on same/earlier block numbers than the last persisted block
        if (lastPersistedBlockNumber != null && block.number <= lastPersistedBlockNumber) {
            throw IllegalStateException(
                "Block ${block.number} is <= last persisted blockNumber $lastPersistedBlockNumber"
            )
        }

        var runningTotalRewardsClaimed = latestRecord?.rewardsClaimed ?: BigInteger.ZERO

        // Get vthoBalance from callResponses
        val vthoBalance = getBalanceOf(callResponses)

        var runningTotal = vthoBalance + runningTotalRewardsClaimed
        events.forEach { event ->
            val value = event.requireValue()
            runningTotal += value
            runningTotalRewardsClaimed += value
        }

        if (runningTotal == BigInteger.ZERO) return null

        return VthoGeneratedByBlock(
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            total = runningTotal,
            rewardsClaimed = runningTotalRewardsClaimed,
        )
    }

    open fun save(record: VthoGeneratedByBlock) {
        repository.save(record)
    }

    /** Resolve the balance of VTHO from the first call response (if any). */
    fun getBalanceOf(responses: List<InspectionResult>): BigInteger {
        if (responses.isEmpty() || !responses[0].hasAbiData()) {
            return BigInteger.ZERO
        }
        val decoded =
            FunctionReturnDecoder.decode(
                responses[0].data,
                listOf(InputOutput("uint256", "balance", "uint256")),
            )
        return decoded["balance"] as BigInteger
    }

    /**
     * Helper that enforces presence of the required "value" param and throws with context if
     * missing.
     */
    private fun IndexedEvent.requireValue(): BigInteger =
        this.params.getAsBigInteger("value")
            ?: throw IllegalStateException(
                "Event for block $blockNumber (blockId=$blockId) is missing required 'value' parameter"
            )
}
