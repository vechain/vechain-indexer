package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
open class AppConfig(
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${token.registry.url}") private val tokenRegistryUrl: String
) {

    @Bean
    open fun thorRest(): WebClient {
        val size = 16 * 1024 * 1024
        val strategies =
            ExchangeStrategies.builder()
                .codecs { codecs: ClientCodecConfigurer ->
                    codecs.defaultCodecs().maxInMemorySize(size)
                }
                .build()
        return WebClient.builder().exchangeStrategies(strategies).baseUrl(thorUrl).build()
    }

    @Bean
    open fun officialTokenRepoRest(): WebClient {
        val size = 16 * 1024 * 1024
        val strategies =
            ExchangeStrategies.builder()
                .codecs { codecs: ClientCodecConfigurer ->
                    codecs.defaultCodecs().maxInMemorySize(size)
                }
                .build()
        return WebClient.builder().exchangeStrategies(strategies).baseUrl(tokenRegistryUrl).build()
    }
}
