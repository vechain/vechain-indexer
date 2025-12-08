package org.vechain.indexer.performance.history

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.history.HistoryRepository
import org.vechain.indexer.history.HistoryService
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.nft.NftBlacklistClient
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.model.Block

/**
 * Extended HistoryService that profiles EVERY internal method call This gives you complete
 * visibility into where time is spent
 */
class ProfiledHistoryService(
    repository: HistoryRepository,
    mongoTemplate: MongoTemplate,
    blacklistClient: NftBlacklistClient,
    private val profiler: DetailedProfiler,
) : HistoryService(repository, mongoTemplate, blacklistClient) {

    override fun processEvents(
        events: List<IndexedEvent>,
        block: Block,
    ): List<IndexedHistoryEvent> {
        return profiler.time("      HistoryService.processEvents") {
            val historyEvents = mutableListOf<IndexedHistoryEvent>()
            val processedTxs = mutableSetOf<String>()

            events.forEach { event ->
                profiler.time("        - determineEventType") {
                    val eventName =
                        org.vechain.indexer.utils.EventUtils.determineEventType(event.params)

                    if (eventName != null) {
                        if (event.params.getEventType() == "TransferBatch") {
                            profiler.time("        - processBatchTransferEvents") {
                                historyEvents.addAll(processBatchTransferEventsInternal(event))
                            }
                        } else {
                            profiler.time("        - createIndexedHistoryEvent") {
                                historyEvents.add(
                                    createIndexedHistoryEventInternal(event, eventName)
                                )
                            }
                        }
                        processedTxs.add(event.txId)
                    }
                }
            }

            profiler.time("        - getMissingTransactions") {
                historyEvents.addAll(getMissingTransactionsInternal(block, processedTxs))
            }

            historyEvents
        }
    }

    override fun save(events: List<IndexedHistoryEvent>) {
        profiler.time("      HistoryService.save (MongoDB)") {
            profiler.time("        - repository.saveAll") { super.save(events) }
        }
    }

    // These call the actual implementation from parent class
    // We just wrap them with profiling
    private fun processBatchTransferEventsInternal(event: IndexedEvent): List<IndexedHistoryEvent> {
        // Call parent's private method via reflection
        val method =
            HistoryService::class
                .java
                .getDeclaredMethod("processBatchTransferEvents", IndexedEvent::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, event) as List<IndexedHistoryEvent>
    }

    private fun createIndexedHistoryEventInternal(
        event: IndexedEvent,
        eventName: org.vechain.indexer.history.HistoryEventName,
    ): IndexedHistoryEvent {
        val method =
            HistoryService::class
                .java
                .getDeclaredMethod(
                    "createIndexedHistoryEvent",
                    IndexedEvent::class.java,
                    org.vechain.indexer.history.HistoryEventName::class.java,
                )
        method.isAccessible = true
        return method.invoke(this, event, eventName) as IndexedHistoryEvent
    }

    private fun getMissingTransactionsInternal(
        block: Block,
        processedTxs: Set<String>,
    ): List<IndexedHistoryEvent> {
        val method =
            HistoryService::class
                .java
                .getDeclaredMethod("getMissingTransactions", Block::class.java, Set::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block, processedTxs) as List<IndexedHistoryEvent>
    }
}
