package org.vechain.indexer.config

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.scheduler.Schedulers
import java.util.concurrent.TimeUnit


@Profile("indexer", "blocks-proxy")
@Component
class ThorHealthIndicator(private val thorRest: WebClient) : HealthIndicator {
    override fun health(): Health {
        val key = "VeChainThor"
        return try {
            performBestBlockTest()
            Health.up().withDetail(key, "Available").build()
        } catch (e: Exception) {
            Health.down().withDetail(key, "Unavailable").build()
        }
    }

    private fun performBestBlockTest() {
        thorRest.get()
            .uri("/blocks/best?expanded=false")
            .retrieve()
            .toEntity(String::class.java)
            .subscribeOn(Schedulers.boundedElastic())
            .toFuture()
            .get(10L, TimeUnit.SECONDS)
    }
}