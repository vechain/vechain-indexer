package org.vechain.indexer

import com.mongodb.internal.connection.Time
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.core.metrics.Histogram
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.version.IndexerVersionService

// Use a histogram to represent a distribution of values
private val processingDuration =
    Histogram.builder()
        .name("processor_duration_counter")
        .labelNames("indexer_name")
        .classicUpperBounds(0.01, 1.0, 5.0, 10.0, 50.0, 100.0, 250.0, 500.0, 1000.0) // milliseconds
        .register()

// Use a counter to represent a cumulative value, it only goes up
private val eventsCounter =
    Counter.builder().name("processor_events_counter").labelNames("indexer_name").register()

// Use a gauge to represent a current number
private val bestBlockGauge =
    Gauge.builder().name("best_block_gauge").labelNames("indexer_name").register()

abstract class BaseProcessor(
    private val repository: BaseIndexedRepository<*, *>,
    private val indexerVersionService: IndexerVersionService,
    private val indexerName: String,
) : IndexerProcessor {

    abstract fun processEntry(entry: IndexingResult)

    override fun process(entry: IndexingResult) {
        val start = Time.nanoTime()
        try {
            processEntry(entry)
            eventsCounter.labelValues(indexerName).inc(entry.events().size.toLong())
            bestBlockGauge.labelValues(indexerName).inc()
        } finally {
            val duration = Time.nanoTime() - start
            val durationMS = duration.toDouble() / 1_000_000.0
            processingDuration.labelValues(indexerName).observe(durationMS)
        }
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        val latestRecords =
            repository.getLatestRecord()?.let {
                BlockIdentifier(number = it.blockNumber, id = it.blockId)
            }
        val lastProcessedBlock = indexerVersionService.getLastProcessedBlock(indexerName)

        return when {
            latestRecords != null && lastProcessedBlock != null -> {
                if (latestRecords.number <= lastProcessedBlock.number) {
                    lastProcessedBlock
                } else {
                    latestRecords
                }
            }
            latestRecords != null -> latestRecords
            lastProcessedBlock != null -> lastProcessedBlock
            else -> null
        }
    }

    override fun rollback(blockNumber: Long) =
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)
}
