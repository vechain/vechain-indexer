package org.vechain.indexer.config

import kotlin.time.TimeSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
open class ThorConfig(
    @Value("\${thor.url}") private val thorUrl: String,
    private val metrics: ThorRestMetrics,
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
            val start = TimeSource.Monotonic.markNow()
            val method = request.method().toString()
            val path = request.url().path

            next
                .exchange(request)
                .doOnNext { response ->
                    metrics.observeRequestDuration(method, path, start.elapsedNow())
                    metrics.recordResponseCode(
                        method,
                        path,
                        response.statusCode().value().toString(),
                    )
                }
                .doOnError { _ ->
                    metrics.observeRequestDuration(method, path, start.elapsedNow())
                    metrics.recordResponseCode(method, path, "unknown-exception")
                }
        }
    }
}
