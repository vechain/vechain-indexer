package org.vechain.indexer.timing

/** Implementations can provide additional context that will be appended to timing logs. */
interface TimingContextAware {
    fun timingContext(): String
}
