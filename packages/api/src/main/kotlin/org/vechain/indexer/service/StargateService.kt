package org.vechain.indexer.service

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.model.TimeSeriesRecord
import org.vechain.indexer.model.stargate.NftHoldersByBlock
import org.vechain.indexer.model.stargate.VetStakedByBlock
import org.vechain.indexer.repository.stargate.NftHoldersByBlockRepository
import org.vechain.indexer.repository.stargate.VetStakedByBlockRepository
import org.vechain.indexer.repository.stargate.VthoClaimedByAccountRepository
import org.vechain.indexer.repository.stargate.VthoClaimedByBlockRepository
import org.vechain.indexer.utils.HexUtils

@Profile("stargate")
@Service
open class StargateService(
    private val vthoClaimedByBlockRepository: VthoClaimedByBlockRepository,
    private val vthoClaimedByAccountRepository: VthoClaimedByAccountRepository,
    private val nftHoldersByBlockRepository: NftHoldersByBlockRepository,
    private val vetStakedByBlockRepository: VetStakedByBlockRepository,
) {

    /**
     * Retrieves the total VTHO claimed up to a specific block number. If no block number is
     * provided, it retrieves the latest total VTHO claimed.
     *
     * @param blockNumber The block number to retrieve the total VTHO claimed for.
     * @return The total VTHO claimed as a BigInteger.
     */
    open fun getTotalVthoClaimed(blockNumber: Long?): BigInteger {
        val record =
            if (blockNumber != null) {
                vthoClaimedByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
            } else {
                vthoClaimedByBlockRepository.getLatestRecord()
            }
        return record?.total ?: BigInteger.ZERO
    }

    /**
     * Retrieves the total VTHO claimed by a specific account.
     *
     * @param account The account address to retrieve the total VTHO claimed for.
     * @return The total VTHO claimed by the account as a BigInteger.
     */
    open fun getTotalVthoClaimed(account: String): BigInteger =
        vthoClaimedByAccountRepository
            .findById(HexUtils.normalise(account))
            .map { it.total }
            .orElse(BigInteger.ZERO)

    /**
     * Retrieves time series records of total VTHO claimed between two timestamps. The time series
     * is sparsely populated, so it may not contain consistent gaps between records.
     *
     * @param after The starting timestamp.
     * @param before The ending timestamp.
     * @return A list of TimeSeriesRecord containing the total VTHO claimed at each block timestamp.
     */
    open fun getTotalVthoClaimedHistoric(
        after: Long,
        before: Long,
    ): List<TimeSeriesRecord<BigInteger>> {
        val data = vthoClaimedByBlockRepository.findByBlockTimestampBetween(after - 1, before + 1)

        // If no records are found, return an empty list
        if (data.isEmpty()) {
            return emptyList()
        }

        // If a record doesn't exist for the start block, find the latest before it and create a
        // record from that
        val firstRecord = data.first()
        val startBookend =
            if (firstRecord.blockTimestamp > after) {
                val latestBeforeStart =
                    vthoClaimedByBlockRepository.findLatestBeforeOrAtBlockTimestamp(after)
                latestBeforeStart?.let { TimeSeriesRecord(after, it.total) }
            } else null

        // If a record doesn't exist for the end block, find the latest before it and create a
        // record from that
        val lastRecord = data.last()
        val endBookend =
            if (lastRecord.blockTimestamp < before) {
                TimeSeriesRecord(before, lastRecord.total)
            } else null

        // Combine the bookends with the existing data if they are not null
        val records = mutableListOf<TimeSeriesRecord<BigInteger>>()
        startBookend?.let { records.add(it) }
        records.addAll(data.map { TimeSeriesRecord(it.blockTimestamp, it.total) })
        endBookend?.let { records.add(it) }
        return records
    }

    /**
     * Retrieves the total number of NFT holders in Stargate at a specific block number. If no block
     * number is provided, it retrieves the latest total number of NFT holders.
     *
     * @param blockNumber The block number to retrieve the total NFT holders for.
     * @return The total number of NFT holders as an instance of NftHoldersByBlock or null if no
     *   data is found.
     */
    open fun getNftHolders(blockNumber: Long?): NftHoldersByBlock? {
        return if (blockNumber != null) {
            nftHoldersByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
        } else {
            nftHoldersByBlockRepository.getLatestRecord()
        }
    }

    open fun getNftHoldersHistoric(
        after: Long,
        before: Long,
        levelId: Int? = null,
    ): List<TimeSeriesRecord<Long>> {
        val data = nftHoldersByBlockRepository.findByBlockTimestampBetween(after - 1, before + 1)

        if (data.isEmpty()) {
            return emptyList()
        }

        // If a record doesn't exist for the start block, find the latest before it and create a
        // record from that
        val firstRecord = data.first()
        val startBookend =
            if (firstRecord.blockTimestamp > after) {
                val latestBeforeStart =
                    nftHoldersByBlockRepository.findLatestBeforeOrAtBlockTimestamp(after)
                latestBeforeStart?.let { TimeSeriesRecord(after, it.valueForLevel(levelId)) }
            } else null

        // If a record doesn't exist for the end block, find the latest before it and create a
        // record from that
        val lastRecord = data.last()
        val endBookend =
            if (lastRecord.blockTimestamp < before) {
                TimeSeriesRecord(before, lastRecord.valueForLevel(levelId))
            } else null

        // Combine the bookends with the existing data if they are not null
        val records = mutableListOf<TimeSeriesRecord<Long>>()
        startBookend?.let { records.add(it) }
        records.addAll(data.map { TimeSeriesRecord(it.blockTimestamp, it.valueForLevel(levelId)) })
        endBookend?.let { records.add(it) }

        return records
    }

    /**
     * Retrieves the total VET staked in Stargate at a specific block number. If no block number is
     * provided, it retrieves the latest total VET staked.
     *
     * @param blockNumber The block number to retrieve the total VET staked for.
     * @return The total VET staked as an instance of VetStakedByBlock or null if no data is found.
     */
    open fun getTotalVetStaked(blockNumber: Long?): VetStakedByBlock? {
        return if (blockNumber != null) {
            vetStakedByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
        } else {
            vetStakedByBlockRepository.getLatestRecord()
        }
    }

    /**
     * Retrieves time series records of total VET staked between two timestamps. The time series is
     * sparsely populated, so it may not contain consistent gaps between records.
     *
     * @param after The starting timestamp.
     * @param before The ending timestamp.
     * @return A list of TimeSeriesRecord containing the total VET staked at each block timestamp
     */
    open fun getTotalVetStakedHistoric(
        after: Long,
        before: Long,
        levelId: Int? = null,
    ): List<TimeSeriesRecord<BigInteger>> {
        val data = vetStakedByBlockRepository.findByBlockTimestampBetween(after - 1, before + 1)

        if (data.isEmpty()) {
            return emptyList()
        }

        // If a record doesn't exist for the start block, find the latest before it and create a
        // record from that
        val firstRecord = data.first()
        val startBookend =
            if (firstRecord.blockTimestamp > after) {
                val latestBeforeStart =
                    vetStakedByBlockRepository.findLatestBeforeOrAtBlockTimestamp(after)
                latestBeforeStart?.let { TimeSeriesRecord(after, it.valueForLevel(levelId)) }
            } else null

        // If a record doesn't exist for the end block, find the latest before it and create a
        // record from that
        val lastRecord = data.last()
        val endBookend =
            if (lastRecord.blockTimestamp < before) {
                TimeSeriesRecord(before, lastRecord.valueForLevel(levelId))
            } else null

        // Combine the bookends with the existing data if they are not null
        val records = mutableListOf<TimeSeriesRecord<BigInteger>>()
        startBookend?.let { records.add(it) }
        records.addAll(data.map { TimeSeriesRecord(it.blockTimestamp, it.valueForLevel(levelId)) })
        endBookend?.let { records.add(it) }
        return records
    }
}
