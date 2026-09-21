package org.vechain.indexer.stargate.rewards

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardWriteRepository
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.validator.DelegationReadRepository
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorReadRepository

@Profile("token-reward")
@Service
open class TokenRewardService(
    private val repository: TokenRewardWriteRepository,
    private val validatorV2Repository: ValidatorReadRepository,
    private val delegationV2Repository: DelegationReadRepository,
    private val thorClient: ThorClient,
    @param:Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    private val stakerAddress: String,
    @param:Value("\${indexer.start-block.validator}") private val validatorStartBlock: Long,
) {
    /**
     * @notice In-memory cache of validator cycle information.
     * @dev Stores per-validator cycle metadata such as next cycle block, current cycle number,
     *   delegation status, and effective stake totals. Keyed by validator address or ID.
     */
    val validatorCycleCache: MutableMap<String, CycleCache> = ConcurrentHashMap()

    /**
     * @notice In-memory cache of ALL-period reward trackers per validator.
     * @dev Avoids re-reading the same reward rows from Postgres every block. Keyed by validator
     *   address, populated after each block's updateRewardInfo and invalidated on rollback or
     *   restart (starts empty, falls through to DB on first miss).
     */
    private val rewardTrackerCache: MutableMap<String, List<TokenReward>> = ConcurrentHashMap()

    /** Each validator's last-read delegator pool by cycle key: its next block's baseline. */
    private val poolCache: MutableMap<String, Map<Long, BigInteger>> = ConcurrentHashMap()

    private val delegatorsRewardsAbi: AbiElement by lazy {
        AbiLoader.loadFunctions(STAKER_ABI_PATH, listOf(DELEGATORS_REWARDS_FN)).first {
            it.name == DELEGATORS_REWARDS_FN
        }
    }

    /**
     * @param block Thor block containing validator and transaction info.
     * @return A list of updated TokenReward documents for this block.
     * @notice Process a block and update validator reward state.
     * @dev The reward is the signer's delegator-pool growth, see [delegatorBlockReward].
     */
    open suspend fun processBlock(block: Block): List<TokenReward> {
        if (block.number < validatorStartBlock) return emptyList()

        val validatorId = block.signer

        // Cycle info now comes from the V2 validator collection (was: aggregator decode).
        // dependsOn(delegationIndexer) → transitively dependsOn(validatorIndexer) guarantees
        // that Validator has applied this block's state by the time we read it here.
        val validator = validatorV2Repository.findById(validatorId) ?: return emptyList()

        val latestRewards = getLatestRewards(block, validator)
        if (latestRewards.isEmpty()) {
            return emptyList()
        }

        val delegatorBlockReward =
            delegatorBlockReward(
                block,
                validatorId,
                validatorCycleCache[validatorId]!!.currentCycle,
            )

        val result =
            updateRewardInfo(
                currentTokenRewards = latestRewards,
                totalBlockReward = delegatorBlockReward,
                validator = validatorId,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                blockId = block.id,
            )

        val allPeriodTrackers = result.filter { it.rewardPeriod == RewardPeriod.ALL }
        if (allPeriodTrackers.isNotEmpty()) {
            rewardTrackerCache[validatorId] = allPeriodTrackers
        }

        return result
    }

    /** @notice Persist a batch of reward records to Postgres. */
    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(rewards: List<TokenReward>) {
        if (rewards.isEmpty()) return
        repository.save(rewards)
    }

    /** @notice Clear all in-memory caches. Called on rollback to ensure consistency. */
    open fun invalidateCache() {
        rewardTrackerCache.clear()
        validatorCycleCache.clear()
        poolCache.clear()
    }

    /** The chain's delegator-pool growth for [validatorId] in [block], read at keys [cycle]±1. */
    suspend fun delegatorBlockReward(block: Block, validatorId: String, cycle: Long): BigInteger {
        val keys = listOf(cycle - 1, cycle, cycle + 1).filter { it > 0 }
        val current = readPools(block.id, validatorId, keys)
        val previous =
            poolCache[validatorId]?.takeIf { it.keys.containsAll(keys) }
                ?: readPools(block.parentID, validatorId, keys)
        poolCache[validatorId] = current
        val delta =
            keys.fold(BigInteger.ZERO) { acc, key ->
                acc.add(current.getValue(key).subtract(previous.getValue(key)))
            }
        check(delta.signum() >= 0) {
            "Delegator pool of $validatorId shrank at block ${block.number} (${block.id}): " +
                "$previous -> $current"
        }
        return delta
    }

    private suspend fun readPools(
        blockId: String,
        validatorId: String,
        keys: List<Long>,
    ): Map<Long, BigInteger> {
        val clauses = keys.map { key ->
            ContractUtils.createClause(
                stakerAddress,
                delegatorsRewardsAbi,
                AddressUtils.toBigInt(validatorId),
                key,
            )
        }
        val responses = thorClient.inspectClauses(clauses, BlockRevision.Id(blockId))
        check(responses.size == keys.size) {
            "$DELEGATORS_REWARDS_FN returned ${responses.size} of ${keys.size} responses at $blockId"
        }
        return keys.zip(responses).associate { (key, response) ->
            check(!response.reverted && response.vmError.isNullOrBlank()) {
                "$DELEGATORS_REWARDS_FN($validatorId, $key) failed at $blockId: $response"
            }
            val decoded = FunctionReturnDecoder.decode(response.data, delegatorsRewardsAbi.outputs)
            key to decoded["rewards"] as BigInteger
        }
    }

    /**
     * Get current validator reward trackers, populating new ones on cycle transitions.
     *
     * @param block Current Thor block.
     * @param validator Up-to-date [Validator] state for [block.signer].
     */
    fun getLatestRewards(block: Block, validator: Validator): List<TokenReward> {
        val validatorId = validator.id

        var cached = validatorCycleCache[validatorId]
        var newCycle = false

        if (cached == null || block.number >= cached.nextCycleBlock) {
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
     * Reads currently-active delegations from [DelegationReadRepository] (was V1
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

        val newDocs = missingDelegations.map { delegation ->
            val rewardId = "$validatorId-${delegation.tokenId}"
            val stake = delegation.tokenLevel.effectiveStake.multiply(weiMultiplier).toBigInteger()

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
            )
        }

        return rewardsFromDb + newDocs
    }

    /**
     * Update cached cycle info for [validator]. Reads cycle parameters straight off the V2 row — no
     * chain decode needed.
     */
    fun updateValidatorCycleCache(validator: Validator) {
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
    ): List<TokenReward> {
        val cycleCache = validatorCycleCache[validator]!!
        val updatedRewards = mutableListOf<TokenReward>()

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
                )

            updatedRewards.add(updatedTracker)
        }

        return updatedRewards
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
        )

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

        private const val STAKER_ABI_PATH = "abis/stargate"
        private const val DELEGATORS_REWARDS_FN = "getDelegatorsRewards"

        /** The Energy builtin's totalSupply() clause, used by the validator-block indexer. */
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
