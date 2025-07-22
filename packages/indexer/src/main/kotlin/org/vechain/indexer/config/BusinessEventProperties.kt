package org.vechain.indexer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "business-event")
class BusinessEventProperties {
    lateinit var substitutions: Map<String, String>
}
