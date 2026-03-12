package org.vechain.indexer.config

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.actuate.health.SystemHealth
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

internal enum class HealthLogLevel {
    INFO,
    WARN,
    ERROR,
}

internal data class HealthStatusLogPolicy(val level: HealthLogLevel, val label: String)

internal fun healthStatusLogPolicy(status: Status): HealthStatusLogPolicy =
    when (status) {
        Status.UP -> HealthStatusLogPolicy(HealthLogLevel.INFO, "healthy")
        Status.OUT_OF_SERVICE -> HealthStatusLogPolicy(HealthLogLevel.WARN, "NOT_READY")
        Status.DOWN -> HealthStatusLogPolicy(HealthLogLevel.ERROR, "UNHEALTHY")
        Status.UNKNOWN -> HealthStatusLogPolicy(HealthLogLevel.WARN, "UNKNOWN")
        else -> HealthStatusLogPolicy(HealthLogLevel.WARN, status.code)
    }

@Component
open class ApplicationHealth(
    private val healthEndpoint: HealthEndpoint,
    private val metrics: ApplicationHealthMetrics,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedDelay = 60 * 1000)
    fun logApplicationHealth() {
        val health = healthEndpoint.health() as SystemHealth
        val applicationPolicy = healthStatusLogPolicy(health.status)

        if (health.status != Status.UP) {
            logAtLevel(
                applicationPolicy.level,
                "Application is ${applicationPolicy.label}: {}",
                health.components.filter { it.value.status != Status.UP },
            )
        }

        health.components.forEach { (name, component) ->
            val componentPolicy = healthStatusLogPolicy(component.status)
            val (message, detail) =
                if (component.status == Status.UP) {
                    "Component $name is healthy" to null
                } else {
                    "Component $name is ${componentPolicy.label}: {}" to component
                }
            logAtLevel(
                componentPolicy.level,
                message,
                detail,
            )
            metrics.setComponentHealth(
                name,
                "component",
                when (component.status) {
                    Status.UP -> 1.0
                    Status.DOWN -> 0.0
                    else -> -1.0
                },
            )
        }
    }

    private fun logAtLevel(level: HealthLogLevel, message: String, detail: Any? = null) {
        when (level) {
            HealthLogLevel.INFO ->
                if (detail == null) {
                    logger.info(message)
                } else {
                    logger.info(message, detail)
                }
            HealthLogLevel.WARN ->
                if (detail == null) {
                    logger.warn(message)
                } else {
                    logger.warn(message, detail)
                }
            HealthLogLevel.ERROR ->
                if (detail == null) {
                    logger.error(message)
                } else {
                    logger.error(message, detail)
                }
        }
    }
}
