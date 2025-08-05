package org.vechain.indexer.b3tr.gm

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory

@Configuration
@Profile("b3tr", "galaxy-member")
open class GmConfig {
    @Bean open fun gmIndexer(): Indexer = IndexerFactory().build()
}
