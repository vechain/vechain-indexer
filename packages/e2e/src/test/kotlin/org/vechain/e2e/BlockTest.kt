package org.vechain.e2e

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class BlockTest {
    @BeforeEach
    fun `perform healthcheck`() {
        VeWorldAPIClient.performIndexerHealthCheck("BlocksIndexer")
    }

    @Test
    fun `get block by number`() {
        val block = VeWorldAPIClient.getBlock("1")

        expectThat(block.blockNumber).isEqualTo(1)
    }

    @Test
    fun `get best block`() {
        assertDoesNotThrow { VeWorldAPIClient.getBlock("best") }
    }

    @Test
    fun `get block by invalid id`() {
        assertThrows<Exception> { VeWorldAPIClient.getBlock("invalid") }
    }
}
