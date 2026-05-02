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

    /**
     * Floor on retained _previousVersions entries. Must be >= the deepest chain reorg the indexer
     * is expected to unwind, so rollbacks never hit an empty history when the block-window trim
     * would otherwise drop the array below that depth.
     */
    var minVersions: Int = 20

    @PostConstruct
    fun validate() {
        require(blockWindow >= 0) {
            "indexer.inline-versioning.block-window must be >= 0, was $blockWindow"
        }
        require(maxVersions >= 1) {
            "indexer.inline-versioning.max-versions must be >= 1, was $maxVersions"
        }
        require(minVersions >= 1) {
            "indexer.inline-versioning.min-versions must be >= 1, was $minVersions"
        }
        require(minVersions <= maxVersions) {
            "indexer.inline-versioning.min-versions ($minVersions) must be <= max-versions ($maxVersions)"
        }
    }
}
