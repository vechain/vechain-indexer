package org.vechain.indexer.vevote

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "indexer.vevote")
open class TestProposalsProperties {
    var testProposals: Map<String, List<Int>> = emptyMap()
}
