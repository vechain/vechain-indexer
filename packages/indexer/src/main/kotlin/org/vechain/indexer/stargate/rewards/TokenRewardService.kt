package org.vechain.indexer.stargate.rewards

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.concurrent.ConcurrentHashMap
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.ValidatorV2
import org.vechain.indexer.validator.ValidatorV2Repository

@Profile("token-reward")
@Service
open class TokenRewardService(
    private val repository: TokenRewardRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val validatorV2Repository: ValidatorV2Repository,
    private val delegationV2Repository: DelegationRepository,
    private val thorClient: ThorClient,
) {
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
     * @param block Thor block containing validator and transaction info.
     * @param callResponses ABI-decoded inspection results — expected to carry a single
     *   `Energy.totalSupply()` result (see [energyTotalSupplyClause]).
     * @return A list of updated TokenReward documents for this block.
     * @notice Process a block and update validator reward state.
     * @dev Reads validator cycle state from [ValidatorV2Repository] (no aggregator decode), uses
     *   the decoded VTHO total supply to derive the per-block reward, then distributes that reward
     *   proportionally across active delegations.
     */
    open suspend fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<TokenReward>, List<TokenReward>> {
        val validatorId = block.signer

        // Cycle info now comes from the V2 validator collection (was: aggregator decode).
        // dependsOn(delegationIndexer) → transitively dependsOn(validatorV2Indexer) guarantees
        // that ValidatorV2 has applied this block's state by the time we read it here.
        val validator =
            validatorV2Repository.findByIdOrNull(validatorId)
                ?: return Pair(emptyList(), emptyList())

        val blockTotalSupply =
            decodeTotalSupply(callResponses) ?: return Pair(emptyList(), emptyList())

        val latestRewards = getLatestRewards(block, validator)
        if (latestRewards.isEmpty()) {
            return Pair(emptyList(), emptyList())
        }

        val delegatorBlockReward =
            getDelegatorsBlockReward(block, blockTotalSupply)
                ?: return Pair(emptyList(), emptyList())

        val result =
            updateRewardInfo(
                currentTokenRewards = latestRewards,
                totalBlockReward = delegatorBlockReward,
                validator = validatorId,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                blockId = block.id,
            )

        val allPeriodTrackers = result.first.filter { it.rewardPeriod == RewardPeriod.ALL }
        if (allPeriodTrackers.isNotEmpty()) {
            rewardTrackerCache[validatorId] = allPeriodTrackers
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
            inlineVersioningProperties.minVersions,
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
     * @param blockTotalSupply VTHO total supply at [block].
     * @return Total delegators' reward share for the block (70% of the VTHO supply delta), or null
     *   if unavailable.
     */
    suspend fun getDelegatorsBlockReward(block: Block, blockTotalSupply: BigInteger): BigInteger? {
        // Initialize cache on restart using the previous block's reward
        if (vthoTotalSupply == BigInteger.ZERO) {
            vthoTotalSupply = getTotalVTHOIssuedAtBlock(block.parentID)
        }

        val blockReward = blockTotalSupply.subtract(vthoTotalSupply)
        vthoTotalSupply = blockTotalSupply

        return (blockReward * BigInteger.valueOf(7)).divide(BigInteger.TEN)
    }

    /**
     * Get current validator reward trackers, populating new ones on cycle transitions.
     *
     * @param block Current Thor block.
     * @param validator Up-to-date [ValidatorV2] state for [block.signer].
     */
    fun getLatestRewards(block: Block, validator: ValidatorV2): List<TokenReward> {
        val validatorId = validator.id

        var cached = validatorCycleCache[validatorId]
        var newCycle = false

        if (cached == null || block.number > cached.nextCycleBlock) {
            updateValidatorCycleCache(validator)
            newCycle = true
            cached = validatorCycleCache[validatorId] ?: return emptyList()
        }

        // If cache says no delegations, verify against the freshly-read V2 row in case of drift.
        if (!cached.hasDelegations) {
            val hasDelegations = (validator.delegatorVetStaked ?: BigDecimal.ZERO) > BigDecimal.ZERO
            if (!hasDelegations) {
                return emptyList()
            }
            cached.hasDelegations = true
            return getOrFetchRewardsNewCycle(validatorId, block, getTimeInfo(block.timestamp))
        }

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
     * Fetch or create reward trackers for a validator at the start of a new cycle.
     *
     * Reads currently-active delegations from [DelegationRepository] (was V1
     * `delegationRepository`). The `dependsOn(delegationIndexer)` ordering guarantees that
     * delegations transitioning at this block's cycle boundary have already been applied.
     */
    fun getOrFetchRewardsNewCycle(
        validatorId: String,
        block: Block,
        time: LocalDate,
    ): List<TokenReward> {
        val delegations =
            delegationV2Repository.findByValidatorAndStatusIn(
                validatorId,
                listOf(DelegationStatus.ACTIVE, DelegationStatus.EXITING),
            )

        if (delegations.isEmpty()) return emptyList()

        val rewardIds = delegations.map { "$validatorId-${it.tokenId}" }
        val rewardsFromDb = repository.findAllById(rewardIds)
        val existingIds = rewardsFromDb.map { it.id }.toSet()
        val missingDelegations = delegations.filter { "$validatorId-${it.tokenId}" !in existingIds }

        val currentCycle = validatorCycleCache[validatorId]!!.currentCycle

        // TokenLevel.effectiveStake is in VET, need to convert to wei (multiply by 10^18)
        val weiMultiplier = BigDecimal.TEN.pow(18)
        val totalEffectiveStake =
            delegations
                .map { it.tokenLevel.effectiveStake.multiply(weiMultiplier) }
                .fold(BigDecimal.ZERO) { acc, stake -> acc.add(stake) }
                .toBigInteger()
        validatorCycleCache[validatorId]!!.totalEffectiveDelegations = totalEffectiveStake

        if (missingDelegations.isEmpty()) {
            return rewardsFromDb.toList()
        }

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
                    version = 1,
                )
            }

        return rewardsFromDb + newDocs
    }

    /**
     * Update cached cycle info for [validator]. Reads cycle parameters straight off the V2 row — no
     * chain decode needed.
     */
    fun updateValidatorCycleCache(validator: ValidatorV2) {
        val cycleLength = validator.cyclePeriodLength ?: return
        val startBlock = validator.startBlock ?: return
        val completed = validator.completedPeriods ?: 0L
        val hasDelegations = (validator.delegatorVetStaked ?: BigDecimal.ZERO) > BigDecimal.ZERO
        val nextCycleBlock = startBlock + ((completed + 1) * cycleLength)

        validatorCycleCache[validator.id] =
            CycleCache(
                nextCycleBlock = nextCycleBlock,
                hasDelegations = hasDelegations,
                currentCycle = completed + 1L,
            )
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

            var daily = rewardTracker.dayReward ?: BigInteger.ZERO
            var weekly = rewardTracker.weekReward ?: BigInteger.ZERO
            var monthly = rewardTracker.monthReward ?: BigInteger.ZERO
            var yearly = rewardTracker.yearReward ?: BigInteger.ZERO
            var cycle = rewardTracker.cycleReward ?: BigInteger.ZERO

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
                            blockId = blockId,
                            blockNumber = blockNumber,
                            blockTimestamp = blockTimestamp,
                        )
                    )
                    rewardShare
                } else {
                    rewards.add(rewardShare)
                }

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
     * Create a finalized reward document for a closed period (day / week / month / year / cycle).
     */
    fun createPeriodReward(
        id: String,
        period: RewardPeriod,
        rewards: BigInteger,
        mainTracker: TokenReward,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
    ): TokenReward =
        TokenReward(
            id = id,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            tokenId = mainTracker.tokenId,
            cycle = mainTracker.cycle,
            validator = mainTracker.validator,
            rewards = rewards,
            rewardPeriod = period,
            dayOfMonth = mainTracker.dayOfMonth,
            weekOfYear = mainTracker.weekOfYear,
            month = mainTracker.month,
            year = mainTracker.year,
            version = 1,
        )

    /**
     * Read total VTHO issued at a specific block by calling `Energy.totalSupply()` directly. Used
     * as a fallback when [vthoTotalSupply] hasn't been seeded yet (cold start / post-restart).
     */
    suspend fun getTotalVTHOIssuedAtBlock(blockId: String): BigInteger {
        val response =
            thorClient.inspectClauses(listOf(energyTotalSupplyClause()), BlockRevision.Id(blockId))
        if (response.isEmpty()) return BigInteger.ZERO
        return decodeTotalSupply(response[0]) ?: BigInteger.ZERO
    }

    private fun getTimeInfo(blockTimestamp: Long): LocalDate {
        val blockDateTime = Instant.ofEpochSecond(blockTimestamp).atZone(ZoneOffset.UTC)
        return blockDateTime.toLocalDate()
    }

    /**
     * Cache entry for a validator's cycle state.
     *
     * @param nextCycleBlock Block number when the next cycle begins.
     * @param currentCycle Current cycle index for the validator.
     * @param hasDelegations Whether the validator has delegations in this cycle.
     * @param totalEffectiveDelegations Total effective stake delegated in current cycle.
     */
    data class CycleCache(
        var nextCycleBlock: Long,
        var currentCycle: Long = 0L,
        var hasDelegations: Boolean,
        var totalEffectiveDelegations: BigInteger = BigInteger.ZERO,
    )

    companion object {
        private val ENERGY_TOTAL_SUPPLY_ABI =
            FunctionDefinition(
                name = "totalSupply",
                inputs = emptyList(),
                outputs = listOf(FunctionParameter("vthoTotalSupply", "uint256")),
                stateMutability = "view",
            )

        private val DECODE_OUTPUTS = listOf(InputOutput("uint256", "vthoTotalSupply", "uint256"))

        /**
         * The single `callDataClause` this indexer registers — a `view` call to the builtin Energy
         * contract's `totalSupply()`. Exposed for [TokenRewardConfig] to wire into the indexer.
         */
        fun energyTotalSupplyClause(): Clause =
            ContractUtils.createClause(VTHO_CONTRACT_ADDRESS, ENERGY_TOTAL_SUPPLY_ABI)

        /** Decode the first inspection result as a VTHO total-supply value, or null if absent. */
        fun decodeTotalSupply(responses: List<InspectionResult>): BigInteger? {
            if (responses.isEmpty()) return null
            return decodeTotalSupply(responses[0])
        }

        private fun decodeTotalSupply(response: InspectionResult): BigInteger? {
            val data = response.data
            if (data.isBlank() || data == "0x") return null
            val decoded = FunctionReturnDecoder.decode(data, DECODE_OUTPUTS)
            return decoded["vthoTotalSupply"] as? BigInteger
        }
    }
}
