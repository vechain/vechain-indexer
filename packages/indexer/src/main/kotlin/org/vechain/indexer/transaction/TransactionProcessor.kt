package org.vechain.indexer.transaction

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.repository.TransactionRepository
import org.vechain.indexer.thor.model.Block

@Profile("transactions")
@Component
open class TransactionProcessor(
    private val transactionService: TransactionService,
    repository: TransactionRepository,
) : BaseProcessor(repository) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun process(events: List<IndexedEvent>, block: Block?) {
        // The block should never be null here, throw an error if it is.
        if (block == null) {
            throw IllegalArgumentException("Block cannot be null in TransactionProcessor")
        }

        val unknownEvents = events.filter { it.address == null }
        if (unknownEvents.isNotEmpty()) {
            logger.warn(
                "⛔️Unknown events found: ${unknownEvents.joinToString(", ") { it.eventType }}"
            )
        }

        if (block.transactions.isNotEmpty()) {
            transactionService.processBlockTransactions(events = events, block = block)
        }
    }
}
