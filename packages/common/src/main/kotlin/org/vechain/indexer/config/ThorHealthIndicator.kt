package org.vechain.indexer.config

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.service.ThorService

@Profile("indexer", "blocks-proxy")
@Component
class ThorHealthIndicator(private val thorService: ThorService) : HealthIndicator {
    override fun health(): Health {
        val key = "VeChainThor"
        return try {
            thorService.getBlock(1)
            Health.up().withDetail(key, "Available").build()
        } catch (e: Exception) {
            Health.down().withDetail(key, "Unavailable").build()
        }
    }
}