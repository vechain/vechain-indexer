package org.vechain.indexer.b3tr.xAlloc

import java.math.BigInteger
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsInt

object XAllocEventUtils {

    data class AggregatedVote(val weight: BigInteger, val voters: Long)

    fun groupByRoundId(events: List<IndexedEvent>): Map<Int, List<IndexedEvent>> =
        events
            .map {
                it.params.getAsInt("roundId")?.let { roundId -> roundId to it }
                    ?: error("Missing roundId in event: ${it.id}")
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, roundEvents) -> roundEvents.sortedBy { it.blockNumber } }

    fun getAppIds(event: IndexedEvent): List<String> {
        val raw = event.params.params["appsIds"]
        val list =
            (raw as? List<*>)?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() }
        return list ?: error("Missing or invalid appsIds in event: ${event.id}")
    }

    fun getWeights(event: IndexedEvent): List<BigInteger> {
        val raw = event.params.params["voteWeights"]
        val list =
            (raw as? List<*>)?.mapNotNull { elem ->
                when (elem) {
                    is BigInteger -> elem
                    is Number -> BigInteger.valueOf(elem.toLong())
                    is String ->
                        try {
                            BigInteger(elem.trim())
                        } catch (_: Exception) {
                            null
                        }
                    else -> null
                }
            }
        return list ?: error("Missing or invalid weights in event: ${event.id}")
    }

    fun parseVotes(events: List<IndexedEvent>): Map<String, AggregatedVote> {
        val aggregated = mutableMapOf<String, AggregatedVote>()

        events.forEach { event ->
            val votes = parseVotes(event) // appId -> weight for this one voter
            votes.forEach { (appId, weight) ->
                val current = aggregated.getOrDefault(appId, AggregatedVote(BigInteger.ZERO, 0))
                aggregated[appId] =
                    AggregatedVote(weight = current.weight + weight, voters = current.voters + 1)
            }
        }
        return aggregated
    }

    /**
     * Parses the votes from an IndexedEvent and returns a map of appId to weight. Validates that
     * the number of appIds matches the number of weights and that there are no duplicate appIds.
     *
     * @param event The IndexedEvent containing the vote data.
     * @return A map where keys are appIds and values are their corresponding weights.
     */
    fun parseVotes(event: IndexedEvent): Map<String, BigInteger> {
        // Extract appIds and weights from event parameters
        val appIds = getAppIds(event)
        val weights = getWeights(event)

        // Ensure both lists are of the same size
        if (appIds.size != weights.size) {
            error("Mismatched appIds and weights sizes in event: ${event.id}")
        }

        // Ensure no duplicate appIds
        if (appIds.size != appIds.toSet().size) {
            error("Duplicate appIds found in event: ${event.id}")
        }

        // Combine into a map of appId to weight
        return appIds.zip(weights).toMap()
    }
}
