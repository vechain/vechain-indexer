package org.vechain.indexer.postgres

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Decoded event `params` as JSONB. Big numbers are written as strings, which is what Spring Data's
 * Mongo converters did, so the API keeps emitting the same JSON for a uint256 parameter.
 */
object PostgresJson {
    private val NUL = Char(0)
    private val ESC = Char(0xE000)

    private val mapper =
        jacksonObjectMapper()
            .registerModule(
                SimpleModule()
                    .addSerializer(BigInteger::class.java, ToStringSerializer.instance)
                    .addSerializer(BigDecimal::class.java, ToStringSerializer.instance)
            )
    private val mapType = object : TypeReference<Map<String, Any>>() {}

    fun write(params: Map<String, Any>?): String? = params?.let {
        mapper.writeValueAsString(if (needsEscaping(it)) mapStrings(it, ::escape) else it)
    }

    fun read(json: String?): Map<String, Any>? = json?.let { text ->
        val parsed = mapper.readValue(text, mapType)
        if (ESC in text) mapStrings(parsed, ::unescape) else parsed
    }

    private fun needsEscaping(value: Any?): Boolean =
        when (value) {
            is String -> NUL in value || ESC in value
            is Map<*, *> -> value.values.any(::needsEscaping)
            is List<*> -> value.any(::needsEscaping)
            else -> false
        }

    private fun mapStrings(params: Map<String, Any>, f: (String) -> String): Map<String, Any> =
        params.mapValues { (_, value) ->
            mapValue(value, f)
        }

    private fun mapValue(value: Any, f: (String) -> String): Any =
        when (value) {
            is String -> f(value)
            is Map<*, *> -> value.mapValues { (_, v) -> v?.let { mapValue(it, f) } }
            is List<*> -> value.map { it?.let { element -> mapValue(element, f) } }
            else -> value
        }

    // jsonb rejects U+0000, and chain data carries it: NUL is written as ESC '0' and a literal
    // ESC as ESC ESC, so unescape can reverse both and the API returns exactly what Mongo did.
    private fun escape(s: String): String =
        if (NUL !in s && ESC !in s) s
        else
            buildString(s.length + 4) {
                for (c in s) {
                    when (c) {
                        NUL -> append(ESC).append('0')
                        ESC -> append(ESC).append(ESC)
                        else -> append(c)
                    }
                }
            }

    private fun unescape(s: String): String =
        if (ESC !in s) s
        else
            buildString(s.length) {
                var i = 0
                while (i < s.length) {
                    val c = s[i]
                    if (c == ESC && i + 1 < s.length) {
                        append(if (s[i + 1] == '0') NUL else s[i + 1])
                        i += 2
                    } else {
                        append(c)
                        i++
                    }
                }
            }
}
