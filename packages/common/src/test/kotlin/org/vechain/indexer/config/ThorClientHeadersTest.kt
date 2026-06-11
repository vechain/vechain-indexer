package org.vechain.indexer.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThorClientHeadersTest {

    @Test
    fun `omits bypass header when key is blank`() {
        val headers = thorClientHeaders("").toList()

        assertEquals(listOf<Pair<String, Any>>("X-Project-Id" to "veworld-indexer"), headers)
    }

    @Test
    fun `omits bypass header when key is whitespace only`() {
        val headers = thorClientHeaders("   ").toList()

        assertEquals(listOf<Pair<String, Any>>("X-Project-Id" to "veworld-indexer"), headers)
    }

    @Test
    fun `appends bypass header when key is non-blank`() {
        val headers = thorClientHeaders("secret-key").toList()

        assertEquals(
            listOf<Pair<String, Any>>(
                "X-Project-Id" to "veworld-indexer",
                "x-rate-limit-bypass" to "secret-key",
            ),
            headers,
        )
    }
}
