package org.vechain.indexer

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.vechain.indexer.config.metrics.ProcessorMetrics

class ProcessingMetricsTracker(
    private val indexerName: String,
    private val processorMetrics: ProcessorMetrics,
) {
    private var lastProcessedBlock: Long = -1L
    private var lastIterationEnd: TimeMark? = null

    fun reset() {
        lastProcessedBlock = -1L
        lastIterationEnd = null
    }

    fun recordEvents(eventCount: Int) {
        processorMetrics.incrementEventsCounter(indexerName, eventCount.toDouble())
    }

    fun record(entry: IndexingResult, processingDuration: Duration) {
        val currentBlock = entry.latestBlockNumber()
        val blocksInEntry =
            when (entry) {
                is IndexingResult.BlockResult -> 1
                is IndexingResult.LogResult -> {
                    if (lastProcessedBlock >= 0 && currentBlock > lastProcessedBlock) {
                        (currentBlock - lastProcessedBlock).toInt()
                    } else {
                        1
                    }
                }
            }.coerceAtLeast(1)
        lastProcessedBlock = currentBlock

        val perBlockDuration = processingDuration / blocksInEntry
        repeat(blocksInEntry) {
            processorMetrics.observeProcessingDuration(indexerName, perBlockDuration)
        }

        val isSyncing = entry.status == Status.SYNCING || entry.status == Status.FAST_SYNCING
        if (isSyncing) {
            lastIterationEnd?.let { previousEnd ->
                val cycleTime = previousEnd.elapsedNow()
                val perBlockCycleTime = cycleTime / blocksInEntry
                repeat(blocksInEntry) {
                    processorMetrics.observeBlockCycleTime(indexerName, perBlockCycleTime)
                }
            }
            lastIterationEnd = TimeSource.Monotonic.markNow()
        } else {
            lastIterationEnd = null
        }
    }
}
