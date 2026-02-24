package org.vechain.indexer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "indexer.inline-versioning")
class InlineVersioningProperties {
    /** Keep previous versions within this many blocks of the current block. */
    var blockWindow: Long = 10_000

    /** Hard cap on the _previousVersions array size (safety net). */
    var maxVersions: Int = 100
}
