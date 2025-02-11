package org.vechain.indexer.service

import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.DecodedEvent
import org.vechain.indexer.model.DecodedOutputs
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Transaction

@Service
class TransactionService {
    fun processBlockTransactions(
        transactions: List<Transaction>,
        eventsByTx: Map<String, List<Pair<IndexedEvent, GenericEventParameters>>>,
        block: Block,
    ): List<IndexedTransaction> =
        transactions.map { tx ->
            val eventsByClause: Map<Int, List<Pair<IndexedEvent, GenericEventParameters>>> =
                eventsByTx[tx.id]?.groupBy { it.first.clauseIndex.toInt() } ?: emptyMap()

            val decodedOutputs =
                tx.outputs.mapIndexed { index, output ->
                    val clauseEvents = eventsByClause[index] ?: emptyList()
                    val eventMap = mutableMapOf<String, DecodedEvent>()
                    // Get all decoded events from the clause
                    clauseEvents.forEach { event ->
                        val key =
                            "${event.first.address}-${event.first.raw?.topics}-${event.first.raw?.data}"
                        eventMap[key] =
                            DecodedEvent(
                                address = event.first.address as String,
                                topics = event.first.raw!!.topics,
                                data = event.first.raw!!.data,
                                name = event.second.getEventType(),
                                params = event.second.getReturnValues(),
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
}
