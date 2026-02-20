package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.time.Duration.Companion.milliseconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProcessorMetricsTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: ProcessorMetrics

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = ProcessorMetrics(registry)
    }

    @Test
    fun `observeProcessingDuration records timer`() {
        metrics.observeProcessingDuration("test-indexer", 100.milliseconds)

        val timer = registry.find("processor_duration").tag("indexer_name", "test-indexer").timer()

        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1)
    }

    @Test
    fun `incrementEventsCounter increments by count`() {
        metrics.incrementEventsCounter("test-indexer", 5.0)

        val counter =
            registry.find("processor_events_counter").tag("indexer_name", "test-indexer").counter()

        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(5.0)
    }
}
