package org.vechain.indexer.stargate

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.timeseries.TimeSeriesRecord
import org.vechain.indexer.utils.TimeSeriesUtils

@Profile("stargate")
@Service
open class StargateService(
    private val vthoClaimedByBlockRepository: VthoClaimedByBlockRepository,
    private val vthoClaimedByAccountRepository: VthoClaimedByAccountRepository,
    private val nftHoldersByBlockRepository: NftHoldersByBlockRepository,
    private val vetStakedByBlockRepository: VetStakedByBlockRepository,
    private val vthoGeneratedByBlockRepository: VthoGeneratedByBlockRepository,
    private val vetDelegatedByBlockRepository: VetDelegatedByBlockRepository,
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
     * Retrieves the cumulative total of VTHO generated (claimed + balance) up to the given block.
     *
     * Behavior:
     * - If [blockNumber] is provided, returns the latest total at or before that block.
     * - If [blockNumber] is null, returns the total from the latest persisted record.
     * - If no record is found, returns [BigInteger.ZERO].
     *
     * @param blockNumber the block number to query, or null to use the latest record
     * @return the total VTHO generated as a [BigInteger]
     */
    open fun getTotalVthoGenerated(blockNumber: Long?): BigInteger {
        val record =
            if (blockNumber != null) {
                vthoGeneratedByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
            } else {
                vthoGeneratedByBlockRepository.getLatestRecord()
            }
        return record?.total ?: BigInteger.ZERO
    }

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
    ): List<TimeSeriesRecord<BigInteger>> =
        TimeSeriesUtils.getHistoricTimeSeries(
            after,
            before,
            vthoClaimedByBlockRepository::findByBlockTimestampBetween,
            vthoClaimedByBlockRepository::findLatestBeforeOrAtBlockTimestamp,
        ) {
            it.total
        }

    /**
     * Retrieves time series records of total VTHO generated between two timestamps. The time series
     * is sparsely populated, so it may not contain consistent gaps between records.
     *
     * @param after The starting timestamp.
     * @param before The ending timestamp.
     * @return A list of TimeSeriesRecord containing the total VTHO generated at each block
     *   timestamp.
     */
    open fun getTotalVthoGeneratedHistoric(
        after: Long,
        before: Long,
    ): List<TimeSeriesRecord<BigInteger>> =
        TimeSeriesUtils.getHistoricTimeSeries(
            after,
            before,
            vthoGeneratedByBlockRepository::findByBlockTimestampBetween,
            vthoGeneratedByBlockRepository::findLatestBeforeOrAtBlockTimestamp,
        ) {
            it.total
        }

    /**
     * Retrieves the total number of NFT holders in Stargate at a specific block number. If no block
     * number is provided, it retrieves the latest total number of NFT holders.
     *
     * @param blockNumber The block number to retrieve the total NFT holders for.
     * @return The total number of NFT holders as an instance of NftHoldersByBlock or null if no
     *   data is found.
     */
    open fun getNftHolders(blockNumber: Long?): NftHoldersByBlock? =
        if (blockNumber != null) {
            nftHoldersByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
        } else {
            nftHoldersByBlockRepository.getLatestRecord()
        }

    open fun getNftHoldersHistoric(
        after: Long,
        before: Long,
        level: TokenLevel? = null,
    ): List<TimeSeriesRecord<Long>> =
        TimeSeriesUtils.getHistoricTimeSeries(
            after,
            before,
            nftHoldersByBlockRepository::findByBlockTimestampBetween,
            nftHoldersByBlockRepository::findLatestBeforeOrAtBlockTimestamp,
        ) {
            it.valueForLevel(level)
        }

    /**
     * Retrieves the total VET staked in Stargate at a specific block number. If no block number is
     * provided, it retrieves the latest total VET staked.
     *
     * @param blockNumber The block number to retrieve the total VET staked for.
     * @return The total VET staked as an instance of VetStakedByBlock or null if no data is found.
     */
    open fun getTotalVetStaked(blockNumber: Long?): VetStakedByBlock? =
        if (blockNumber != null) {
            vetStakedByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
        } else {
            vetStakedByBlockRepository.getLatestRecord()
        }

    /**
     * Retrieves the total VET delegated in Stargate at a specific block number. If no block number
     * is provided, it retrieves the latest total VET delegated.
     *
     * @param blockNumber The block number to retrieve the total VET delegated for.
     * @return The total VET delegated as an instance of VetDelegatedByBlock or null if no data is
     *   found.
     */
    open fun getTotalVetDelegated(blockNumber: Long?): VetDelegatedByBlock? =
        if (blockNumber != null) {
            vetDelegatedByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
        } else {
            vetDelegatedByBlockRepository.getLatestRecord()
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
        level: TokenLevel? = null,
    ): List<TimeSeriesRecord<BigInteger>> =
        TimeSeriesUtils.getHistoricTimeSeries(
            after,
            before,
            vetStakedByBlockRepository::findByBlockTimestampBetween,
            vetStakedByBlockRepository::findLatestBeforeOrAtBlockTimestamp,
        ) {
            it.valueForLevel(level)
        }
}
