package org.vechain.indexer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.slack.api.Slack
import com.slack.api.webhook.Payload
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.actuate.health.SystemHealth
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
open class ApplicationHealth(
    private val healthEndpoint: HealthEndpoint,
    @Value("\${spring.application.name}") private val applicationName: String,
    @Value("\${slack.webhook.url}") private val slackWebhookUrl: String? = null
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val objectMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    @Scheduled(fixedDelay = 60 * 1000)
    fun logApplicationHealth() {
        val health = healthEndpoint.health() as SystemHealth

        if (health.status != Status.UP) {
            logger.error(
                "Application is UNHEALTHY: {}",
                health.components.filter { it.value.status != Status.UP }
            )
            sendSlackMessage(health)
        }
    }

    private fun sendSlackMessage(health: SystemHealth) {

        if (slackWebhookUrl == null) {
            logger.warn("Slack webhook url is not configured")
            return
        }

        try {
            val slack = Slack.getInstance()

            val payload: Payload =
                Payload.builder()
                    .text(
                        "$applicationName is not healthy!! \n \n ${objectMapper.writeValueAsString(health)}"
                    )
                    .build()

            slack.send(slackWebhookUrl, payload)
        } catch (e: Exception) {
            logger.error("Failed to send slack message", e)
        }
    }
}
