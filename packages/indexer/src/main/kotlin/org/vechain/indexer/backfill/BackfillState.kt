package org.vechain.indexer.backfill

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/** What each schema's deferrable indexes are doing, for the health indicators and the gauge. */
@Component
class BackfillState {

    enum class Phase {
        /** Every deferrable index stands; queries and the API see the schema they expect. */
        SERVING,
        /** The indexes are dropped and the indexer is catching up without them. */
        BACKFILL,
        /** The indexer has stopped taking blocks while the indexes are rebuilt. */
        BUILDING,
    }

    private val phases = ConcurrentHashMap<String, Phase>()

    fun record(schema: String, phase: Phase) {
        phases[schema] = phase
    }

    fun phases(): Map<String, Phase> = phases.toMap()

    /** The schemas rebuilding now, which hold the whole group still without being stalled. */
    fun building(): Set<String> = phases.filterValues { it == Phase.BUILDING }.keys
}
