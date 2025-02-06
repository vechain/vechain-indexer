package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.client.ThorClient

@Configuration
open class IndexerConfig(
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${abis.path}") private val abisPath: String,
    @Value("\${businessEvents.path}") private val businessEventsPath: String
) {

    @Bean
    open fun thorClient(): ThorClient {
        return DefaultThorClient(thorUrl, Pair("X-Project-Id", "veworld-indexer"))
    }

    @Bean
    open fun abiManager(): AbiManager {
        val abiManager = AbiManager()
        abiManager.loadAbis(abisPath)
        return abiManager
    }

    @Bean
    open fun businessEventManager(): BusinessEventManager {
        val businessEventManager = BusinessEventManager()
        businessEventManager.loadBusinessEvents(businessEventsPath)
        return businessEventManager
    }
}
