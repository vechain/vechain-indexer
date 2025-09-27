package org.vechain.indexer.transaction

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult

@Profile("transactions")
@Component
open class TransactionProcessor(
    private val transactionService: TransactionService,
    repository: TransactionRepository,
) : BaseProcessor(repository) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    
    override fun process(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block must be a normal block.")
        }
        val unknownEvents = entry.events().filter { it.address == null }
        if (unknownEvents.isNotEmpty()) {
            logger.warn(
                "⛔️Unknown events found: ${unknownEvents.joinToString(", ") { it.eventType }}"
            )
        }

        if (entry.block.transactions.isNotEmpty()) {
            transactionService.processBlockTransactions(events = entry.events, block = entry.block)
        }
    }
}
