package org.vechain.indexer.stargate.vetStaked

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

/** Repository interface for VetStakedByBlock time-series data. */
interface VetStakedByBlockRepository : TimeFrameRepo<VetStakedByBlock> {
    fun saveAll(records: Iterable<VetStakedByBlock>): Iterable<VetStakedByBlock>

    fun deleteAllByBlockNumberGreaterThanEqual(start: Long)

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<VetStakedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetStakedByBlock>

    override fun findByBlockTimestampAfter(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetStakedByBlock>

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): VetStakedByBlock?

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VetStakedByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetStakedByBlock>

    override fun getLatestRecord(): VetStakedByBlock?

    fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<VetStakedByBlock>

    fun findByBlockTimestampAfter(blockTimestamp: Long): List<VetStakedByBlock>

    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): VetStakedByBlock?

    override fun findAll(pageable: Pageable): Slice<VetStakedByBlock>

    override fun findByBlockTimestampBefore(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<VetStakedByBlock>

    override fun findByBlockTimestampBetween(
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<VetStakedByBlock>
}
