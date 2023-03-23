package org.vechain.indexer.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("caching-enabled")
@EnableCaching
@Configuration
class CacheConfig {
}