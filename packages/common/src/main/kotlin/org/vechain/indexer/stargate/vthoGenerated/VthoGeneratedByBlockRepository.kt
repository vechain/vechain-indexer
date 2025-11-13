package org.vechain.indexer.stargate.vthoGenerated

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

@Profile("stargate", "vtho-generated-by-block")
interface VthoGeneratedByBlockRepository :
    BaseIndexedRepository<VthoGeneratedByBlock, Long>, TimeFrameRepo<VthoGeneratedByBlock> {
    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<VthoGeneratedByBlock>

    override fun findByBlockTimestampAfter(blockTimestamp: Long): List<VthoGeneratedByBlock>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VthoGeneratedByBlock?
}
