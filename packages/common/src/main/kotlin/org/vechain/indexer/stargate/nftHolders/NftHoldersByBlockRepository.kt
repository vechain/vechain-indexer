package org.vechain.indexer.stargate.nftHolders

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresIndexedRepository
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo

interface NftHoldersByBlockRepository :
    PostgresIndexedRepository, TimeFrameRepo<NftHoldersByBlock> {

    fun saveAll(records: List<NftHoldersByBlock>)

    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): NftHoldersByBlock?

    override fun findByTimeFramesContains(
        timeFrame: TimeFrame,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock>

    override fun findByBlockTimestampAfter(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampBetween(
        timeFrame: TimeFrame,
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock>

    override fun findByTimeFramesContainsAndBlockTimestampBefore(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock>

    override fun getLatestRecord(): NftHoldersByBlock?

    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): NftHoldersByBlock?

    override fun findAll(pageable: Pageable): Slice<NftHoldersByBlock>

    override fun findByBlockTimestampBefore(
        blockTimestamp: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock>

    override fun findByBlockTimestampBetween(
        from: Long,
        to: Long,
        pageable: Pageable,
    ): Slice<NftHoldersByBlock>

    fun findByTimeFramesContainsAndBlockTimestampAfter(
        timeFrame: TimeFrame,
        blockTimestamp: Long,
    ): List<NftHoldersByBlock>

    fun findByBlockTimestampAfter(blockTimestamp: Long): List<NftHoldersByBlock>
}
