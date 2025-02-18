package org.vechain.indexer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
open class WebClientConfig {

    @Bean
    open fun webClient(builder: WebClient.Builder): WebClient {
        return builder.build()
    }
}
