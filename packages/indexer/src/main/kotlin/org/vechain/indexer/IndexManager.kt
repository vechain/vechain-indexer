package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.service.ThorService
import java.util.concurrent.Executors

import java.util.concurrent.ThreadPoolExecutor

const val BLOCK_TIME = 10000L

@Component
class IndexManager(private val indexers: List<Indexer>, private val thorService: ThorService) {

    private val logger = LogManager.getLogger(this::class.simpleName)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {

        logger.warn("Starting indexers for ${indexers.size} chains")

        val executor = Executors.newFixedThreadPool(indexers.size) as ThreadPoolExecutor

        indexers.forEach { indexer -> executor.submit<Any> { indexer.start(); null } }
    }

    @Scheduled(fixedRate = BLOCK_TIME * 2)
    fun reSyncingIndexers() {
        val latestBlock = thorService.getBestBlock()

        latestBlock.number?.let { latestBlockNumber ->
            indexers.forEach { indexer ->
                if (indexer.currentBlock < latestBlockNumber && indexer.status == Status.FULLY_SYNCED) {
                    logger.info("${indexer.name()} - Switching from FULLY_SYNCED to SYNCING (current=${indexer.currentBlock}, latest=${latestBlock.number})")
                    indexer.status = Status.SYNCING
                }
            }
        }
    }

}
