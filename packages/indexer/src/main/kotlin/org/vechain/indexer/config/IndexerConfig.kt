package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.FileUtils

@Configuration
open class IndexerConfig(
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${abis.path}") private val abisPath: String,
    @Value("\${businessEvents.path}") private val businessEventsPath: String,
) {
    @Bean
    open fun thorClient(): ThorClient =
        DefaultThorClient(thorUrl, Pair("X-Project-Id", "veworld-indexer"))

    @Bean
    open fun abiManager(): AbiManager {
        val abiManager = AbiManager()
        val abis = FileUtils.loadFileStreams(abisPath)
        abiManager.loadAbis(abis)
        return abiManager
    }

    @Bean
    open fun businessEventManager(): BusinessEventManager {
        val businessEventManager = BusinessEventManager()
        val businessEvents = FileUtils.loadFileStreams(businessEventsPath)
        businessEventManager.loadBusinessEvents(businessEvents)
        return businessEventManager
    }
}
