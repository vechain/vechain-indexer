package org.vechain.indexer.b3tr.challenges.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.match
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.challenges.B3trChallenge
import org.vechain.indexer.b3tr.challenges.B3trUserChallenge
import org.vechain.indexer.b3tr.challenges.ChallengeStatus
import org.vechain.indexer.utils.SliceBuilder

@Profile("b3tr", "b3tr-challenges")
@Repository
open class CustomB3trChallengeRepositoryImpl
@Autowired
constructor(private val mongoTemplate: MongoTemplate) : CustomB3trChallengeRepository {

    override fun findByWalletAndStatus(
        wallet: String,
        status: ChallengeStatus?,
        pageable: Pageable,
    ): Slice<B3trChallenge> {
        val operations = buildList {
            add(match(Criteria.where(B3trUserChallenge::wallet.name).`is`(wallet)))
            add(
                Aggregation.lookup(
                    IndexerNames.B3TR_CHALLENGES.COLLECTION,
                    B3trUserChallenge::challengeId.name,
                    B3trChallenge::challengeId.name,
                    "challenge",
                )
            )
            add(Aggregation.unwind("challenge"))
            status?.let {
                add(match(Criteria.where("challenge.${B3trChallenge::status.name}").`is`(it)))
            }
            add(Aggregation.sort(pageable.sort))
            add(Aggregation.skip(pageable.offset))
            add(Aggregation.limit(pageable.pageSize.toLong() + 1))
            add(Aggregation.replaceRoot("challenge"))
        }

        val aggregation = Aggregation.newAggregation(B3trUserChallenge::class.java, operations)
        val results = mongoTemplate.aggregate(aggregation, B3trChallenge::class.java).mappedResults

        return SliceBuilder.buildResultsSlice(results, pageable)
    }
}
