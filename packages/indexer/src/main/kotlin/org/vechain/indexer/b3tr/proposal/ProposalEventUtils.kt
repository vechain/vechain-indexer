package org.vechain.indexer.b3tr.proposal

import java.math.BigInteger
import java.util.Locale
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString

object ProposalEventUtils {
    fun getProposalId(event: IndexedEvent): String =
        event.params.getAsString("proposalId")
            ?: error("Missing param 'proposalId' in event: ${event.id}")

    fun getVoter(event: IndexedEvent): String =
        event.params.getAsString("from") ?: error("Missing 'from' param in event: ${event.id}")

    fun getSupport(event: IndexedEvent): Support =
        Support.fromValue(
            event.params.getAsInt("support")
                ?: error("Missing param 'support' in event: ${event.id}")
        ) ?: error("Invalid 'support' value in event: ${event.id}")

    fun getWeight(event: IndexedEvent): BigInteger =
        event.params.getAsString("voteWeight")?.let { BigInteger(it) }
            ?: error("Missing param 'voteWeight' in event: ${event.id}")

    fun getPower(event: IndexedEvent): BigInteger =
        event.params.getAsString("votePower")?.let { BigInteger(it) }
            ?: error("Missing param 'votePower' in event: ${event.id}")

    fun getReason(event: IndexedEvent): String = event.params.getAsString("reason") ?: ""

    fun getStartRoundId(event: IndexedEvent): Int =
        event.params.getAsInt("startRoundId")
            ?: error("Missing param 'startRoundId' in event: ${event.id}")

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
