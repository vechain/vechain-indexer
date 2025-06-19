package org.vechain.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.FileUtils.getJsonFilePaths

@Configuration
open class IndexerConfig(
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${abis.path}") private val abisPath: String,
    private val bEProperties: BusinessEventProperties,
) {
    @Bean
    open fun thorClient(): ThorClient =
        DefaultThorClient(thorUrl, Pair("X-Project-Id", "veworld-indexer"))

    @Bean open fun abiManager() = AbiManager(getJsonFilePaths(abisPath))

    @Bean
    open fun businessEventManager() =
        BusinessEventManager(getJsonFilePaths(bEProperties.path), bEProperties.substitutions)
}
