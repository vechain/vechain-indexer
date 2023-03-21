package org.vechain.indexer

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

import java.util.concurrent.ThreadPoolExecutor

const val ALLOWED_BLOCK_GAP = 10000
@Component
class IndexManager(val blockIndexer: BlockIndexer, val transactionIndexer: TransactionIndexer, val clauseIndexer: ClauseIndexer) {

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        val executor = Executors.newFixedThreadPool(3) as ThreadPoolExecutor
        executor.submit<Any> {
            blockIndexer.start()
            null
        }

        executor.submit<Any> {
            transactionIndexer.start()
            null
        }

        executor.submit<Any> {
            clauseIndexer.start()
            null
        }
    }

    @Scheduled(fixedDelay = 60000)
    fun throttleIndexers() {
        val indexers = listOf(blockIndexer, transactionIndexer, clauseIndexer)
        val minIndexer = indexers.minBy { it.currentBlock }
        val maxIndexer = indexers.maxBy { it.currentBlock }

        indexers.forEach { indexer -> indexer.unthrottle() }
        if (maxIndexer!!.currentBlock - minIndexer!!.currentBlock > ALLOWED_BLOCK_GAP) {
            maxIndexer.throttle()
        }
    }

}
