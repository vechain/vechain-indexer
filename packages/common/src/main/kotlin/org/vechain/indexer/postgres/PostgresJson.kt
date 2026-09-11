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
    private val mapper =
        jacksonObjectMapper()
            .registerModule(
                SimpleModule()
                    .addSerializer(BigInteger::class.java, ToStringSerializer.instance)
                    .addSerializer(BigDecimal::class.java, ToStringSerializer.instance)
            )
    private val mapType = object : TypeReference<Map<String, Any>>() {}

    fun write(params: Map<String, Any>?): String? = params?.let(mapper::writeValueAsString)

    fun read(json: String?): Map<String, Any>? = json?.let { mapper.readValue(it, mapType) }
}
