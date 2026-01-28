package org.vechain.indexer.stargate.vetDelegated

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

/** Repository interface for VetDelegatedByBlock time-series data. */
interface VetDelegatedByBlockRepository : TimeFrameRepo<VetDelegatedByBlock> {
    fun saveAll(records: Iterable<VetDelegatedByBlock>): Iterable<VetDelegatedByBlock>

    fun deleteAllByBlockNumberGreaterThanEqual(start: Long)

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VetDelegatedByBlock?

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock>

    override fun findByBlockTimestampAfter(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock>

    override fun getLatestRecord(): VetDelegatedByBlock?

    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): VetDelegatedByBlock?

    override fun findAll(pageable: Pageable): Slice<VetDelegatedByBlock>

    override fun findByBlockTimestampBefore(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock>

    override fun findByBlockTimestampBetween(
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VetDelegatedByBlock>
}
