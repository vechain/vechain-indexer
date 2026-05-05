package org.vechain.indexer.b3tr.relayer

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.exception.BadRequestException

/**
 * Resolves "who is auto-voting at round R?" by aggregating the AutoVotingToggle history.
 *
 * Each row already encodes the effective round in `activeFromRound` (= source round + 1, set by the
 * indexer). So the answer for round R is: per address, take the row with the highest
 * `activeFromRound <= R` and keep only those where `enabled = true`.
 *
 * Returns the full set in one call — relayer-node consumers need the entire queue at once, not a
 * page at a time.
 */
@Profile("b3tr", "b3tr-auto-voting-users")
@Service
open class AutoVotingService(private val mongoTemplate: MongoTemplate) {

    open fun findEnabledAddressesAtRound(roundId: Int): List<String> {
        if (roundId < 1) {
            throw BadRequestException("roundId must be >= 1")
        }

        val pipeline =
            Aggregation.newAggregation(
                    Aggregation.match(
                        Criteria.where(AutoVotingToggle::activeFromRound.name).lte(roundId)
                    ),
                    Aggregation.sort(
                        Sort.by(Sort.Order.desc(AutoVotingToggle::activeFromRound.name))
                    ),
                    Aggregation.group(AutoVotingToggle::address.name)
                        .first(AutoVotingToggle::enabled.name)
                        .`as`("lastEnabled"),
                    Aggregation.match(Criteria.where("lastEnabled").`is`(true)),
                    Aggregation.sort(Sort.by(Sort.Order.asc("_id"))),
                )
                .withOptions(AggregationOptions.builder().allowDiskUse(true).build())

        return mongoTemplate
            .aggregate(pipeline, IndexerNames.AUTO_VOTING_TOGGLE.COLLECTION, Document::class.java)
            .mappedResults
            .map { it.getString("_id") }
    }
}
