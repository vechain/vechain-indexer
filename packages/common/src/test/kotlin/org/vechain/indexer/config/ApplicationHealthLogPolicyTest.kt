package org.vechain.indexer.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status

class ApplicationHealthLogPolicyTest {

    @Test
    fun `out of service is treated as not ready`() {
        val policy = healthStatusLogPolicy(Status.OUT_OF_SERVICE)

        assertEquals(HealthLogLevel.WARN, policy.level)
        assertEquals("NOT_READY", policy.label)
    }

    @Test
    fun `down is treated as unhealthy`() {
        val policy = healthStatusLogPolicy(Status.DOWN)

        assertEquals(HealthLogLevel.ERROR, policy.level)
        assertEquals("UNHEALTHY", policy.label)
    }

    @Test
    fun `unknown is warned`() {
        val policy = healthStatusLogPolicy(Status.UNKNOWN)

        assertEquals(HealthLogLevel.WARN, policy.level)
        assertEquals("UNKNOWN", policy.label)
    }
}
