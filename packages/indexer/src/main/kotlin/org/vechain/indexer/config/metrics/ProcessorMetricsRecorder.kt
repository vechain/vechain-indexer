package org.vechain.indexer.config.metrics

import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.vechain.indexer.IndexingResult

class ProcessorMetricsRecorder(
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
        if (lastProcessedBlock >= 0 && currentBlock <= lastProcessedBlock) return

        val blocksInEntry =
            when (entry) {
                is IndexingResult.BlockResult -> 1
                is IndexingResult.LogResult -> {
                    if (lastProcessedBlock >= 0) {
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

        // Cycle time is recorded for all statuses (not just SYNCING) to ensure it tracks
        // the same population of events as processing duration. Gating on status previously
        // caused cycle time to appear lower than processing time in aggregate metrics.
        lastIterationEnd?.let { previousEnd ->
            val cycleTime = previousEnd.elapsedNow()
            val perBlockCycleTime = cycleTime / blocksInEntry
            repeat(blocksInEntry) {
                processorMetrics.observeBlockCycleTime(indexerName, perBlockCycleTime)
            }
        }
        lastIterationEnd = TimeSource.Monotonic.markNow()
    }
}
