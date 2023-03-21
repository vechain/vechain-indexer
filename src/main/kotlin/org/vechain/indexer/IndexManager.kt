package org.vechain.indexer

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

import java.util.concurrent.ThreadPoolExecutor

@Component
class IndexManager(val blockIndexer: BlockIndexer, val transactionIndexer: TransactionIndexer, val clauseIndexer: ClauseIndexer) {
    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        val executor = Executors.newFixedThreadPool(3) as ThreadPoolExecutor
        executor.submit<Any> {
            blockIndexer.run()
            null
        }

        executor.submit<Any> {
            transactionIndexer.run()
            null
        }

        executor.submit<Any> {
            clauseIndexer.run()
            null
        }
    }

}