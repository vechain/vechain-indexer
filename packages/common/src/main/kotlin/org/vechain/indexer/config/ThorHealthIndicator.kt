package org.vechain.indexer.config

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.thor.model.Block

@Profile("blocks-proxy")
@Component
class ThorHealthIndicator(private val thorRest: WebClient) : HealthIndicator {
    override fun health(): Health {
        val key = "VeChainThor"
        return try {
            getBlock(1)
            Health.up().withDetail(key, "Available").build()
        } catch (e: Exception) {
            Health.down().withDetail(key, "Unavailable").build()
        }
    }

    fun getBlock(number: Long): Block {

        return thorRest
            .get()
            .uri("/blocks/$number?expanded=false")
            .retrieve()
            .bodyToMono(Block::class.java)
            .block()
            ?: throw Exception("Failed thorRest healthcheck")
    }
}