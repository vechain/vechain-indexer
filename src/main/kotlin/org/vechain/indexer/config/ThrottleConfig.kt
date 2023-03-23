package org.vechain.indexer.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.vechain.indexer.ALLOWED_BLOCK_GAP
import org.vechain.indexer.Indexer

@Profile("throttling-enabled")
@EnableScheduling
@Configuration
class ThrottleConfig {
    @Scheduled(fixedDelay = 5000)
    fun throttleIndexers(indexers: List<Indexer>) {
        val minIndexer = indexers.minBy { it.currentBlock }

        indexers.forEach { indexer ->
            if (indexer.currentBlock - minIndexer!!.currentBlock > ALLOWED_BLOCK_GAP) {
                indexer.throttle()
            } else {
                indexer.unthrottle()
            }
        }
    }
}