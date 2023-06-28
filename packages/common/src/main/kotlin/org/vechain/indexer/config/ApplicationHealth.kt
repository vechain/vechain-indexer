package org.vechain.indexer.config

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.actuate.health.SystemHealth
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
open class ApplicationHealth(private val healthEndpoint: HealthEndpoint) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedDelay = 30 * 1000)
    fun logApplicationHealth() {
        val health = healthEndpoint.health() as SystemHealth

        if (health.status != Status.UP) {
            logger.error(
                "Application is UNHEALTHY: {}",
                health.components.filter { it.value.status != Status.UP }
            )
        }
    }
}
