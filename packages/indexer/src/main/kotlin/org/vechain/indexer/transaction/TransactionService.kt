package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.DecodedEvent
import org.vechain.indexer.thor.DecodedOutputs
import org.vechain.indexer.thor.model.Block

@Profile("transactions")
@Service
class TransactionService(private val mongoTemplate: MongoTemplate) {
    fun processBlockTransactions(events: List<IndexedEvent>, block: Block) {
        // Group events by transaction ID
        val eventsByTx = events.groupBy { it.txId }

        val txs =
            block.transactions.map { tx ->
                val eventsByClause: Map<Int, List<IndexedEvent>> =
                    eventsByTx[tx.id]?.groupBy { it.clauseIndex.toInt() } ?: emptyMap()

                val decodedOutputs =
                    tx.outputs.mapIndexed { index, output ->
                        val clauseEvents = eventsByClause[index] ?: emptyList()
                        val eventMap = mutableMapOf<String, DecodedEvent>()
                        // Get all decoded events from the clause
                        clauseEvents.forEach { event ->
                            val key = "${event.address}-${event.raw?.topics}-${event.raw?.data}"
                            eventMap[key] =
                                DecodedEvent(
                                    address = event.address as String,
                                    topics = event.raw!!.topics,
                                    data = event.raw!!.data,
                                    name = event.params.getEventType(),
                                    params = event.params.getReturnValues(),
                                )
                        }
                        // Get all non-decoded events from the clause
                        output.events.forEach { rawEvent ->
                            val key = "${rawEvent.address}-${rawEvent.topics}-${rawEvent.data}"
                            eventMap.putIfAbsent(
                                key,
                                DecodedEvent(
                                    address = rawEvent.address,
                                    topics = rawEvent.topics,
                                    data = rawEvent.data,
                                ),
                            )
                        }
                        // Create the DecodedOutputs object
                        DecodedOutputs(
                            contractAddress = tx.origin,
                            events = eventMap.values.toList(),
                            transfers = tx.outputs.getOrNull(index)?.transfers ?: emptyList(),
                        )
                    }

                IndexedTransaction(block = block, tx = tx, decodedOutputs = decodedOutputs)
            }

        mongoTemplate.insert(txs, IndexedTransaction::class.java)
    }
}
