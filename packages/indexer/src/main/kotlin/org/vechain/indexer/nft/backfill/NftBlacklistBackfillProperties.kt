package org.vechain.indexer.nft.backfill

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "indexer.blacklist.backfill")
class NftBlacklistBackfillProperties {
    var enabled: Boolean = true

    /** Rows flipped per tick; with the tick interval this bounds the write rate. */
    var batchSize: Int = 2_000

    var maxAttempts: Int = 10

    @PostConstruct
    fun validate() {
        require(batchSize >= 1) { "indexer.blacklist.backfill.batch-size must be >= 1" }
        require(maxAttempts >= 1) { "indexer.blacklist.backfill.max-attempts must be >= 1" }
    }
}
