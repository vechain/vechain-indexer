package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.thor.model.BlockIdentifier

/**
 * [IndexerStore] over one Postgres schema: resume from `indexer_state`'s checkpoint, roll back
 * through the schema's tables, and resync by truncating them when `indexer.version.*` is raised.
 */
open class PostgresIndexerStore(
    protected val schema: String,
    private val tables: PostgresIndexerTables,
    private val state: IndexerStateRepository,
    private val checkpointProperties: CheckpointProperties,
    private val horizon: InlineVersioningProperties = InlineVersioningProperties(),
) : IndexerStore {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // Set after each processed entry; read by the shutdown thread, hence @Volatile.
    @Volatile private var lastObserved: BlockIdentifier? = null
    private var lastSavedNanos: Long? = null
    private var lastPrunedBucket: Long = -1

    /** False for a schema whose newest row is the resume point, such as `blocks`. */
    protected open val usesCheckpoint: Boolean = true

    override fun lastSynced(): BlockIdentifier? = state.checkpoint(schema)

    /** Runs inside the processor's Postgres transaction, so the data and the marker agree. */
    override fun rollbackFrom(blockNumber: Long) {
        tables.rollbackFrom(blockNumber)
        lastObserved = null
        if (usesCheckpoint) state.saveCheckpoint(schema, BlockIdentifier(blockNumber - 1, null))
    }

    /** Throttled by `indexer.checkpoint.save-interval-seconds`; failures only log. */
    open fun onProcessed(latest: BlockIdentifier) {
        lastObserved = latest
        pruneIfDue(latest.number)
        if (!usesCheckpoint) return
        val now = System.nanoTime()
        val interval = checkpointProperties.saveIntervalSeconds * 1_000_000_000L
        if (lastSavedNanos?.let { now - it < interval } == true) return
        try {
            state.saveCheckpoint(schema, latest)
            lastSavedNanos = now
        } catch (e: Exception) {
            logger.warn("Failed to save checkpoint for {} at block {}", schema, latest.number, e)
        }
    }

    // Outside the block transaction; the horizon is `indexer.inline-versioning.block-window`.
    private fun pruneIfDue(blockNumber: Long) {
        val bucket = blockNumber / PRUNE_EVERY_BLOCKS
        if (bucket <= lastPrunedBucket) return
        lastPrunedBucket = bucket
        try {
            tables.prune(blockNumber - horizon.blockWindow)
        } catch (e: Exception) {
            logger.warn("Failed to prune {} below block {}", schema, blockNumber, e)
        }
    }

    /** Unthrottled; called on shutdown so a clean restart resumes from the last processed block. */
    open fun flushCheckpoint() {
        val block = lastObserved ?: return
        if (!usesCheckpoint) return
        try {
            state.saveCheckpoint(schema, block)
            logger.info("{}: flushed checkpoint at block {} on shutdown", schema, block.number)
        } catch (e: Exception) {
            logger.error("Failed to flush checkpoint for {} at block {}", schema, block.number, e)
        }
    }

    /** Mirrors the Mongo version check: a raised configured version empties the schema. */
    open fun ensureVersion(version: Int) {
        val stored = state.storedVersion(schema)
        when {
            stored == null -> {
                logger.info("{}: no version recorded; recording version {}", schema, version)
                state.recordVersion(schema, version)
            }
            stored < version -> {
                logger.info(
                    "{}: version {} is below {}; truncating for a resync",
                    schema,
                    stored,
                    version,
                )
                state.resync(schema, version, tables)
            }
        }
    }

    companion object {
        const val PRUNE_EVERY_BLOCKS = 1_000L
    }
}
