package org.vechain.indexer.config

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "indexer.checkpoint")
class CheckpointProperties {
    /** Minimum interval in seconds between checkpoint saves per collection. */
    var saveIntervalSeconds: Long = 60

    @PostConstruct
    fun validate() {
        require(saveIntervalSeconds >= 0) {
            "indexer.checkpoint.save-interval-seconds must be >= 0, was $saveIntervalSeconds"
        }
    }
}
