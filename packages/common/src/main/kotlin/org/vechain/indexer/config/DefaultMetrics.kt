package org.vechain.indexer.config

import io.micrometer.core.instrument.Timer
import java.time.Duration

object DefaultMetrics {

    fun newTimer(name: String): Timer.Builder {
        return Timer.builder(name)
            .publishPercentileHistogram()
            .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
            .serviceLevelObjectives(
                Duration.ofMillis(1),
                Duration.ofMillis(2),
                Duration.ofMillis(5),
                Duration.ofMillis(10),
                Duration.ofMillis(25),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
            )
            .minimumExpectedValue(Duration.ofMillis(1))
            .maximumExpectedValue(Duration.ofSeconds(5))
            .distributionStatisticExpiry(Duration.ofMinutes(1))
            .distributionStatisticBufferLength(3)
    }
}
