package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient


@Configuration
class AppConfig (@Value("\${thor.url}") private val thorUrl: String,) {

    @Bean
    fun thorRest(): WebClient {
        val size = 16 * 1024 * 1024
        val strategies = ExchangeStrategies.builder()
            .codecs { codecs: ClientCodecConfigurer ->
                codecs.defaultCodecs().maxInMemorySize(size)
            }
            .build()
        return WebClient.builder()
            .exchangeStrategies(strategies)
            .baseUrl(thorUrl)
            .build()
    }
}
