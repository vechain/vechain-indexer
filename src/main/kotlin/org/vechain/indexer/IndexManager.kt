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

}
