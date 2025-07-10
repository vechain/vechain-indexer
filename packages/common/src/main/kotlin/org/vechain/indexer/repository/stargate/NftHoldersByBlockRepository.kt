package org.vechain.indexer.repository.stargate

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.model.stargate.NftHoldersByBlock
import org.vechain.indexer.repository.BaseIndexedRepository

@Profile("stargate")
interface NftHoldersByBlockRepository :
    BaseIndexedRepository<NftHoldersByBlock, Long>, TimeSeriesRepo<NftHoldersByBlock> {

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): NftHoldersByBlock?

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockTimestamp': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockTimestamp': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): NftHoldersByBlock?
}
