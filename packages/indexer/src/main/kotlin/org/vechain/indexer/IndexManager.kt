package org.vechain.indexer

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class IndexManager(private val indexers: List<Indexer>) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        logger.info("Starting ${indexers.size} indexers")

        val scope = CoroutineScope(Dispatchers.Default)

        indexers.forEach { indexer ->
            scope.launch {
                indexer.start()
            }
        }
    }
}
