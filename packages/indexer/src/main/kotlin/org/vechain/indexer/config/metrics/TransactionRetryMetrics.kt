package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

@Component
class TransactionRetryMetrics(private val registry: MeterRegistry) {

    private val retryCounters = ConcurrentHashMap<String, Counter>()
    private val exhaustedCounters = ConcurrentHashMap<String, Counter>()

    fun incrementRetry(targetClass: String, errorCode: Int) {
        val key = "$targetClass:$errorCode"
        retryCounters
            .computeIfAbsent(key) {
                Counter.builder("transaction_retry_total")
                    .description("Total transient transaction retries")
                    .tag("target_class", targetClass)
                    .tag("error_code", errorCode.toString())
                    .register(registry)
            }
            .increment()
    }

    fun incrementExhausted(targetClass: String) {
        exhaustedCounters
            .computeIfAbsent(targetClass) {
                Counter.builder("transaction_retry_exhausted_total")
                    .description("Total transient transaction retries that exhausted all attempts")
                    .tag("target_class", targetClass)
                    .register(registry)
            }
            .increment()
    }
}
