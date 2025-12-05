package org.vechain.indexer.performance.transaction

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.DecodedEvent
import org.vechain.indexer.thor.DecodedOutputs
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.transaction.IndexedTransaction

/**
 * Wrapper for TransactionService that profiles EVERY internal method call Tracks performance of:
 * - processBlockTransactions (main processing)
 * - grouping events by transaction
 * - grouping events by clause
 * - decoding outputs
 * - MongoDB insert
 */
class ProfiledTransactionService(
    private val mongoTemplate: MongoTemplate,
    private val profiler: DetailedProfiler,
) {

    fun processBlockTransactions(events: List<IndexedEvent>, block: Block) {
        profiler.time("      TransactionService.processBlockTransactions") {
            // Group events by transaction ID
            val eventsByTx = profiler.time("        - groupBy txId") { events.groupBy { it.txId } }

            val txs =
                profiler.time("        - map transactions") {
                    block.transactions.map { tx ->
                        val eventsByClause: Map<Int, List<IndexedEvent>> =
                            profiler.time("          - groupBy clauseIndex") {
                                eventsByTx[tx.id]?.groupBy { it.clauseIndex.toInt() } ?: emptyMap()
                            }

                        val decodedOutputs =
                            profiler.time("          - mapIndexed outputs") {
                                tx.outputs.mapIndexed { index, output ->
                                    val clauseEvents = eventsByClause[index] ?: emptyList()
                                    val eventMap = mutableMapOf<String, DecodedEvent>()

                                    // Get all decoded events from the clause
                                    profiler.time("            - process decoded events") {
                                        clauseEvents.forEach { event ->
                                            val key =
                                                "${event.address}-${event.raw?.topics}-${event.raw?.data}"
                                            eventMap[key] =
                                                DecodedEvent(
                                                    address = event.address as String,
                                                    topics = event.raw!!.topics,
                                                    data = event.raw!!.data,
                                                    name = event.params.getEventType(),
                                                    params = event.params.getReturnValues(),
                                                )
                                        }
                                    }

                                    // Get all non-decoded events from the clause
                                    profiler.time("            - process raw events") {
                                        output.events.forEach { rawEvent ->
                                            val key =
                                                "${rawEvent.address}-${rawEvent.topics}-${rawEvent.data}"
                                            eventMap.putIfAbsent(
                                                key,
                                                DecodedEvent(
                                                    address = rawEvent.address,
                                                    topics = rawEvent.topics,
                                                    data = rawEvent.data,
                                                ),
                                            )
                                        }
                                    }

                                    // Create the DecodedOutputs object
                                    DecodedOutputs(
                                        contractAddress = tx.origin,
                                        events = eventMap.values.toList(),
                                        transfers =
                                            tx.outputs.getOrNull(index)?.transfers ?: emptyList(),
                                    )
                                }
                            }

                        IndexedTransaction(block = block, tx = tx, decodedOutputs = decodedOutputs)
                    }
                }

            profiler.time("        - MongoDB insert") {
                mongoTemplate.insert(txs, IndexedTransaction::class.java)
            }
        }
    }
}
