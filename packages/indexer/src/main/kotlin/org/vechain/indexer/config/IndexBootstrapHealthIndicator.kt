package org.vechain.indexer.config

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class IndexBootstrapHealthIndicator(private val indexBootstrapState: IndexBootstrapState) :
    HealthIndicator {
    override fun health(): Health {
        val snapshot = indexBootstrapState.snapshot()
        val builder =
            when (snapshot.status) {
                IndexBootstrapState.Status.READY -> Health.up()
                IndexBootstrapState.Status.FAILED -> Health.down()
                IndexBootstrapState.Status.NOT_STARTED,
                IndexBootstrapState.Status.RUNNING -> Health.outOfService()
            }

        return builder
            .withDetail("status", snapshot.status.name)
            .withDetail("message", snapshot.message)
            .build()
    }
}
