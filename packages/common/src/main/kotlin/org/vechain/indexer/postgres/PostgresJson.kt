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
    private val ESC = Char(0xE000)

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
        else mapper.writeValueAsString(mapStrings(mapper.valueToTree(it), PostgresText::escape))
    }

    fun <T> read(json: String?, type: TypeReference<T>): T? = json?.let { text ->
        if (ESC !in text) mapper.readValue(text, type)
        else mapper.treeToValue(mapStrings(mapper.readTree(text), PostgresText::unescape), type)
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
}
