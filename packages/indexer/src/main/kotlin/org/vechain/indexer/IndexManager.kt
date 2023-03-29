package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.service.ThorService
import java.util.concurrent.Executors

import java.util.concurrent.ThreadPoolExecutor

@Component
class IndexManager(private val indexers: List<Indexer>, private val thorService: ThorService) {

    private val logger = LogManager.getLogger(this::class.simpleName)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {

        logger.warn("Starting indexers for ${indexers.size} chains")

        val executor = Executors.newFixedThreadPool(indexers.size) as ThreadPoolExecutor

        indexers.forEach { indexer -> executor.submit<Any> { indexer.start(); null } }
    }

    @Scheduled(fixedRate = APPROX_BLOCK_PERIOD * 2)
    fun reSyncingIndexers() {
        val latestBlockNumber = thorService.getBestBlockNumber()

        indexers.forEach {
            if (it.getCurrentBlock() < latestBlockNumber && it.getStatus() == Status.FULLY_SYNCED) {
                it.updateStatus(Status.SYNCING)
            }
        }
    }

}
