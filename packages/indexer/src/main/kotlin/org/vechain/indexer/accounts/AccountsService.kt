package org.vechain.indexer.accounts

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import kotlin.collections.isNotEmpty
import kotlin.collections.set
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.logic.ValidatorAssembler.listOf
import org.vechain.indexer.validator.models.DecodedValidatorInfo

@Profile("token-reward")
@Service
open class AccountsService(
    private val repository: AccountsRepository,
    private val archiveService: ArchiveService<Accounts, AccountsArchive>,
) {
    open fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<Accounts>, List<Accounts>> {
        // Get validator total block reward
        val delegatorBlockReward =
            getDelegatorsBlockReward(block, decodedInfo) ?: return Pair(emptyList(), emptyList())

        // Update reward info for each delegation and handle period rollovers
        return updateRewardInfo(
            currentTokenRewards = latestRewards,
            totalBlockReward = delegatorBlockReward,
            validator = block.signer,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            blockId = block.id,
        )
    }

    /** @notice Persist a batch of reward records to MongoDB. */
    @Transactional
    open fun save(rewards: List<Accounts>, archive: List<Accounts>) {
        if (rewards.isEmpty()) return
        repository.saveAll(rewards)

        if (archive.isNotEmpty()) {
            archiveService.saveAll(archive)
        }
    }

    /**
     * @param block Current Thor block.
     * @param decodedInfo Decoded validator state from contract call.
     * @return List of TokenReward trackers (existing or newly created).
     * @notice Get current validator reward trackers.
     * @dev Returns ongoing reward docs for a validator in the current cycle. If a new cycle has
     *   started, triggers creation of new docs.
     */
    fun getLatestAccounts(block: Block, decodedInfo: DecodedValidatorInfo): List<TokenReward> {
        val txSigners = block.transactions.map { it.origin }.toSet()
        val gasPayers = block.transactions.map { it.gasPayer }.toSet()

        val accounts = txSigners + gasPayers

        // Otherwise return saved delegations for current cycle
        return repository.findAllByValidatorAndRewardPeriodAndCycle(
            validatorId,
            RewardPeriod.ALL,
            cached.currentCycle,
        )
    }

    /**
     * @param validatorId Validator address (signer).
     * @param contractAddress Stargate contract address.
     * @param blockBlock for inspection.
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
        val tokenIds =
            delegationRepository
                .findByValidatorAndStatusIn(validatorId, listOf(Status.ACTIVE, Status.EXITING))
                .map { it.tokenId }

        if (tokenIds.isEmpty()) return emptyList()

        // 2. Build reward doc IDs
        val rewardIds = tokenIds.map { "$validatorId-$it" }

        // 3. Fetch existing reward docs from DB
        val rewardsFromDb = repository.findAllById(rewardIds)
        val existingIds = rewardsFromDb.map { it.id }.toSet()

        // 4. Find missing delegations
        val missingIds = rewardIds.filterNot { it in existingIds }

        // 5. Need to call Thor for effective stakes
        val inspectionResults =
            getEffectiveStakes(
                validatorId,
                missingIds,
                block.id,
                validatorCycleCache[validatorId]!!.currentCycle,
            )

        // 6. Decode results & build new TokenReward docs
        val newDocs = mutableListOf<TokenReward>()
        var resultIndex = 0

        val validatorStake = decodeEffectiveStake(inspectionResults[resultIndex++].data)
        validatorCycleCache[validatorId]!!.totalEffectiveDelegations = validatorStake

        // 7. If nothing is missing, just return what we have
        if (missingIds.isEmpty()) {
            return rewardsFromDb.toList()
        }

        // Each missing delegation effective stake
        missingIds.forEach { rewardId ->
            val tokenId = rewardId.substringAfter("$validatorId-")
            val stake = decodeEffectiveStake(inspectionResults[resultIndex++].data)

            val doc =
                TokenReward(
                    id = rewardId,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    tokenId = tokenId,
                    cycle = validatorCycleCache[validatorId]!!.currentCycle, // from context
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
            newDocs.add(doc)
        }

        // 9. Return union of old + new
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
     * @param totalBlockReward Total delegators’ reward for this block.
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
    fun createPeriodAccounts(
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
     * @param blockTimestamp Unix epoch seconds from Thor block header.
     * @return LocalDate corresponding to the block timestamp.
     * @notice Convert a Thor block timestamp into LocalDate.
     * @dev Uses UTC zone offset to derive date, day, week, month, and year fields.
     */
    private fun getTimeInfo(blockTimestamp: Long): LocalDate {
        val blockDateTime = Instant.ofEpochSecond(blockTimestamp).atZone(ZoneOffset.UTC)
        return blockDateTime.toLocalDate()
    }
}
