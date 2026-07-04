package org.vechain.indexer.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "metrics.project-ids")
data class ProjectIdsProperties(var whitelist: List<String> = emptyList())
