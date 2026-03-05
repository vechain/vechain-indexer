package org.vechain.indexer.config.metrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.Status

class StatusExtensionsTest {

    // --- toReadableEnumLabel tests ---

    @Test
    fun `toReadableEnumLabel converts single word to title case`() {
        assertThat("SYNCING".toReadableEnumLabel()).isEqualTo("Syncing")
    }

    @Test
    fun `toReadableEnumLabel converts multi-word enum to title case with spaces`() {
        assertThat("FAST_SYNCING".toReadableEnumLabel()).isEqualTo("Fast Syncing")
        assertThat("FULLY_SYNCED".toReadableEnumLabel()).isEqualTo("Fully Synced")
        assertThat("NOT_INITIALISED".toReadableEnumLabel()).isEqualTo("Not Initialised")
        assertThat("SHUT_DOWN".toReadableEnumLabel()).isEqualTo("Shut Down")
    }

    @Test
    fun `toReadableEnumLabel handles already lowercase input`() {
        assertThat("syncing".toReadableEnumLabel()).isEqualTo("Syncing")
    }

    @Test
    fun `toReadableEnumLabel handles empty string`() {
        assertThat("".toReadableEnumLabel()).isEqualTo("")
    }

    // --- toStatusCode tests ---

    @Test
    fun `toStatusCode returns expected values for all statuses`() {
        assertThat(Status.NOT_INITIALISED.toStatusCode()).isEqualTo(0.0)
        assertThat(Status.INITIALISED.toStatusCode()).isEqualTo(1.0)
        assertThat(Status.SYNCING.toStatusCode()).isEqualTo(2.0)
        assertThat(Status.FAST_SYNCING.toStatusCode()).isEqualTo(3.0)
        assertThat(Status.SHUT_DOWN.toStatusCode()).isEqualTo(5.0)
        assertThat(Status.FULLY_SYNCED.toStatusCode()).isEqualTo(6.0)
    }

    @Test
    fun `toStatusCode covers all Status entries`() {
        Status.entries.forEach { status -> assertThat(status.toStatusCode()).isNotNaN() }
    }
}
