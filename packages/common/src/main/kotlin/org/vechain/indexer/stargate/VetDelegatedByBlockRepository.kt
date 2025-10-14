package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate", "vet-delegated-by-block")
interface VetDelegatedByBlockRepository :
    BaseIndexedRepository<VetDelegatedByBlock, Long>, TimeSeriesRepo<VetDelegatedByBlock> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VetDelegatedByBlock?

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockTimestamp': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockTimestamp': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): VetDelegatedByBlock?
}
