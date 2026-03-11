package org.vechain.indexer.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

class IndexBootstrapHealthIndicatorTest {
    private val indexBootstrapState = IndexBootstrapState()
    private val indicator = IndexBootstrapHealthIndicator(indexBootstrapState)

    @Test
    fun `health is out of service before bootstrap completes`() {
        val health = indicator.health()

        assertEquals(Status.OUT_OF_SERVICE, health.status)
        assertEquals("NOT_STARTED", health.details["status"])
    }

    @Test
    fun `health is up after bootstrap completes`() {
        indexBootstrapState.markReady(initializerCount = 3)

        val health = indicator.health()

        assertEquals(Status.UP, health.status)
        assertEquals("READY", health.details["status"])
    }

    @Test
    fun `health is down after bootstrap failure`() {
        indexBootstrapState.markFailed(IllegalStateException("boom"))

        val health = indicator.health()

        assertEquals(Status.DOWN, health.status)
        assertEquals("FAILED", health.details["status"])
        assertEquals("boom", health.details["message"])
    }
}
