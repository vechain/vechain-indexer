package org.vechain.indexer.stargate.vthoClaimed

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

@Profile("stargate", "vtho-claimed-by-block")
interface VthoClaimedByBlockRepository :
    BaseIndexedRepository<VthoClaimedByBlock, Long>, TimeFrameRepo<VthoClaimedByBlock> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VthoClaimedByBlock?

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VthoClaimedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<VthoClaimedByBlock>

    override fun findByBlockTimestampAfter(blockTimestamp: Long): List<VthoClaimedByBlock>
}
