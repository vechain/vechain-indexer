package org.vechain.indexer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.slack.api.Slack
import com.slack.api.webhook.Payload
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.actuate.health.SystemHealth
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

const val SLACK_MESSAGE_INTERVAL_MINUTES: Long = 30

internal enum class HealthLogLevel {
    INFO,
    WARN,
    ERROR,
}

internal data class HealthStatusLogPolicy(
    val level: HealthLogLevel,
    val label: String,
    val shouldSendSlack: Boolean,
)

internal fun healthStatusLogPolicy(status: Status): HealthStatusLogPolicy =
    when (status) {
        Status.UP -> HealthStatusLogPolicy(HealthLogLevel.INFO, "healthy", shouldSendSlack = false)
        Status.OUT_OF_SERVICE ->
            HealthStatusLogPolicy(HealthLogLevel.WARN, "NOT_READY", shouldSendSlack = false)
        Status.DOWN ->
            HealthStatusLogPolicy(HealthLogLevel.ERROR, "UNHEALTHY", shouldSendSlack = true)
        Status.UNKNOWN ->
            HealthStatusLogPolicy(HealthLogLevel.WARN, "UNKNOWN", shouldSendSlack = false)
        else -> HealthStatusLogPolicy(HealthLogLevel.WARN, status.code, shouldSendSlack = false)
    }

@Component
open class ApplicationHealth(
    private val healthEndpoint: HealthEndpoint,
    private val metrics: ApplicationHealthMetrics,
    @param:Value("\${spring.application.name}") private val applicationName: String,
    @param:Value("\${slack.webhook.url}") private val slackWebhookUrl: String? = null,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val objectMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    private var lastAlertTime = LocalDateTime.now().minusMinutes(SLACK_MESSAGE_INTERVAL_MINUTES)

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
            if (applicationPolicy.shouldSendSlack) {
                sendSlackMessage(health)
            }
        }

        health.components.forEach { (name, component) ->
            val componentPolicy = healthStatusLogPolicy(component.status)
            logAtLevel(
                componentPolicy.level,
                if (component.status == Status.UP) {
                    "Component $name is healthy"
                } else {
                    "Component $name is ${componentPolicy.label}: {}"
                },
                component,
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

    private fun sendSlackMessage(health: SystemHealth) {

        if (slackWebhookUrl == null) {
            logger.warn("Slack webhook url is not configured")
            return
        }

        if (
            LocalDateTime.now().minusMinutes(SLACK_MESSAGE_INTERVAL_MINUTES).isBefore(lastAlertTime)
        ) {
            return
        }

        try {
            val slack = Slack.getInstance()

            val payload: Payload =
                Payload.builder()
                    .text(
                        ":alert: :alert: $applicationName is not healthy :alert: :alert: \n ```\n ${objectMapper.writeValueAsString(health)}\n```"
                    )
                    .build()

            slack.send(slackWebhookUrl, payload)

            lastAlertTime = LocalDateTime.now()
        } catch (e: Exception) {
            logger.error("Failed to send slack message", e)
        }
    }
}
