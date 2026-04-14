package org.vechain.indexer.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class QueryDispatcherConfig {
    @Bean("queryDispatcher")
    open fun queryDispatcher(
        @Value("\${query.dispatcher.parallelism:20}") parallelism: Int
    ): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(parallelism)
}
