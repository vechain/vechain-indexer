package org.vechain.indexer.backfill

import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import org.slf4j.LoggerFactory
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.backfill.BackfillState.Phase
import org.vechain.indexer.chain.ChainHead
import org.vechain.indexer.postgres.IndexBuilder
import org.vechain.indexer.postgres.IndexSet

/**
 * Keeps one schema's API-only indexes out of a backfill. Far enough behind the head, the indexer
 * reads none of them and every row it writes pays for all of them, so they are dropped; at the head
 * they are rebuilt, several at a time, while the processor stops taking blocks. The phase is
 * derived from the catalogue and the runner on every entry — nothing is stored, so an interrupted
 * backfill picks up where it left off rather than from a flag that outlived its state.
 */
class BackfillCoordinator(
    private val indexSet: IndexSet,
    private val builder: IndexBuilder,
    private val chainHead: ChainHead,
    private val properties: BackfillProperties,
    private val state: BackfillState,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val schema = indexSet.schema

    private val declared = indexSet.indexes.size

    private var phase: Phase? = null
    private var neededChecked = false
    private var retryAfterNanos: Long? = null

    /** The processor names itself once built: a schema is not what a dashboard is read by. */
    fun ownedBy(indexer: String) = state.owned(schema, indexer)

    /** Runs before the entry is processed, outside its transaction; may block for a rebuild. */
    suspend fun beforeEntry(entry: IndexingResult) {
        if (!neededChecked) buildNeeded()
        if (!properties.enabled) return
        val behind = (chainHead.bestBlockNumber() ?: return) - entry.latestBlockNumber()
        when (phase) {
            null -> begin(behind)
            Phase.SERVING -> if (behind > properties.enterBehindBlocks) enterBackfill(behind)
            Phase.BACKFILL -> if (entry.status == Status.FULLY_SYNCED) rebuild()
            // Unreachable: the rebuild blocks this call until the phase has moved on.
            Phase.BUILDING -> {}
        }
    }

    /** The first entry is the first point at which both the gap and the catalogue are known. */
    private fun begin(behind: Long) {
        if (behind > properties.enterBehindBlocks) return enterBackfill(behind)
        val missing = builder.missing(indexSet)
        count(declared - missing.size)
        enter(Phase.SERVING)
        if (missing.isEmpty()) return
        // Either a release declared an index, or a snapshot was taken mid-backfill and restored.
        // Neither can pause a colour that may be serving reads, so these grow underneath it.
        logger.info(
            "{}: building {} indexes concurrently under a running indexer",
            schema,
            missing.size,
        )
        buildInBackground { builder.buildConcurrently(indexSet, missing, ::built) }
    }

    // Outside the phase and the enabled flag: these are the indexer's own, which a migration could
    // not build in time, and a backfill is when their absence costs the write path most.
    private fun buildNeeded() {
        neededChecked = true
        val missing = builder.missing(indexSet, indexSet.needed)
        if (missing.isEmpty()) return
        logger.info(
            "{}: building {} of the indexer's own indexes concurrently",
            schema,
            missing.size,
        )
        buildInBackground { builder.buildConcurrently(indexSet, missing) }
    }

    private fun enterBackfill(behind: Long) {
        val standing = declared - builder.missing(indexSet).size
        logger.info(
            "{}: {} blocks behind the head; dropping {} of the API's {} indexes",
            schema,
            behind,
            standing,
            declared,
        )
        if (standing > 0) builder.drop(indexSet)
        count(0)
        enter(Phase.BACKFILL)
    }

    private suspend fun rebuild() {
        // Subtracted rather than compared, because nanoTime's origin can be negative.
        if (retryAfterNanos?.let { System.nanoTime() - it < 0 } == true) return
        val missing = builder.missing(indexSet)
        count(declared - missing.size)
        if (missing.isEmpty()) return enter(Phase.SERVING)
        logger.info("{}: at the head; pausing to rebuild {} indexes", schema, missing.size)
        enter(Phase.BUILDING)
        val done = CompletableDeferred<Unit>()
        buildInBackground(done) { builder.build(indexSet, missing, ::built) }
        try {
            done.await()
            count(declared)
            enter(Phase.SERVING)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("$schema: rebuilding the API's indexes failed; retrying in $RETRY", e)
            enter(Phase.BACKFILL)
            retryAfterNanos = System.nanoTime() + RETRY.inWholeNanoseconds
        }
    }

    // On its own thread, so awaiting it stays cancellable: a build sends nothing for hours and
    // shutdown would otherwise wait out its drain timeout before the task could exit.
    private fun buildInBackground(done: CompletableDeferred<Unit>? = null, build: () -> Unit) {
        thread(isDaemon = true, name = "$schema-index-build") {
            try {
                build()
                done?.complete(Unit)
            } catch (e: Exception) {
                if (done == null)
                    logger.error("$schema: the index build failed; the next start retries", e)
                else done.completeExceptionally(e)
            }
        }
    }

    private fun enter(phase: Phase) {
        this.phase = phase
        state.record(schema, phase)
    }

    private fun count(standing: Int) = state.count(schema, standing, declared)

    private fun built() = state.built(schema)

    private companion object {
        val RETRY = 10.minutes
    }
}
