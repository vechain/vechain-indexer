package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

import java.util.concurrent.ThreadPoolExecutor

const val ALLOWED_BLOCK_GAP = 10000

@Component
class IndexManager(private val indexers: List<Indexer>) {

    private val logger = LogManager.getLogger(this::class.simpleName)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {

        logger.warn("Starting indexers for ${indexers.size} chains")

        val executor = Executors.newFixedThreadPool(indexers.size) as ThreadPoolExecutor

        indexers.forEach { indexer -> executor.submit<Any> { indexer.start(); null } }
    }

}
