package org.vechain.indexer.b3tr.round

import org.slf4j.LoggerFactory
import org.vechain.indexer.b3tr.action.ActionSummaryUtils
import org.vechain.indexer.event.model.generic.IndexedEvent

object RoundUtils {

    private val logger = LoggerFactory.getLogger(RoundUtils::class.java)

    /**
     * Discover what the current round is.
     *
     * Priority: (1) EmissionDistributed event in the current block, (2) latest DB record, (3)
     * provided [currRound] as the final fallback.
     *
     * @param emissionDistributedEvents events used to check for a change of round id
     * @param currRound the current in-memory round id (non-null)
     */
    fun discoverRoundId(emissionDistributedEvents: List<IndexedEvent>, currRound: Int): Int {
        // If we observed a round-change event in this block, it takes highest priority
        if (emissionDistributedEvents.isNotEmpty()) {
            require(emissionDistributedEvents.size == 1) {
                "Expected only one EmissionDistributed or EmissionDistributedV2 event per block, but found ${emissionDistributedEvents.size} in block ${emissionDistributedEvents[0].blockNumber}"
            }
            val eventRoundId = ActionSummaryUtils.getCycle(emissionDistributedEvents[0])

            if (eventRoundId != currRound) {
                logger.info(
                    "Round has changed from $currRound to $eventRoundId at block ${emissionDistributedEvents[0].blockNumber} (via EmissionDistributed event)"
                )
            }
            return eventRoundId
        }

        return currRound
    }
}
