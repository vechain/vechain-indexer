package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class HealthCheckTests {

    @Test
    fun `infrastructure and apps should start`() {
        assertDoesNotThrow { VeWorldAPIClient.performHealthCheck() }
    }
}
