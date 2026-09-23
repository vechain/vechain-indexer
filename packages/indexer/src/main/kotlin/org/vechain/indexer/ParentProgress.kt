package org.vechain.indexer

/** Refuses an as-of read of [parent]'s tables at a block [parent] has not committed. */
class ParentProgress(private val parent: Indexer) {
    fun requireCommitted(blockNumber: Long) {
        val next = parent.getCurrentBlockNumber()
        check(next > blockNumber) {
            "${parent.name} has not committed block $blockNumber yet (its next block is $next)"
        }
    }
}
