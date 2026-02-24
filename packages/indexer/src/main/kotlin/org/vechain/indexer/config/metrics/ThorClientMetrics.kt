package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.vechain.indexer.config.DefaultMetrics

@Component
class ThorClientMetrics(private val registry: MeterRegistry) {

    private val responseCodeCounters = ConcurrentHashMap<String, Counter>()
    private val requestDurationTimers = ConcurrentHashMap<String, Timer>()

    fun recordResponseCode(method: String, path: String, code: String) {
        val endpoint = endpointName(method, path)
        val key = "$endpoint:$code"
        responseCodeCounters
            .computeIfAbsent(key) {
                Counter.builder("thor_client_response_codes_total")
                    .description("Count of response codes from Thor client")
                    .tag("endpoint", endpoint)
                    .tag("code", code)
                    .register(registry)
            }
            .increment()
    }

    fun observeRequestDuration(method: String, path: String, duration: Duration) {
        val endpoint = endpointName(method, path)
        requestDurationTimers
            .computeIfAbsent(endpoint) {
                DefaultMetrics.newTimer("thor_client_request_duration")
                    .description("Duration of Thor client requests")
                    .tag("endpoint", endpoint)
                    .register(registry)
            }
            .record(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    private fun endpointName(method: String, path: String): String {
        if (method == HttpMethod.POST.toString()) {
            return "POST $path"
        }
        var cleanPath = path
        cleanPath = hexRegex.replace(cleanPath, "{hex}")
        cleanPath = numberRegex.replace(cleanPath, "{number}")
        return "$method $cleanPath"
    }

    companion object {
        private val numberRegex = Regex("""[0-9]+""")
        private val hexRegex = Regex("""0x[0-9a-fA-F]+""")
    }
}
