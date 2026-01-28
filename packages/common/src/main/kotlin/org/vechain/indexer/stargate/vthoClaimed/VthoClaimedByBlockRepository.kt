package org.vechain.indexer.stargate.vthoClaimed

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

/** Repository interface for VthoClaimedByBlock time-series data. */
interface VthoClaimedByBlockRepository : TimeFrameRepo<VthoClaimedByBlock> {
    fun saveAll(records: Iterable<VthoClaimedByBlock>): Iterable<VthoClaimedByBlock>

    fun deleteAllByBlockNumberGreaterThanEqual(start: Long)

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VthoClaimedByBlock?

    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): VthoClaimedByBlock?

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VthoClaimedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoClaimedByBlock>

    override fun findByBlockTimestampAfter(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoClaimedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VthoClaimedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoClaimedByBlock>

    override fun getLatestRecord(): VthoClaimedByBlock?

    override fun findAll(pageable: Pageable): Slice<VthoClaimedByBlock>

    override fun findByBlockTimestampBefore(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoClaimedByBlock>

    override fun findByBlockTimestampBetween(
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VthoClaimedByBlock>

    fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<VthoClaimedByBlock>

    fun findByBlockTimestampAfter(blockTimestamp: Long): List<VthoClaimedByBlock>
}
