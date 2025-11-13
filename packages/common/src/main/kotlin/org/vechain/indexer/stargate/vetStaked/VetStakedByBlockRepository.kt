package org.vechain.indexer.stargate.vetStaked

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

@Profile("stargate", "vet-staked-by-block")
interface VetStakedByBlockRepository :
    BaseIndexedRepository<VetStakedByBlock, Long>, TimeFrameRepo<VetStakedByBlock> {
    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VetStakedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<VetStakedByBlock>

    override fun findByBlockTimestampAfter(blockTimestamp: Long): List<VetStakedByBlock>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VetStakedByBlock?
}
