package org.vechain.indexer.stargate

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.stereotype.Service
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlock
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockRepository
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenRepository
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlock
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockRepository
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlock
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockRepository
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
    private val stargateTokenRepository: StargateTokenRepository,
    private val tokenRewardRepository: TokenRewardRepository,
) {
    /**
     * Retrieves the total VTHO claimed up to a specific block number. If no block number is
     * provided, it retrieves the latest total VTHO claimed.
     *
     * @param blockNumber The block number to retrieve the total VTHO claimed for.
     * @return The total VTHO claimed as a BigInteger.
     */
    open fun getTotalVthoClaimed(blockNumber: Long?, rewardType: String?): BigInteger {
        val record =
            if (blockNumber != null) {
                vthoClaimedByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
            } else {
                vthoClaimedByBlockRepository.getLatestRecord()
            }
        if (rewardType == "LEGACY") {
            return record?.legacyRewards ?: BigInteger.ZERO
        } else if (rewardType == "DELEGATION") {
            return record?.total ?: BigInteger.ZERO
        }
        return (record?.total ?: BigInteger.ZERO) + (record?.legacyRewards ?: BigInteger.ZERO)
    }

    /**
     * Retrieves the total VTHO claimed by a specific account.
     *
     * @param account The account address to retrieve the total VTHO claimed for.
     * @return The total VTHO claimed by the account as a BigInteger.
     */
    open fun getTotalVthoClaimed(account: String, rewardType: String?): BigInteger =
        vthoClaimedByAccountRepository
            .findById(HexUtils.normalise(account))
            .map {
                when (rewardType) {
                    "LEGACY" -> {
                        it.legacyRewards
                    }
                    "DELEGATION" -> {
                        it.delegationRewards
                    }
                    else -> {
                        it.total
                    }
                }
            }
            .orElse(BigInteger.ZERO)

    /**
     * Retrieves the total VTHO claimed by a specific account and token id.
     *
     * @param account The account address to retrieve the total VTHO claimed for.
     * @param tokenId The token ID to retrieve the total VTHO claimed for.
     * @return The total VTHO claimed by the pair account - token ID as a BigInteger.
     */
    open fun getTotalVthoClaimed(
        account: String,
        tokenId: String,
        rewardType: String?,
    ): BigInteger =
        vthoClaimedByAccountRepository
            .findById("${HexUtils.normalise(account)}_${BigInteger(tokenId).toString(10)}")
            .map {
                when (rewardType) {
                    "LEGACY" -> {
                        it.legacyRewards
                    }
                    "DELEGATION" -> {
                        it.delegationRewards
                    }
                    else -> {
                        it.total
                    }
                }
            }
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

    /**
     * Retrieves Stargate tokens with optional filtering by tokenId, manager, or owner.
     *
     * Behavior:
     * - If [tokenId] is provided, returns that single token (or empty slice if not found).
     * - If both [manager] and [owner] are provided, returns tokens matching either.
     * - If only [manager] is provided, returns tokens by that manager.
     * - If only [owner] is provided, returns tokens by that owner.
     * - If no filters are provided, returns all tokens (paginated).
     *
     * @param tokenId Optional token ID to filter by.
     * @param manager Optional manager address to filter by.
     * @param owner Optional owner address to filter by.
     * @param pageable Pagination information.
     * @return A [Slice] of [org.vechain.indexer.stargate.token.StargateToken] results.
     */
    open fun getStargateTokens(
        tokenId: String?,
        manager: String?,
        owner: String?,
        pageable: Pageable,
    ): PaginatedResponse<StargateToken> {
        val slice =
            when {
                tokenId != null -> {
                    stargateTokenRepository
                        .findById(tokenId)
                        .map { SliceImpl(listOf(it), pageable, false) }
                        .orElseGet { SliceImpl(emptyList(), pageable, false) }
                }
                manager != null && owner != null ->
                    stargateTokenRepository.findByOwnerOrManager(owner, manager, pageable)

                manager != null -> stargateTokenRepository.findByManager(manager, pageable)

                owner != null -> stargateTokenRepository.findByOwner(owner, pageable)

                else -> stargateTokenRepository.findAll(pageable)
            }

        return paginatedResponse(slice)
    }

    fun getRewards(
        tokenId: String,
        validator: String?,
        period: RewardPeriod?,
        pageable: Pageable,
    ): Slice<TokenReward> {
        val target = period ?: RewardPeriod.ALL
        val periods: List<RewardPeriod> =
            when (target) {
                RewardPeriod.ALL -> listOf(RewardPeriod.ALL)
                else ->
                    listOf(
                        target,
                        RewardPeriod.ALL,
                    ) // always include ALL alongside the target period
            }

        val slice =
            if (validator != null) {
                tokenRewardRepository.findByTokenIdAndRewardPeriodInAndValidator(
                    tokenId,
                    periods,
                    validator,
                    pageable,
                )
            } else {
                tokenRewardRepository.findByTokenIdAndRewardPeriodIn(tokenId, periods, pageable)
            }

        // If target == ALL → nothing to normalize; return as-is.
        if (target == RewardPeriod.ALL || slice.isEmpty) return slice

        // We included ALL in the query; adjust the ALL doc to look like the target period.
        val sortDesc = pageable.sort.getOrderFor("blockTimestamp")?.isDescending ?: true
        val edgeIndex = if (sortDesc) 0 else slice.content.lastIndex

        val adjustedContent = slice.content.toMutableList()
        val edgeDoc = adjustedContent[edgeIndex]

        if (edgeDoc.rewardPeriod == RewardPeriod.ALL) {
            adjustedContent[edgeIndex] = normalizeAllAs(edgeDoc, target)
        } else {
            // If your data can return ALL not at the edge, optionally scan & normalize the first
            // ALL found:
            val idx = adjustedContent.indexOfFirst { it.rewardPeriod == RewardPeriod.ALL }
            if (idx >= 0) {
                adjustedContent[idx] = normalizeAllAs(adjustedContent[idx], target)
            }
        }

        return SliceImpl(adjustedContent, pageable, slice.hasNext())
    }

    private fun normalizeAllAs(allDoc: TokenReward, target: RewardPeriod): TokenReward {
        val normalized =
            when (target) {
                RewardPeriod.DAY -> allDoc.dayReward
                RewardPeriod.WEEK -> allDoc.weekReward
                RewardPeriod.MONTH -> allDoc.monthReward
                RewardPeriod.YEAR -> allDoc.yearReward
                RewardPeriod.CYCLE -> allDoc.cycleReward
                RewardPeriod.ALL -> allDoc.rewards
            } ?: BigInteger.ZERO

        return allDoc.copy(
            rewards = normalized,
            rewardPeriod = target,
            // hide ALL internals from response
            dayReward = null,
            weekReward = null,
            monthReward = null,
            yearReward = null,
            cycleReward = null,
        )
    }
}
