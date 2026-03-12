package org.vechain.indexer.config.metrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.Status

class StatusExtensionsTest {
    private val statusExtensionsClass =
        Class.forName("org.vechain.indexer.config.metrics.StatusExtensionsKt")
    private val toReadableEnumLabelMethod =
        statusExtensionsClass.getMethod("toReadableEnumLabel", String::class.java)
    private val toStatusCodeMethod =
        statusExtensionsClass.getMethod("toStatusCode", Status::class.java)

    // --- toReadableEnumLabel tests ---

    @Test
    fun `toReadableEnumLabel converts single word to title case`() {
        assertThat(toReadableEnumLabel("SYNCING")).isEqualTo("Syncing")
    }

    @Test
    fun `toReadableEnumLabel converts multi-word enum to title case with spaces`() {
        assertThat(toReadableEnumLabel("FAST_SYNCING")).isEqualTo("Fast Syncing")
        assertThat(toReadableEnumLabel("FULLY_SYNCED")).isEqualTo("Fully Synced")
        assertThat(toReadableEnumLabel("NOT_INITIALISED")).isEqualTo("Not Initialised")
        assertThat(toReadableEnumLabel("SHUT_DOWN")).isEqualTo("Shut Down")
    }

    @Test
    fun `toReadableEnumLabel handles already lowercase input`() {
        assertThat(toReadableEnumLabel("syncing")).isEqualTo("Syncing")
    }

    @Test
    fun `toReadableEnumLabel handles empty string`() {
        assertThat(toReadableEnumLabel("")).isEqualTo("")
    }

    // --- toStatusCode tests ---

    @Test
    fun `toStatusCode returns expected values for all statuses`() {
        assertThat(toStatusCode(Status.NOT_INITIALISED)).isEqualTo(0.0)
        assertThat(toStatusCode(Status.INITIALISED)).isEqualTo(1.0)
        assertThat(toStatusCode(Status.SYNCING)).isEqualTo(2.0)
        assertThat(toStatusCode(Status.FAST_SYNCING)).isEqualTo(3.0)
        assertThat(toStatusCode(Status.SHUT_DOWN)).isEqualTo(5.0)
        assertThat(toStatusCode(Status.FULLY_SYNCED)).isEqualTo(6.0)
    }

    @Test
    fun `toStatusCode covers all Status entries`() {
        Status.entries.forEach { status -> assertThat(toStatusCode(status)).isNotNaN() }
    }

    private fun toReadableEnumLabel(value: String): String =
        toReadableEnumLabelMethod.invoke(null, value) as String

    private fun toStatusCode(status: Status): Double =
        toStatusCodeMethod.invoke(null, status) as Double
}
