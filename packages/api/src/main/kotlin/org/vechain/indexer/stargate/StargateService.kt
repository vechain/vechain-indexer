package org.vechain.indexer.stargate

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.stereotype.Service
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockRepository
import org.vechain.indexer.stargate.timeFrame.TimeFrameDocument
import org.vechain.indexer.stargate.timeFrame.TimeFrameRepo
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenRepository
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockRepository
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockRepository
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.timeseries.TimeRangePreset
import org.vechain.indexer.timeseries.TimeSeriesRecord
import org.vechain.indexer.utils.BigIntegerUtils
import org.vechain.indexer.utils.IdUtils

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
     * @param blockNumber The block number to query, or null for the latest.
     * @param rewardType Optional filter: "LEGACY", "DELEGATION", or null for combined totals.
     * @return Total VTHO claimed based on the selected reward type.
     * @notice Returns the cumulative VTHO claimed up to a specific block.
     * @dev If blockNumber is null, the latest record is returned. rewardType controls whether to
     *   return LEGACY-only, DELEGATION-only, or combined totals.
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
     * @param account Account address in hex format.
     * @param rewardType Optional filter: "LEGACY", "DELEGATION", or null for combined totals.
     * @return Total VTHO claimed by the account.
     * @notice Retrieves the total VTHO claimed by a specific account.
     * @dev If the account has never claimed rewards, zero is returned.
     */
    open fun getTotalVthoClaimed(account: String, rewardType: String?): BigInteger =
        vthoClaimedByAccountRepository
            .findById(IdUtils.generateId(HexUtils.normalise(account)))
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
     * @param account Owner or delegator address.
     * @param tokenId Token ID in hex or decimal.
     * @param rewardType Optional filter selecting LEGACY, DELEGATION, or all rewards.
     * @return Total VTHO claimed for the given token.
     * @notice Retrieves total VTHO claimed for an (account, tokenId) pair.
     * @dev Accounts may have multiple Stargate tokens; this returns the per-token claim total.
     */
    open fun getTotalVthoClaimed(
        account: String,
        tokenId: String,
        rewardType: String?,
    ): BigInteger =
        vthoClaimedByAccountRepository
            .findById(
                IdUtils.generateId(
                    HexUtils.normalise(account),
                    BigIntegerUtils.fromHexOrDecimal(tokenId).toString(10),
                )
            )
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
     * @param blockNumber Block number to query.
     * @return Total VTHO generated at or before the block.
     * @notice Returns total VTHO generated (claimed + wallet balance) up to a block.
     * @dev If blockNumber is null, the latest generated total is returned.
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
     * @param timeRange Preset defining how far back to query.
     * @param after Unix timestamp lower bound (exclusive).
     * @return List of timestamp-value records of VTHO claimed.
     * @notice Returns a time series of cumulative VTHO claimed values.
     * @dev Uses block-level granularity unless a timeframe (WEEK/MONTH/YEAR) is applied, in which
     *   case only blocks that rolled that timeframe are included.
     */
    open fun getTotalVthoClaimedHistoric(
        timeRange: TimeRangePreset,
        after: Long,
    ): List<TimeSeriesRecord<BigInteger>> {
        val timeFrame = toTimeFrame(timeRange)

        val rows =
            if (timeFrame == null) {
                vthoClaimedByBlockRepository.findByBlockTimestampAfter(after)
            } else {
                vthoClaimedByBlockRepository.findByTimeFramesContainsAndBlockTimestampAfter(
                    timeFrame,
                    after,
                )
            }

        return rows.map { rec ->
            TimeSeriesRecord(timestamp = rec.blockTimestamp, value = rec.total)
        }
    }

    /**
     * @param timeRange Range preset to determine timeframe granularity.
     * @param after Only records strictly after this timestamp are returned.
     * @return TimeSeries records for VTHO generation totals.
     * @notice Returns historic cumulative VTHO generation totals.
     * @dev Uses the same timeFrame logic as VTHO-claimed historic queries.
     */
    open fun getTotalVthoGeneratedHistoric(
        timeRange: TimeRangePreset,
        after: Long,
    ): List<TimeSeriesRecord<BigInteger>> {
        val timeFrame = toTimeFrame(timeRange)

        val rows =
            if (timeFrame == null) {
                vthoGeneratedByBlockRepository.findByBlockTimestampAfter(after)
            } else {
                vthoGeneratedByBlockRepository.findByTimeFramesContainsAndBlockTimestampAfter(
                    timeFrame,
                    after,
                )
            }

        return rows.map { rec ->
            TimeSeriesRecord(timestamp = rec.blockTimestamp, value = rec.total)
        }
    }

    /**
     * @param blockNumber Block number to query or null for latest.
     * @return DTO containing total holder count and per-level breakdown.
     * @notice Retrieves the number of Stargate NFT holders at a block.
     * @dev Returns a DTO; if no record exists, a zeroed default object is returned.
     */
    open fun getNftHolders(blockNumber: Long?): NftHoldersByBlockDto {
        val response =
            if (blockNumber != null) {
                nftHoldersByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
            } else {
                nftHoldersByBlockRepository.getLatestRecord()
            }

        if (response == null) {
            return NftHoldersByBlockDto(
                blockId = "ignoredanyway",
                blockNumber = 0,
                blockTimestamp = 0,
                total = 0L,
                byLevel = emptyMap(),
            )
        }

        return NftHoldersByBlockDto(
            blockId = response.blockId,
            blockNumber = response.blockNumber,
            blockTimestamp = response.blockTimestamp,
            total = response.total,
            byLevel = response.byLevel,
        )
    }

    /**
     * @param after Only records after this timestamp.
     * @param timeRange How far back to fetch and which timeframe to use.
     * @param level Optional NFT level filter; null means total holders.
     * @return Time series of NFT holder counts.
     * @notice Returns historic NFT holder counts.
     * @dev If a timeframe is applied, only the blocks that rolled that timeframe are included.
     */
    open fun getNftHoldersHistoric(
        after: Long,
        timeRange: TimeRangePreset,
        level: TokenLevel? = null,
    ): List<TimeSeriesRecord<Long>> {
        val timeFrame = toTimeFrame(timeRange)

        val rows =
            if (timeFrame == null) {
                nftHoldersByBlockRepository.findByBlockTimestampAfter(after)
            } else {
                nftHoldersByBlockRepository.findByTimeFramesContainsAndBlockTimestampAfter(
                    timeFrame,
                    after,
                )
            }

        return rows.map { rec ->
            TimeSeriesRecord(timestamp = rec.blockTimestamp, value = rec.valueForLevel(level))
        }
    }

    fun toTimeFrame(timeRange: TimeRangePreset): TimeFrame? =
        when (timeRange) {
            TimeRangePreset.ONE_DAY -> TimeFrame.HOUR
            TimeRangePreset.ONE_WEEK -> TimeFrame.HOUR
            TimeRangePreset.ONE_MONTH -> TimeFrame.DAY
            TimeRangePreset.ONE_YEAR -> TimeFrame.DAY
            TimeRangePreset.ALL -> TimeFrame.WEEK
            else -> null
        }

    /**
     * @param blockNumber Block height or null for latest.
     * @return DTO containing total staked and per-level values.
     * @notice Returns total VET staked at a specific block.
     * @dev If blockNumber is null, returns the latest available record.
     */
    open fun getTotalVetStaked(blockNumber: Long?): TotalByBlockDto {
        val response =
            if (blockNumber != null) {
                vetStakedByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
            } else {
                vetStakedByBlockRepository.getLatestRecord()
            }

        if (response == null) {
            return TotalByBlockDto(
                blockId = "ignoredanyway",
                blockNumber = 0,
                blockTimestamp = 0,
                total = BigInteger.ZERO,
                byLevel = emptyMap(),
                totalNftCount = 0,
                nftCountByLevel = emptyMap(),
            )
        }

        return TotalByBlockDto(
            blockId = response.blockId,
            blockNumber = response.blockNumber,
            blockTimestamp = response.blockTimestamp,
            total = response.total,
            byLevel = response.byLevel,
            totalNftCount = response.totalNftCount,
            nftCountByLevel = response.nftCountByLevel,
        )
    }

    /**
     * @param after Lower timestamp bound.
     * @param timeRange Range preset controlling timeframe and depth.
     * @param level Optional NFT level to return only that level's staked amount.
     * @return Time series of VET staked totals.
     * @notice Returns historic VET staked snapshots.
     * @dev Granularity depends on timeRange; DAILY → block-level, WEEK/MONTH/YEAR → rolled periods
     *   only.
     */
    open fun getTotalVetDelegated(blockNumber: Long?): TotalByBlockDto? {
        val response =
            if (blockNumber != null) {
                vetDelegatedByBlockRepository.findLatestBeforeOrAtBlockNumber(blockNumber)
            } else {
                vetDelegatedByBlockRepository.getLatestRecord()
            }

        if (response == null) {
            return TotalByBlockDto(
                blockId = "ignoredanyway",
                blockNumber = 0,
                blockTimestamp = 0,
                total = BigInteger.ZERO,
                byLevel = emptyMap(),
                totalNftCount = 0,
                nftCountByLevel = emptyMap(),
            )
        }

        return TotalByBlockDto(
            blockId = response.blockId,
            blockNumber = response.blockNumber,
            blockTimestamp = response.blockTimestamp,
            total = response.total,
            byLevel = response.byLevel,
            totalNftCount = response.totalNftCount,
            nftCountByLevel = response.nftCountByLevel,
        )
    }

    /**
     * @param after Lower timestamp bound.
     * @param timeRange Range preset controlling timeframe and depth.
     * @param level Optional NFT level to return only that level's staked amount.
     * @return Time series of VET staked totals.
     * @notice Returns historic VET staked snapshots.
     * @dev Granularity depends on timeRange; DAILY → block-level, WEEK/MONTH/YEAR → rolled periods
     *   only.
     */
    open fun getTotalVetStakedHistoric(
        after: Long,
        timeRange: TimeRangePreset,
        level: TokenLevel? = null,
    ): List<TimeSeriesRecord<BigInteger>> {
        val timeFrame = toTimeFrame(timeRange)

        val rows =
            if (timeFrame == null) {
                vetStakedByBlockRepository.findByBlockTimestampAfter(after)
            } else {
                vetStakedByBlockRepository.findByTimeFramesContainsAndBlockTimestampAfter(
                    timeFrame,
                    after,
                )
            }

        return rows.map { rec ->
            TimeSeriesRecord(timestamp = rec.blockTimestamp, value = rec.valueForLevel(level))
        }
    }

    /**
     * Retrieves Stargate tokens with optional filtering by tokenId, manager, or owner.
     *
     * Behavior:
     * - If [tokenId] is provided, returns that single token (or empty slice if not found).
     * - If both [manager] and [owner] are provided, returns unburned tokens matching either.
     * - If only [manager] is provided, returns unburned tokens by that manager.
     * - If only [owner] is provided, returns unburned tokens by that owner.
     * - If no filters are provided, returns all unburned tokens (paginated).
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
                    stargateTokenRepository.findActiveByOwnerOrManager(
                        owner,
                        manager,
                        pageable = pageable,
                    )

                manager != null ->
                    stargateTokenRepository.findActiveByManager(manager, pageable = pageable)

                owner != null ->
                    stargateTokenRepository.findActiveByOwner(owner, pageable = pageable)

                else -> stargateTokenRepository.findAllActive(pageable = pageable)
            }

        return paginatedResponse(slice)
    }

    /**
     * @param tokenId ID of the Stargate token.
     * @param validator Optional validator address.
     * @param period Optional RewardPeriod; null defaults to ALL.
     * @param pageable Pagination settings.
     * @return Slice containing reward records.
     * @notice Returns reward history for a token, optionally filtered by validator and period.
     * @dev If a specific period (DAY/WEEK/MONTH/YEAR/CYCLE) is requested, ALL-period records are
     *   normalized to behave like the requested period.
     */
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

        // Normalize all ALL documents to the target period
        val adjustedContent =
            slice.content.map { doc ->
                if (doc.rewardPeriod == RewardPeriod.ALL) {
                    normalizeAllAs(doc, target)
                } else {
                    doc
                }
            }

        return SliceImpl(adjustedContent, pageable, slice.hasNext())
    }

    open fun <T : TimeFrameDocument> getTimeFrameDataRange(
        from: Long?,
        to: Long?,
        timeFrame: TimeFrame?,
        pageable: Pageable,
        repository: TimeFrameRepo<T>,
    ): Slice<TotalByPeriodDto> {
        if (timeFrame == TimeFrame.ALL) {
            val time = to ?: from
            val latest =
                repository.findLatestBeforeOrAtBlockTimestamp(time!!)
                    ?: return SliceImpl(emptyList(), pageable, false)

            val dto = normalizeTimeFrameAs(TimeFrame.DAY, latest)

            return SliceImpl(listOf(dto), pageable, false) // only one result
        }

        val slice: Slice<T> =
            when {
                from != null && to != null ->
                    if (timeFrame == null) {
                        repository.findByBlockTimestampBetween(from, to, pageable)
                    } else {
                        repository.findByTimeFramesContainsAndBlockTimestampBetween(
                            timeFrame,
                            from,
                            to,
                            pageable,
                        )
                    }
                from != null ->
                    if (timeFrame == null) {
                        repository.findByBlockTimestampAfter(from, pageable)
                    } else {
                        repository.findByTimeFramesContainsAndBlockTimestampAfter(
                            timeFrame,
                            from,
                            pageable,
                        )
                    }

                to != null ->
                    if (timeFrame == null) {
                        repository.findByBlockTimestampBefore(to, pageable)
                    } else {
                        repository.findByTimeFramesContainsAndBlockTimestampBefore(
                            timeFrame,
                            to,
                            pageable,
                        )
                    }

                else -> SliceImpl(emptyList(), pageable, false)
            }

        val mappedSlice = slice.map { normalizeTimeFrameAs(timeFrame, it) }

        return SliceImpl(mappedSlice.content, pageable, slice.hasNext())
    }

    open fun <T : TimeFrameDocument> getTimeFrameData(
        timeFrame: TimeFrame?,
        pageable: Pageable,
        direction: String? = "DESC",
        repository: TimeFrameRepo<T>,
    ): Slice<TotalByPeriodDto> {
        if (timeFrame == TimeFrame.ALL) {
            val latest =
                repository.getLatestRecord() ?: return SliceImpl(emptyList(), pageable, false)

            val dto = normalizeTimeFrameAs(TimeFrame.DAY, latest)

            return SliceImpl(listOf(dto), pageable, false) // only one result
        }

        val isDesc = direction.isNullOrBlank() || direction.equals("DESC", ignoreCase = true)
        val willPrependLatest = isDesc && pageable.pageNumber == 0

        // When prepending latest on page 0, we need to fetch one less record.
        // For subsequent pages, we need to adjust offset by -1 to account for this.
        // Since PageRequest doesn't support arbitrary offsets, we fetch from (page-1)
        // with extra size, then skip/take the right records.
        val adjustedPageable: Pageable?
        val skipCount: Int

        if (willPrependLatest && pageable.pageSize > 1) {
            // Page 0: fetch size-1 records, prepend latest
            adjustedPageable = PageRequest.of(0, pageable.pageSize - 1, pageable.sort)
            skipCount = 0
        } else if (willPrependLatest) {
            // size=1 and prepending: don't fetch from DB, just return latest
            adjustedPageable = null
            skipCount = 0
        } else if (isDesc && pageable.pageNumber > 0) {
            // Pages > 0 when DESC: need offset (page * size - 1)
            // Fetch from page (N-1) with size (size * 2 - 1), then skip (size-1) records
            adjustedPageable =
                PageRequest.of(pageable.pageNumber - 1, pageable.pageSize * 2 - 1, pageable.sort)
            skipCount = pageable.pageSize - 1
        } else {
            adjustedPageable = pageable
            skipCount = 0
        }

        val rawSlice: Slice<T> =
            if (adjustedPageable == null) {
                SliceImpl(emptyList(), pageable, true)
            } else if (timeFrame == null) {
                repository.findAll(adjustedPageable)
            } else {
                repository.findByTimeFramesContains(timeFrame, adjustedPageable)
            }

        // Apply skip and take for adjusted pagination
        val sliceContent = rawSlice.content.drop(skipCount).take(pageable.pageSize)
        val slice =
            SliceImpl(
                sliceContent,
                pageable,
                rawSlice.content.size > skipCount + pageable.pageSize || rawSlice.hasNext(),
            )

        val shouldInsertLatest =
            if (isDesc) {
                pageable.pageNumber == 0 // prepend
            } else {
                !slice.hasNext() // append
            }

        if (!shouldInsertLatest) {
            return slice.map { normalizeTimeFrameAs(timeFrame, it) }
        }

        val latest =
            repository.getLatestRecord() ?: return slice.map { normalizeTimeFrameAs(timeFrame, it) }

        val latestNormalized = normalizeTimeFrameAs(timeFrame, latest)
        val sliceNormalized = slice.content.map { normalizeTimeFrameAs(timeFrame, it) }

        val merged =
            if (isDesc) {
                listOf(latestNormalized) + sliceNormalized // newest first
            } else {
                sliceNormalized + latestNormalized // oldest first
            }

        val hasNext = slice.hasNext() || (adjustedPageable == null)

        return SliceImpl(merged, pageable, hasNext)
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

    private fun normalizeTimeFrameAs(target: TimeFrame?, doc: TimeFrameDocument): TotalByPeriodDto {
        val normalized =
            when (target) {
                TimeFrame.HOUR -> doc.hourTotal
                TimeFrame.DAY -> doc.dayTotal
                TimeFrame.WEEK -> doc.weekTotal
                TimeFrame.MONTH -> doc.monthTotal
                TimeFrame.YEAR -> doc.yearTotal
                null -> doc.blockTotal
                else -> null
            } ?: BigInteger.ZERO

        val timeFrame = target?.name ?: "BLOCK"

        return TotalByPeriodDto(
            doc.blockId,
            doc.blockNumber,
            doc.blockTimestamp,
            timeFrame = timeFrame,
            total = normalized,
            hourOfDay = doc.hourOfDay,
            dayOfMonth = doc.dayOfMonth,
            weekOfYear = doc.weekOfYear,
            month = doc.month,
            year = doc.year,
        )
    }
}
