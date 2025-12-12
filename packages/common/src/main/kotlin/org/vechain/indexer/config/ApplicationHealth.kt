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

        if (health.status != Status.UP) {
            logger.error(
                "Application is UNHEALTHY: {}",
                health.components.filter { it.value.status != Status.UP },
            )
            sendSlackMessage(health)
        }

        health.components.forEach { (name, component) ->
            if (component.status != Status.UP) {
                logger.error("Component $name is UNHEALTHY: {}", component)
            } else {
                logger.info("Component $name is healthy")
            }
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
