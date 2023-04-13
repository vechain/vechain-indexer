package org.vechain.indexer.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.vechain.devkit.Function
import org.vechain.indexer.abi.FunctionDefinition
import org.vechain.indexer.model.Clause

object ClauseUtils {

    private val objectMapper = ObjectMapper()

    init {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
    }

    fun contractCall(address: String, function: FunctionDefinition, vararg args: Any): Clause {
        val func = objectMapper.convertValue(function, Function::class.java)
        val encoded = func.encodeToHex(true, *args)
        return Clause(to = address, data = encoded, value = "0x0")
    }
}