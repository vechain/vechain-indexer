package org.vechain.indexer.postgres

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
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
    private val NUL_MARK = Char(0xE001)
    private val ESC_MARK = Char(0xE002)

    private val mapper =
        jacksonObjectMapper()
            .registerModule(
                SimpleModule()
                    .addSerializer(BigInteger::class.java, ToStringSerializer.instance)
                    .addSerializer(BigDecimal::class.java, ToStringSerializer.instance)
            )
    private val mapType = object : TypeReference<Map<String, Any>>() {}

    fun read(json: String?): Map<String, Any>? = read(json, mapType)

    fun <T> write(value: T?): String? = value?.let {
        val text = mapper.writeValueAsString(it)
        if ("\\u0000" !in text && ESC !in text) text
        else mapper.writeValueAsString(mapStrings(mapper.valueToTree(it), ::escape))
    }

    fun <T> read(json: String?, type: TypeReference<T>): T? = json?.let { text ->
        if (ESC !in text) mapper.readValue(text, type)
        else mapper.treeToValue(mapStrings(mapper.readTree(text), ::unescape), type)
    }

    private fun mapStrings(node: JsonNode, f: (String) -> String): JsonNode =
        when (node) {
            is TextNode -> TextNode.valueOf(f(node.textValue()))
            is ObjectNode ->
                node.apply {
                    fieldNames().asSequence().toList().forEach {
                        replace(it, mapStrings(get(it), f))
                    }
                }
            is ArrayNode -> node.apply { for (i in 0 until size()) set(i, mapStrings(get(i), f)) }
            else -> node
        }

    // jsonb rejects U+0000, and chain data carries it: NUL is written as ESC NUL_MARK and a literal
    // ESC as ESC ESC_MARK; unescape reverses both and leaves any other ESC alone, so rows written
    // before this escaping existed still read back unchanged.
    private fun escape(s: String): String =
        if (NUL !in s && ESC !in s) s
        else
            buildString(s.length + 4) {
                for (c in s) {
                    when (c) {
                        NUL -> append(ESC).append(NUL_MARK)
                        ESC -> append(ESC).append(ESC_MARK)
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
                    val mark = if (c == ESC && i + 1 < s.length) s[i + 1] else null
                    if (mark == NUL_MARK || mark == ESC_MARK) {
                        append(if (mark == NUL_MARK) NUL else ESC)
                        i += 2
                    } else {
                        append(c)
                        i++
                    }
                }
            }
}
