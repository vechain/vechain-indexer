package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import kotlin.collections.iterator
import kotlin.collections.plusAssign
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger

@Profile("stargate", "vtho-claimed-by-block")
@Service
open class VthoClaimedByBlockService(private val repository: VthoClaimedByBlockRepository) {
    private val legacyEventNames =
        setOf("STARGATE_CLAIM_REWARDS_BASE_LEGACY", "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY")

    open fun processEvents(events: List<IndexedEvent>): List<VthoClaimedByBlock> {
        if (events.isEmpty()) return emptyList()

        val latest = repository.getLatestRecord()
        val lastBlock = latest?.blockNumber
        val startingLatestTotal = latest?.total ?: BigInteger.ZERO
        val startingLegacyTotal = latest?.legacyRewards ?: BigInteger.ZERO

        if (lastBlock != null && events.any { it.blockNumber <= lastBlock }) {
            throw IllegalStateException("Events include block ≤ last persisted block $lastBlock")
        }

        val grouped = events.groupBy { it.blockNumber }.toSortedMap()

        var runningLatest = startingLatestTotal
        var runningLegacy = startingLegacyTotal

        val output = mutableListOf<VthoClaimedByBlock>()

        for ((blockNum, blockEvents) in grouped) {
            var blockLatestSum = BigInteger.ZERO
            var blockLegacySum = BigInteger.ZERO

            for (event in blockEvents) {
                val value = event.requireValue()
                if (event.eventType in legacyEventNames) {
                    blockLegacySum += value
                } else {
                    blockLatestSum += value
                }
            }

            runningLatest += blockLatestSum
            runningLegacy += blockLegacySum

            val rep = blockEvents.first()
            output +=
                VthoClaimedByBlock(
                    blockId = rep.blockId,
                    blockNumber = blockNum,
                    blockTimestamp = rep.blockTimestamp,
                    total = runningLatest, // ✅ only latest rewards
                    legacyRewards = runningLegacy, // ✅ only legacy
                )
        }

        return output
    }

    open fun saveRecords(record: List<VthoClaimedByBlock>) {
        repository.saveAll(record)
    }
}

/**
 * Helper that enforces presence of the required "value" param and throws with context if missing.
 */
private fun IndexedEvent.requireValue(): BigInteger =
    this.params.getAsBigInteger("value")
        ?: throw IllegalStateException(
            "Event for block $blockNumber (blockId=$blockId) is missing required 'value' parameter"
        )
