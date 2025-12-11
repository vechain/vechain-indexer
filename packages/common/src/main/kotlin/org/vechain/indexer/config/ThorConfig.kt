package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.metrics.Metrics

@Configuration
open class ThorConfig(
    @Value("\${thor.url}") private val thorUrl: String,
    private val metrics: Metrics,
) {

    @Bean
    open fun thorRest(): WebClient {
        val size = 16 * 1024 * 1024
        val strategies =
            ExchangeStrategies.builder()
                .codecs { codecs: ClientCodecConfigurer ->
                    codecs.defaultCodecs().maxInMemorySize(size)
                }
                .build()

        return WebClient.builder()
            .exchangeStrategies(strategies)
            .baseUrl(thorUrl)
            .defaultHeaders { it.add("X-Project-Id", "veworld-indexer") }
            .filter(metricsFilter())
            .build()
    }

    private fun metricsFilter(): ExchangeFilterFunction {
        return ExchangeFilterFunction { request, next ->
            val startTime = System.nanoTime()
            val method = request.method().toString()
            val path = request.url().path

            next
                .exchange(request)
                .doOnNext { response ->
                    val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
                    metrics.observeRequestDuration(method, path, durationMs)
                    metrics.recordResponseCode(
                        method,
                        path,
                        response.statusCode().value().toString(),
                    )
                }
                .doOnError { _ ->
                    val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
                    metrics.observeRequestDuration(method, path, durationMs)
                    metrics.recordResponseCode(method, path, "unknown-exception")
                }
        }
    }
}
