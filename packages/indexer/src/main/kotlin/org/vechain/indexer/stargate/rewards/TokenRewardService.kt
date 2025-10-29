package org.vechain.indexer.stargate.rewards

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.isNotEmpty
import kotlin.collections.set
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.rest.ExecuteCodeResponse
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardArchive
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.domain.ValidatorDecoder.buildVTHOTotalsClauses
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeResponseInfo
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeVTHOIssued
import org.vechain.indexer.validator.logic.ValidatorAssembler.listOf
import org.vechain.indexer.validator.models.DecodedValidatorInfo

@Profile("stargate", "token-reward")
@Service
open class TokenRewardService(
    private val repository: TokenRewardRepository,
    private val archiveService: ArchiveService<TokenReward, TokenRewardArchive>,
    private val delegationRepository: DelegationRepository,
    private val thorService: ThorService,
    @Value("\${business-event.substitutions.STARGATE_CONTRACT}")
    private val stargateContract: String,
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
    open fun processBlock(
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
    open fun save(rewards: List<TokenReward>, archive: List<TokenReward>) {
        if (rewards.isEmpty()) return
        repository.saveAll(rewards)

        if (archive.isNotEmpty()) {
            archiveService.saveAll(archive)
        }
    }

    /**
     * @param block Current Thor block.
     * @param decodedInfo Optional decoded validator state info (saves RPC calls if present).
     * @return Total delegators' reward as BigInteger, or null if unavailable.
     * @notice Compute total delegators' reward share for a block.
     * @dev Calculates block reward as the delta in VTHO supply, then applies a 70% weighting to
     *   delegators.
     */
    fun getDelegatorsBlockReward(block: Block, decodedInfo: DecodedValidatorInfo?): BigInteger? {
        // Get total VTHO issued at this block
        val blockTotalSupply = getTotalVTHOIssued(decodedInfo, block.id)

        // Initialize cache on restart using the previous block’s reward
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
            cached = validatorCycleCache[validatorId]!!
        }

        // If delegations is false return empty list
        if (!cached.hasDelegations) {
            return emptyList() // Validator has no delegations in this cycle
        }

        // If new cycle, ensure we have up-to-date delegations
        if (newCycle) {
            return getOrFetchRewardsNewCycle(validatorId, block, getTimeInfo(block.timestamp))
        }

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
    fun getTotalVTHOIssued(decodedInfo: DecodedValidatorInfo?, blockId: String): BigInteger {
        if (decodedInfo == null) {
            return getTotalVTHOIssuedAtBlock(blockId)
        }
        return decodedInfo.vthoTotalSupply.add(decodedInfo.vthoBurned)
    }

    /**
     * @param blockId Block ID.
     * @return Total VTHO issued.
     * @notice Get total VTHO issued at a specific block via Thor calls.
     * @dev Calls vthoTotalSupply + totalBurned contract functions and decodes result.
     */
    fun getTotalVTHOIssuedAtBlock(blockId: String): BigInteger {
        val response = thorService.inspectClausesAtBlock(buildVTHOTotalsClauses(), blockId)

        if (response.size < 2) {
            return BigInteger.ZERO
        }

        val inspectionResults =
            listOf(
                InspectionResult(response[0].data, emptyList(), emptyList(), false, null),
                InspectionResult(response[1].data, emptyList(), emptyList(), false, null),
            )

        return decodeVTHOIssued(inspectionResults)
    }

    /**
     * @param data Hex-encoded response string.
     * @return Effective stake as BigInteger.
     * @notice Decode effective stake from Thor call response.
     * @dev Parses ABI return value into a BigInteger. Returns 0 if empty.
     */
    private fun decodeEffectiveStake(data: String): BigInteger {
        if (data.isBlank() || data == "0x") return BigInteger.ZERO
        val decoded =
            FunctionReturnDecoder.decode(
                data,
                listOf(InputOutput("uint256", "effectiveStake", "uint256")),
            )
        return decoded["effectiveStake"] as? BigInteger ?: BigInteger.ZERO
    }

    /**
     * @param validatorId The address of the validator.
     * @param missingIds List of delegation reward document IDs (validatorId-tokenId) not yet
     *   present in DB.
     * @param blockId The block hash or number to perform inspection at.
     * @return Pair of validator total effective stake and a map of tokenId -> effective stake.
     * @notice Fetch effective stake values for a validator and its missing delegations.
     * @dev Builds Thor call clauses for validator total delegations and each missing token ID.
     *   Executes inspection at the given block and decodes the ABI responses into BigIntegers. Also
     *   updates the cycle cache with the validator's total effective delegations.
     */
    private fun getEffectiveStakes(
        validatorId: String,
        missingIds: List<String>,
        blockId: String,
        cycle: Long = 0L,
    ): List<ExecuteCodeResponse> {
        val clauses = buildList {
            // Validator effective stake (cache it for the cycle)
            add(
                ContractUtils.createClause(
                    stargateContract,
                    cachedGetValidatorsAbi["getDelegatorsEffectiveStake"]!!,
                    AddressUtils.toBigInt(validatorId),
                    cycle,
                )
            )

            // Add each missing delegation effective stake clause
            missingIds.forEach { rewardId ->
                val tokenId = rewardId.substringAfter("$validatorId-")
                add(
                    ContractUtils.createClause(
                        stargateContract,
                        cachedGetValidatorsAbi["getEffectiveStake"]!!,
                        tokenId.toBigInteger(),
                    )
                )
            }
        }

        return thorService.inspectClausesAtBlock(clauses, blockId)
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
