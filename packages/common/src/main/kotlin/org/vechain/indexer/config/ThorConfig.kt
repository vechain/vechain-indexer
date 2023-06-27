package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Profile("blocks-proxy")
@Configuration
open class ThorConfig(@Value("\${thor.url}") private val thorUrl: String) {

    @Bean
    open fun thorRest(): WebClient {
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
