package org.vechain.indexer

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

import java.util.concurrent.ThreadPoolExecutor

const val ALLOWED_BLOCK_GAP = 10000
@Component
class IndexManager(val indexers: List<Indexer>) {

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        val executor = Executors.newFixedThreadPool(indexers.size) as ThreadPoolExecutor

        indexers.forEach { indexer -> executor.submit<Any> { indexer.start(); null } }
    }

    @Scheduled(fixedDelay = 30000)
    fun throttleIndexers() {
        val minIndexer = indexers.minBy { it.currentBlock }
        val maxIndexer = indexers.maxBy { it.currentBlock }

        indexers.forEach { indexer -> indexer.unthrottle() }
        if (maxIndexer.currentBlock - minIndexer.currentBlock > ALLOWED_BLOCK_GAP) {
            maxIndexer.throttle()
        }
    }

}
