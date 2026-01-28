package org.vechain.indexer.stargate.vthoGenerated

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

/** Repository interface for VthoGeneratedByBlock time-series data. */
interface VthoGeneratedByBlockRepository : TimeFrameRepo<VthoGeneratedByBlock> {
    fun saveAll(records: Iterable<VthoGeneratedByBlock>): Iterable<VthoGeneratedByBlock>

    fun deleteAllByBlockNumberGreaterThanEqual(start: Long)

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock>

    override fun findByBlockTimestampAfter(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock>

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VthoGeneratedByBlock?

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock>

    override fun getLatestRecord(): VthoGeneratedByBlock?

    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): VthoGeneratedByBlock?

    override fun findAll(pageable: Pageable): Slice<VthoGeneratedByBlock>

    override fun findByBlockTimestampBefore(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock>

    override fun findByBlockTimestampBetween(
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VthoGeneratedByBlock>

    fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<VthoGeneratedByBlock>

    fun findByBlockTimestampAfter(blockTimestamp: Long): List<VthoGeneratedByBlock>
}
