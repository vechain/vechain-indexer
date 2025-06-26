package org.vechain.indexer.repository.stargate

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.model.stargate.TotalVthoClaimedByBlock
import org.vechain.indexer.repository.BaseIndexedRepository

@Profile("stargate")
interface TotalVthoClaimedByBlockRepository : BaseIndexedRepository<TotalVthoClaimedByBlock, Long> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun findLatestBeforeOrAtBlock(blockNumber: Long): TotalVthoClaimedByBlock?
}
