package org.vechain.indexer.config

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "indexer.inline-versioning")
class InlineVersioningProperties {
    /** Keep previous versions within this many blocks of the current block. */
    var blockWindow: Long = 10_000

    /** Hard cap on the _previousVersions array size (safety net). */
    var maxVersions: Int = 100

    @PostConstruct
    fun validate() {
        require(blockWindow >= 0) {
            "indexer.inline-versioning.block-window must be >= 0, was $blockWindow"
        }
        require(maxVersions >= 1) {
            "indexer.inline-versioning.max-versions must be >= 1, was $maxVersions"
        }
    }
}
