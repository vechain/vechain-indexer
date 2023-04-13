package org.vechain.indexer

import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {

        logger.info("Starting indexers for ${indexers.size} chains")

        val executor = Executors.newFixedThreadPool(indexers.size) as ThreadPoolExecutor

        indexers.forEach { indexer -> executor.submit<Any> { indexer.start(); null } }
    }

    @Scheduled(fixedRate = BLOCK_TIME * 10)
    fun reSyncingIndexers() {
        try {
            val latestBlockNumber = thorService.getBestBlockNumber()

            indexers.forEach { indexer ->
                if (indexer.currentBlockNumber < latestBlockNumber && indexer.status == Status.FULLY_SYNCED) {
                    logger.info("${indexer.name} - Changing status to SYNCING (indexerBlock=${indexer.currentBlockNumber}, bestBlock=${latestBlockNumber})")
                    indexer.status = Status.SYNCING
                }
            }
        } catch (e: Exception) {
            logger.warn("There was an error while checking sync status of indexers", e)
        }
    }
}
