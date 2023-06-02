package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.vechain.indexer.service.ThorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

@Component
class IndexManager(private val indexers: List<Indexer>, private val thorService: ThorService) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {

        logger.info("Starting indexers for ${indexers.size} chains")

        val executor = Executors.newFixedThreadPool(indexers.size) as ThreadPoolExecutor

        indexers.forEach { indexer -> executor.submit<Any> { indexer.start(); null } }
    }


}
