package org.vechain.indexer.stargate.rewards

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.isNotEmpty
import kotlin.collections.set
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.domain.ValidatorDecoder.buildVTHOTotalsClauses
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeResponseInfo
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeVTHOIssued
import org.vechain.indexer.validator.logic.ValidatorAssembler.listOf
import org.vechain.indexer.validator.models.DecodedValidatorInfo

@Profile("token-reward")
@Service
open class TokenRewardService(
    private val repository: TokenRewardRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val delegationRepository: DelegationRepository,
    private val thorClient: ThorClient,
) {
    /**
     * @notice Cache of Stargate contract ABI functions.
     * @dev Populated once at service initialization by loading ABIs from disk. Keys are function
     *   names, values are parsed AbiElement objects.
     */
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()

    /**
     * @notice In-memory cache of validator cycle information.
     * @dev Stores per-validator cycle metadata such as next cycle block, current cycle number,
     *   delegation status, and effective stake totals. Keyed by validator address or ID.
     */
    val validatorCycleCache: MutableMap<String, CycleCache> = ConcurrentHashMap()

    /**
     * @notice In-memory cache of ALL-period reward trackers per validator.
     * @dev Avoids re-reading the same reward documents from MongoDB every block. Keyed by validator
     *   address, populated after each block's updateRewardInfo and invalidated on rollback or
     *   restart (starts empty, falls through to DB on first miss).
     */
    private val rewardTrackerCache: MutableMap<String, List<TokenReward>> = ConcurrentHashMap()

    /**
     * @notice Cached VTHO total supply from the previous block.
     * @dev Used to calculate deltas in block rewards between consecutive blocks.
     */
    private var vthoTotalSupply: BigInteger = BigInteger.ZERO

    /**
     * @notice Initialize contract ABI cache at service startup.
     * @dev Loads required Stargate ABI function definitions from resources into {@link
     *   cachedGetValidatorsAbi}. Ensures they are available for all subsequent decode operations.
     */
    init {
        loadAllValidatorAbiFunctions(
            listOf(
                "getValidators",
                "totalStake",
                "vthoTotalSupply",
                "getVetPriceUsd",
                "getVthoPriceUsd",
                "totalBurned",
                "getEffectiveStake",
                "getDelegatorsEffectiveStake",
            )
        )
    }

    /**
     * @param block Thor block containing validator and transaction info.
     * @param callResponses ABI-decoded inspection results for validator state.
     * @return A list of updated TokenReward documents for this block.
     * @notice Process a block and update validator reward state.
     * @dev Decodes validator info, fetches or creates reward trackers, calculates block reward
     *   distribution, and updates ongoing rewards.
     */
    open suspend fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<TokenReward>, List<TokenReward>> {
        val decodedInfo =
            decodeResponseInfo(callResponses, cachedGetValidatorsAbi)
                ?: return Pair(emptyList(), emptyList())

        // Fetch ABIs for decoding
        val latestRewards = getLatestRewards(block, decodedInfo)

        // Validator has no delegations in this cycle
        if (latestRewards.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }

        // Get validator total block reward
        val delegatorBlockReward =
            getDelegatorsBlockReward(block, decodedInfo) ?: return Pair(emptyList(), emptyList())

        // Update reward info for each delegation and handle period rollovers
        val result =
            updateRewardInfo(
                currentTokenRewards = latestRewards,
                totalBlockReward = delegatorBlockReward,
                validator = block.signer,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                blockId = block.id,
            )

        // Cache updated ALL-period trackers so the next block skips the DB read
        val allPeriodTrackers = result.first.filter { it.rewardPeriod == RewardPeriod.ALL }
        if (allPeriodTrackers.isNotEmpty()) {
            rewardTrackerCache[block.signer] = allPeriodTrackers
        }

        return result
    }

    /** @notice Persist a batch of reward records to MongoDB. */
    @Transactional(rollbackFor = [Exception::class])
    open fun save(rewards: List<TokenReward>, archive: List<TokenReward>) {
        saveVersionedDocuments(
            rewards,
            archive,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    /** @notice Clear all in-memory caches. Called on rollback to ensure consistency. */
    open fun invalidateCache() {
        rewardTrackerCache.clear()
        validatorCycleCache.clear()
        vthoTotalSupply = BigInteger.ZERO
    }

    /**
     * @param block Current Thor block.
     * @param decodedInfo Optional decoded validator state info (saves RPC calls if present).
     * @return Total delegators' reward as BigInteger, or null if unavailable.
     * @notice Compute total delegators' reward share for a block.
     * @dev Calculates block reward as the delta in VTHO supply, then applies a 70% weighting to
     *   delegators.
     */
    suspend fun getDelegatorsBlockReward(
        block: Block,
        decodedInfo: DecodedValidatorInfo?,
    ): BigInteger? {
        // Get total VTHO issued at this block
        val blockTotalSupply = getTotalVTHOIssued(decodedInfo, block.id)

        // Initialize cache on restart using the previous block's reward
        if (vthoTotalSupply == BigInteger.ZERO) {
            vthoTotalSupply = getTotalVTHOIssuedAtBlock(block.parentID)
        }

        val blockReward = blockTotalSupply.subtract(vthoTotalSupply)
        vthoTotalSupply = blockTotalSupply // update cache

        return (blockReward * BigInteger.valueOf(7)).divide(BigInteger.TEN)
    }

    /**
     * @param block Current Thor block.
     * @param decodedInfo Decoded validator state from contract call.
     * @return List of TokenReward trackers (existing or newly created).
     * @notice Get current validator reward trackers.
     * @dev Returns ongoing reward docs for a validator in the current cycle. If a new cycle has
     *   started, triggers creation of new docs.
     */
    fun getLatestRewards(block: Block, decodedInfo: DecodedValidatorInfo): List<TokenReward> {
        val validatorId = block.signer

        var cached = validatorCycleCache[validatorId]
        var newCycle = false

        if (cached == null || block.number > cached.nextCycleBlock) {
            // Need to update cycle info
            updateValidatorCycleCache(validatorId, decodedInfo)
            newCycle = true
            cached = validatorCycleCache[validatorId] ?: return emptyList()
        }

        // If cache says no delegations, verify against decodedInfo in case of race condition
        if (!cached.hasDelegations) {
            // Double-check using the actual delegatorsStake from decodedInfo
            val ids = decodedInfo.decodedValidators.listOf<String>("masters")
            val delegatorsStake =
                decodedInfo.decodedValidators.listOf<BigInteger>("delegatorsStake")
            val idx = ids.indexOf(validatorId)

            if (idx == -1 || delegatorsStake[idx] == BigInteger.ZERO) {
                return emptyList() // Confirmed: no delegations
            }
            // Cache was stale - update it and fetch rewards (need to get effective stakes)
            cached.hasDelegations = true
            return getOrFetchRewardsNewCycle(validatorId, block, getTimeInfo(block.timestamp))
        }

        // If new cycle, ensure we have up-to-date delegations
        if (newCycle) {
            return getOrFetchRewardsNewCycle(validatorId, block, getTimeInfo(block.timestamp))
        }

        // Try in-memory cache first (populated after each block's updateRewardInfo)
        val cachedRewards = rewardTrackerCache[validatorId]
        if (!cachedRewards.isNullOrEmpty() && cachedRewards[0].cycle == cached.currentCycle) {
            return cachedRewards
        }

        // Cache miss — fall through to DB (happens once after restart)
        val rewards =
            repository.findAllByValidatorAndRewardPeriodAndCycle(
                validatorId,
                RewardPeriod.ALL,
                cached.currentCycle,
            )

        // If we have delegations but no rewards in DB, or totalEffectiveDelegations not set,
        // fall back to fetching (handles restarts and race conditions)
        if (rewards.isEmpty() || cached.totalEffectiveDelegations == BigInteger.ZERO) {
            return getOrFetchRewardsNewCycle(validatorId, block, getTimeInfo(block.timestamp))
        }

        return rewards
    }

    /**
     * @param validatorId Validator address (signer).
     * @param block Block for inspection.
     * @param time LocalDate derived from block timestamp.
     * @return Combined list of existing + new TokenReward docs.
     * @notice Fetch or create reward trackers for a validator in a new cycle.
     * @dev Ensures every delegation has a TokenReward doc. Queries DB for existing docs and calls
     *   Thor for missing effective stakes.
     */
    fun getOrFetchRewardsNewCycle(
        validatorId: String,
        block: Block,
        time: LocalDate,
    ): List<TokenReward> {
        // 1. Get active/exiting delegations for validator
        val delegations =
            delegationRepository.findByValidatorAndStatusIn(
                validatorId,
                listOf(Status.ACTIVE, Status.EXITING),
            )

        if (delegations.isEmpty()) return emptyList()

        // 2. Build reward doc IDs
        val rewardIds = delegations.map { "$validatorId-${it.tokenId}" }

        // 4. Fetch existing reward docs from DB
        val rewardsFromDb = repository.findAllById(rewardIds)
        val existingIds = rewardsFromDb.map { it.id }.toSet()

        // 5. Find missing delegations
        val missingDelegations = delegations.filter { "$validatorId-${it.tokenId}" !in existingIds }

        val currentCycle = validatorCycleCache[validatorId]!!.currentCycle

        // 6. Calculate total effective stake from delegations (no Thor call needed)
        // TokenLevel.effectiveStake is in VET, need to convert to wei (multiply by 10^18)
        val weiMultiplier = BigDecimal.TEN.pow(18)
        val totalEffectiveStake =
            delegations
                .map { it.tokenLevel.effectiveStake.multiply(weiMultiplier) }
                .fold(BigDecimal.ZERO) { acc, stake -> acc.add(stake) }
                .toBigInteger()
        validatorCycleCache[validatorId]!!.totalEffectiveDelegations = totalEffectiveStake

        // 7. If nothing is missing, just return what we have
        if (missingDelegations.isEmpty()) {
            return rewardsFromDb.toList()
        }

        // 8. Create new docs from delegation data (no Thor calls needed)
        val newDocs =
            missingDelegations.map { delegation ->
                val rewardId = "$validatorId-${delegation.tokenId}"
                val stake =
                    delegation.tokenLevel.effectiveStake.multiply(weiMultiplier).toBigInteger()

                TokenReward(
                    id = rewardId,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    tokenId = delegation.tokenId,
                    cycle = currentCycle,
                    validator = validatorId,
                    rewards = BigInteger.ZERO,
                    effectiveStake = stake,
                    rewardPeriod = RewardPeriod.ALL,
                    dayOfMonth = time.dayOfMonth.toLong(),
                    weekOfYear = time.get(WeekFields.ISO.weekOfYear()).toLong(),
                    month = time.monthValue.toLong(),
                    year = time.year.toLong(),
                    dayReward = null,
                    weekReward = null,
                    monthReward = null,
                    yearReward = null,
                    cycleReward = null,
                    version = 0,
                )
            }

        return rewardsFromDb + newDocs
    }

    /**
     * @param validatorId Validator address (signer).
     * @param decodedInfo Decoded validator state from ABI.
     * @notice Update cached cycle info for a validator.
     * @dev Reads validator ABI fields to compute next cycle start, current cycle number, and
     *   delegation presence.
     */
    fun updateValidatorCycleCache(validatorId: String, decodedInfo: DecodedValidatorInfo) {
        val ids = decodedInfo.decodedValidators.listOf<String>("masters")
        val stakingPeriodLengths =
            decodedInfo.decodedValidators.listOf<BigInteger>("stakingPeriodLengths")
        val startBlocks = decodedInfo.decodedValidators.listOf<BigInteger>("startBlocks")
        val completedPeriods = decodedInfo.decodedValidators.listOf<BigInteger>("completedPeriods")
        val delegatorsStake = decodedInfo.decodedValidators.listOf<BigInteger>("delegatorsStake")

        val idx = ids.indexOf(validatorId)
        if (idx != -1) {
            val cycleLength = stakingPeriodLengths[idx].toLong()
            val startBlock = startBlocks[idx].toLong()
            val completed = completedPeriods[idx].toLong()
            val hasDelegations = delegatorsStake[idx].compareTo(BigInteger.ZERO) != 0
            val nextCycleBlock = startBlock + ((completed + 1) * cycleLength)

            validatorCycleCache[validatorId] =
                CycleCache(
                    nextCycleBlock = nextCycleBlock,
                    hasDelegations = hasDelegations,
                    currentCycle = completed + 1L,
                )
        }
    }

    /**
     * @param currentTokenRewards List of ongoing reward trackers (from DB or new).
     * @param totalBlockReward Total delegators' reward for this block.
     * @param validator Validator address (signer).
     * @param blockNumber Current block number.
     * @param blockTimestamp Current block timestamp (seconds).
     * @param blockId Block ID (hash).
     * @return List of updated TokenReward docs (including rollovers).
     * @notice Update per-delegation rewards for the current block.
     * @dev Splits block reward across delegations proportionally by effective stake. Updates
     *   in-progress counters in `ALL` docs and emits new period docs (DAY/WEEK/MONTH/YEAR/CYCLE)
     *   when rollovers occur.
     */
    fun updateRewardInfo(
        currentTokenRewards: List<TokenReward>,
        totalBlockReward: BigInteger,
        validator: String,
        blockNumber: Long,
        blockTimestamp: Long,
        blockId: String,
    ): Pair<List<TokenReward>, List<TokenReward>> {
        val cycleCache = validatorCycleCache[validator]!!
        val updatedRewards = mutableListOf<TokenReward>()
        val archive = mutableListOf<TokenReward>()

        val blockDateTime = Instant.ofEpochSecond(blockTimestamp).atZone(ZoneOffset.UTC)
        val blockDate = blockDateTime.toLocalDate()

        val blockDay = blockDate.dayOfMonth.toLong()
        val blockWeek = blockDate.get(WeekFields.ISO.weekOfYear()).toLong()
        val blockMonth = blockDate.monthValue.toLong()
        val blockYear = blockDate.year.toLong()

        currentTokenRewards.forEach { rewardTracker ->
            val effectiveStake = rewardTracker.effectiveStake

            val rewardShare =
                if (cycleCache.totalEffectiveDelegations == BigInteger.ZERO) {
                    BigInteger.ZERO
                } else {
                    totalBlockReward
                        .multiply(effectiveStake)
                        .divide(cycleCache.totalEffectiveDelegations)
                }

            // convenience values
            var daily = rewardTracker.dayReward ?: BigInteger.ZERO
            var weekly = rewardTracker.weekReward ?: BigInteger.ZERO
            var monthly = rewardTracker.monthReward ?: BigInteger.ZERO
            var yearly = rewardTracker.yearReward ?: BigInteger.ZERO
            var cycle = rewardTracker.cycleReward ?: BigInteger.ZERO

            // Check rollover for each period
            fun rollover(
                condition: Boolean,
                period: RewardPeriod,
                oldId: String,
                rewards: BigInteger,
            ): BigInteger =
                if (condition && rewards > BigInteger.ZERO) {
                    updatedRewards.add(
                        createPeriodReward(
                            id = oldId,
                            period = period,
                            rewards = rewards,
                            mainTracker = rewardTracker,
                        )
                    )
                    rewardShare // reset to new cycle value
                } else {
                    rewards.add(rewardShare)
                }

            // Apply rollover logic
            daily =
                rollover(
                    rewardTracker.dayOfMonth != blockDay ||
                        rewardTracker.month != blockMonth ||
                        rewardTracker.year != blockYear,
                    RewardPeriod.DAY,
                    "${rewardTracker.id}-day-${rewardTracker.year}-${rewardTracker.month}-${rewardTracker.dayOfMonth}",
                    daily,
                )
            weekly =
                rollover(
                    rewardTracker.weekOfYear != blockWeek || rewardTracker.year != blockYear,
                    RewardPeriod.WEEK,
                    "${rewardTracker.id}-week-${rewardTracker.year}-${rewardTracker.weekOfYear}",
                    weekly,
                )
            monthly =
                rollover(
                    rewardTracker.month != blockMonth || rewardTracker.year != blockYear,
                    RewardPeriod.MONTH,
                    "${rewardTracker.id}-month-${rewardTracker.month}-${rewardTracker.year}",
                    monthly,
                )

            yearly =
                rollover(
                    rewardTracker.year != blockYear,
                    RewardPeriod.YEAR,
                    "${rewardTracker.id}-year-${rewardTracker.year}",
                    yearly,
                )

            cycle =
                rollover(
                    rewardTracker.cycle != cycleCache.currentCycle,
                    RewardPeriod.CYCLE,
                    "${rewardTracker.id}-cycle-${rewardTracker.cycle}",
                    cycle,
                )

            // Update main reward tracker
            val updatedTracker =
                rewardTracker.copy(
                    blockId = blockId,
                    blockNumber = blockNumber,
                    blockTimestamp = blockTimestamp,
                    rewards = rewardTracker.rewards.add(rewardShare),
                    dayReward = daily,
                    weekReward = weekly,
                    monthReward = monthly,
                    yearReward = yearly,
                    cycleReward = cycle,
                    cycle = cycleCache.currentCycle,
                    dayOfMonth = blockDay,
                    weekOfYear = blockWeek,
                    month = blockMonth,
                    year = blockYear,
                    version = rewardTracker.version + 1,
                )

            updatedRewards.add(updatedTracker)
            archive.add(rewardTracker)
        }

        return updatedRewards to archive
    }

    /**
     * @param id Unique doc ID (validator-tokenId-period).
     * @param period RewardPeriod (DAY, WEEK, MONTH, YEAR, CYCLE).
     * @param rewards Total reward for the period.
     * @param mainTracker Current ALL tracker providing context.
     * @return New TokenReward doc representing finalized period totals.
     * @notice Create a finalized reward document for a closed period.
     * @dev Copies metadata from the main tracker but stores only the finalized reward total.
     */
    fun createPeriodReward(
        id: String,
        period: RewardPeriod,
        rewards: BigInteger,
        mainTracker: TokenReward,
    ): TokenReward =
        TokenReward(
            id = id,
            blockId = mainTracker.blockId,
            blockNumber = mainTracker.blockNumber,
            blockTimestamp = mainTracker.blockTimestamp,
            tokenId = mainTracker.tokenId,
            cycle = mainTracker.cycle,
            validator = mainTracker.validator,
            rewards = rewards,
            rewardPeriod = period,
            dayOfMonth = mainTracker.dayOfMonth,
            weekOfYear = mainTracker.weekOfYear,
            month = mainTracker.month,
            year = mainTracker.year,
            version = 0,
        )

    /**
     * @param decodedInfo Optional decoded validator info (saves Thor RPC calls).
     * @param blockId Block ID.
     * @return Total VTHO issued at this block.
     * @notice Get total VTHO issued at a block.
     * @dev Adds totalSupply + burned, optionally using decodedInfo if present.
     */
    suspend fun getTotalVTHOIssued(
        decodedInfo: DecodedValidatorInfo?,
        blockId: String,
    ): BigInteger {
        if (decodedInfo == null) {
            return getTotalVTHOIssuedAtBlock(blockId)
        }
        return decodedInfo.vthoTotalSupply
    }

    /**
     * @param blockId Block ID.
     * @return Total VTHO issued.
     * @notice Get total VTHO issued at a specific block via Thor calls.
     * @dev Calls vthoTotalSupply + totalBurned contract functions and decodes result.
     */
    suspend fun getTotalVTHOIssuedAtBlock(blockId: String): BigInteger {
        val response =
            thorClient.inspectClauses(buildVTHOTotalsClauses(), BlockRevision.Id(blockId))

        if (response.size < 2) {
            return BigInteger.ZERO
        }

        val inspectionResults =
            listOf(
                InspectionResult(
                    data = response[0].data,
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = false,
                    vmError = null,
                ),
                InspectionResult(
                    data = response[1].data,
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = false,
                    vmError = null,
                ),
            )

        return decodeVTHOIssued(inspectionResults)
    }

    /**
     * @param functionNames List of ABI function names to load from resources.
     * @notice Load and cache Stargate validator ABI functions.
     * @dev Prevents repeated ABI loading by caching function definitions. Will skip execution if
     *   already populated.
     */
    private fun loadAllValidatorAbiFunctions(functionNames: List<String>) {
        if (cachedGetValidatorsAbi.isNotEmpty()) return // already loaded

        val abis = AbiLoader.load(basePath = "abis/stargate", names = functionNames)

        abis.forEach { abi -> cachedGetValidatorsAbi[abi.name!!] = abi }
    }

    /**
     * @param blockTimestamp Unix epoch seconds from Thor block header.
     * @return LocalDate corresponding to the block timestamp.
     * @notice Convert a Thor block timestamp into LocalDate.
     * @dev Uses UTC zone offset to derive date, day, week, month, and year fields.
     */
    private fun getTimeInfo(blockTimestamp: Long): LocalDate {
        val blockDateTime = Instant.ofEpochSecond(blockTimestamp).atZone(ZoneOffset.UTC)
        return blockDateTime.toLocalDate()
    }

    /**
     * @param nextCycleBlock Block number when the next cycle begins.
     * @param currentCycle Current cycle index for the validator.
     * @param hasDelegations Whether the validator has delegations in this cycle.
     * @param totalEffectiveDelegations Total effective stake delegated in current cycle.
     * @notice Cache entry for a validator's cycle state.
     * @dev Stores next cycle boundary, delegation presence, current cycle index, and total
     *   effective delegation stake.
     */
    data class CycleCache(
        var nextCycleBlock: Long,
        var currentCycle: Long = 0L,
        var hasDelegations: Boolean,
        var totalEffectiveDelegations: BigInteger = BigInteger.ZERO,
    )
}
