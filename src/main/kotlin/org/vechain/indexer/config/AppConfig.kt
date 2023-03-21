package org.vechain.indexer.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableCaching
class AppConfig {

    @Bean
    fun thorRest(): WebClient {
        return WebClient.create("https://mainnet.vechain.org")
    }
}