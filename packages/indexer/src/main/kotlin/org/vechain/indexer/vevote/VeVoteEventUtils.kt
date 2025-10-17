package org.vechain.indexer.vevote

import java.util.Locale
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

object VeVoteEventUtils {
    fun getProposalId(event: IndexedEvent): String =
        event.params.getAsString("proposalId")
            ?: error("Missing param 'proposalId' in event: ${event.id}")

    fun getSupport(event: IndexedEvent): Support =
        Support.map(
            event.params.getAsBigInteger("support")
                ?: error("Missing param 'support' in event: ${event.id}")
        )

    fun getWeight(event: IndexedEvent) =
        event.params.getAsBigDecimal("weight")
            ?: error("Missing param 'weight' in event: ${event.id}")

    fun groupByProposalId(events: List<IndexedEvent>): Map<String, List<IndexedEvent>> =
        events
            .map {
                it.params.getAsString("proposalId")?.let { proposalId ->
                    proposalId.lowercase(Locale.getDefault()) to it
                } ?: error("Missing proposalId in event: ${it.id}")
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, proposalEvents) -> proposalEvents.sortedBy { it.blockNumber } }

    fun groupBySupport(events: List<IndexedEvent>): Map<Support, List<IndexedEvent>> =
        events
            .map { getSupport(it) to it }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, supportEvents) -> supportEvents.sortedBy { it.blockNumber } }
}
