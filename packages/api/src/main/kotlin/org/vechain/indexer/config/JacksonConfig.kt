package org.vechain.indexer.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class JacksonConfig {

    @Bean
    open fun objectMapper(): ObjectMapper {
        val module =
            SimpleModule().apply {
                addSerializer(BigInteger::class.java, ToStringSerializer.instance)
            }

        return ObjectMapper().apply { registerModule(module) }
    }
}
