package org.vechain.indexer.stargate.nftHolders

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

@Profile("stargate", "nft-holders-by-block")
interface NftHoldersByBlockRepository :
    BaseIndexedRepository<NftHoldersByBlock, Long>, TimeFrameRepo<NftHoldersByBlock> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): NftHoldersByBlock?

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<NftHoldersByBlock>

    override fun findByBlockTimestampAfter(blockTimestamp: Long): List<NftHoldersByBlock>
}
