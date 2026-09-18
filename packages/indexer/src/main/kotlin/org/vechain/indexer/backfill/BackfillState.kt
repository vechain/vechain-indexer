package org.vechain.indexer.backfill

import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

/** What each schema's deferrable indexes are doing, for the health indicators and the gauges. */
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

    /** A schema's phase, and how many of the indexes it declares are standing right now. */
    data class Progress(
        val phase: Phase = Phase.SERVING,
        val standing: Int = 0,
        val declared: Int = 0,
        val indexer: String = "",
    )

    private val progress = ConcurrentHashMap<String, Progress>()

    fun record(schema: String, phase: Phase) = update(schema) { it.copy(phase = phase) }

    /** The indexer that owns the schema, which is the name anyone reading a dashboard knows. */
    fun owned(schema: String, indexer: String) = update(schema) { it.copy(indexer = indexer) }

    /** Only where the catalogue has just been read, so the count is the catalogue's. */
    fun count(schema: String, standing: Int, declared: Int) =
        update(schema) { it.copy(standing = standing, declared = declared) }

    /** One index of a build finished; several builders report this at once. */
    fun built(schema: String) = update(schema) { it.copy(standing = it.standing + 1) }

    private fun update(schema: String, change: (Progress) -> Progress) {
        progress.compute(schema) { _, current -> change(current ?: Progress()) }
    }

    fun progress(): Map<String, Progress> = progress.toMap()

    /** The schemas rebuilding now, which hold the whole group still without being stalled. */
    fun building(): Set<String> = progress.filterValues { it.phase == Phase.BUILDING }.keys
}
