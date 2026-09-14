package org.vechain.indexer.timeseries

import org.slf4j.LoggerFactory
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.CacheUtils

/**
 * A series' newest row between batches: cached after commit, reread on a miss, dropped on rollback.
 */
class SeriesCursor<T : TimeFrameDocument>(private val read: () -> T?) {
    private val logger = LoggerFactory.getLogger(SeriesCursor::class.java)
    private var cached: T? = null

    /** The newest row; fails if any of [blockNumbers] is at or before it. */
    fun latestBefore(blockNumbers: Collection<Long>): T? {
        val latest = cached ?: read()
        val last = latest?.blockNumber
        if (last != null && blockNumbers.any { it <= last }) {
            throw IllegalStateException("Events include block ≤ last persisted block $last")
        }
        return latest
    }

    /**
     * The newest row for a block-driven series: the cache when it is [block]'s parent, else reread.
     */
    fun latestBefore(block: Block): T? {
        val hit = cached
        val latest =
            if (
                hit != null && hit.blockNumber == block.number - 1 && hit.blockId == block.parentID
            ) {
                hit
            } else {
                if (hit != null) {
                    logger.info(
                        "Cache miss for block {}: cached blockNumber={}, expected={}, parentID match={}",
                        block.number,
                        hit.blockNumber,
                        block.number - 1,
                        hit.blockId == block.parentID,
                    )
                }
                read()
            } ?: return null

        if (block.number <= latest.blockNumber) {
            throw IllegalStateException(
                "Block ${block.number} is at or before last persisted block ${latest.blockNumber}"
            )
        }
        if (block.number > latest.blockNumber + 1) {
            logger.warn(
                "Forward gap detected: block {} is {} blocks ahead of last persisted block {}",
                block.number,
                block.number - latest.blockNumber,
                latest.blockNumber,
            )
        }
        return latest
    }

    /**
     * Keeps the cache current through a block that wrote no row, as [latest] moved to that block.
     */
    fun advance(latest: T) {
        cached = latest
    }

    /** Caches the newest of [rows] once the surrounding transaction commits. */
    fun commit(rows: List<T>) {
        val latest = rows.maxByOrNull { it.blockNumber } ?: return
        CacheUtils.updateAfterCommit(latest, { cached = it }, { cached = null })
    }

    /** Called from the processor's rollback path, so the cache cannot outrun the table. */
    fun reset() {
        cached = null
    }
}
