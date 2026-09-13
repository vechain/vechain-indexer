package org.vechain.indexer.postgres

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PostgresJsonTest {
    private val nul = Char(0)
    private val esc = Char(0xE000)

    @Test
    fun `ordinary parameters are written as plain JSON`() {
        val params = mapOf("to" to "0xabc", "ids" to listOf("1", "2"), "flag" to true)
        assertEquals("""{"to":"0xabc","ids":["1","2"],"flag":true}""", PostgresJson.write(params))
        assertEquals(params, PostgresJson.read(PostgresJson.write(params)))
    }

    @Test
    fun `a NUL and the escape character both survive the round trip`() {
        val params =
            mapOf(
                "proof" to "a${nul}b${esc}c${esc}${nul}${nul}",
                "nested" to mapOf("list" to listOf("$nul", "$esc", "plain")),
                "flag" to true,
            )
        val text = PostgresJson.write(params)!!
        assertFalse(text.contains("u0000"), text)
        assertEquals(params, PostgresJson.read(text))
    }
}
