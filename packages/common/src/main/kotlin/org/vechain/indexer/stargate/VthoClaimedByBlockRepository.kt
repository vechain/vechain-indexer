package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate")
interface VthoClaimedByBlockRepository :
    BaseIndexedRepository<VthoClaimedByBlock, Long>, TimeSeriesRepo<VthoClaimedByBlock> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VthoClaimedByBlock?

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockTimestamp': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockTimestamp': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): VthoClaimedByBlock?
}
