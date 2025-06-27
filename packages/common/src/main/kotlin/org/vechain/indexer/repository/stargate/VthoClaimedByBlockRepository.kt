package org.vechain.indexer.repository.stargate

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.model.stargate.VthoClaimedByBlock
import org.vechain.indexer.repository.BaseIndexedRepository

@Profile("stargate")
interface VthoClaimedByBlockRepository : BaseIndexedRepository<VthoClaimedByBlock, Long> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun findLatestBeforeOrAtBlock(blockNumber: Long): VthoClaimedByBlock?
}
