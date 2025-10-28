package org.vechain.indexer.stargate.rewards

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.stargate.tokenReward.RewardPeriod
import org.vechain.indexer.stargate.tokenReward.TokenReward
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

@Profile("validator", "validator-reward")
@Service
open class TokenRewardService(
    private val repository: TokenRewardRepository,
    private val delegationRepository: DelegationRepository,
    private val thorService: ThorService,
    @Value("\${business-event.substitutions.STARGATE_CONTRACT}")
    private val stargateContract: String,
) {
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()

    private val validatorCycleCache: MutableMap<String, CycleCache> = ConcurrentHashMap()

    /** Cached VTHO total supply from the previous block to calculate deltas. */
    private var vthoTotalSupply: BigInteger = BigInteger.ZERO

    init {
        loadAllValidatorAbiFunctions(
            listOf(
                "getValidators",
                "totalStake",
                "vthoTotalSupply",
                "getVetPriceUsd",
                "getVthoPriceUsd",
                "totalBurned",
            )
        )
    }

    open fun processBlock(block: Block, callResponses: List<InspectionResult>): List<TokenReward> {
        val decodedInfo =
            decodeResponseInfo(callResponses, cachedGetValidatorsAbi) ?: return emptyList()

        // Fetch ABIs for decoding
        val latestRewards = getLatestRewards(block, decodedInfo)

        // Validator has no delegations in this cycle
        if (latestRewards.isEmpty()) {
            return emptyList()
        }

        // Get validator total block reward
        val delegatorBlockReward =
            getDelegatorsBlockReward(block, decodedInfo) ?: return emptyList()

        // Update reward info for each delegation
        val updatedRewards =
            updateRewardInfo(
                currentTokenRewards = latestRewards,
                totalBlockReward = delegatorBlockReward,
                validator = block.signer,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                blockId = block.id,
            )

        return updatedRewards
    }

    @Transactional open fun save(records: List<TokenReward>) {}

    fun getDelegatorsBlockReward(block: Block, decodedInfo: DecodedValidatorInfo?): BigInteger? {
        // Get total VTHO issued at this block
        val blockTotalSupply = getTotalVTHOIssued(decodedInfo, block.id)

        // Initialize cache on restart using the previous block’s reward
        if (vthoTotalSupply == BigInteger.ZERO) {
            vthoTotalSupply = getTotalVTHOIssuedAtBlock(block.parentID)
        }

        val blockReward = blockTotalSupply.subtract(vthoTotalSupply)
        vthoTotalSupply = blockTotalSupply // update cache

        // Sum all transaction rewards in this block
        val priorityRewards: BigInteger =
            block.transactions
                .map { it.reward }
                .map { it.hexToBigInteger() }
                .fold(BigInteger.ZERO, BigInteger::add)

        return (blockReward.add(priorityRewards) * BigInteger.valueOf(7)).divide(BigInteger.TEN)
    }

    fun getLatestRewards(block: Block, decodedInfo: DecodedValidatorInfo): List<TokenReward> {
        val validatorId = block.signer

        val cached = validatorCycleCache[validatorId]
        var newCycle = false

        if (cached == null || block.number > cached.nextCycleBlock) {
            // Need to update cycle info
            updateValidatorCycleCache(validatorId, decodedInfo)
            newCycle = true
        }

        // If delegations is false return empty list
        if (!cached!!.hasDelegations) {
            return emptyList() // Validator has no delegations in this cycle
        }

        // If new cycle, ensure we have up-to-date delegations
        if (newCycle) {
            return getOrFetchRewardsNewCycle(
                validatorId,
                stargateContract,
                block.id,
                getTimeInfo(block.timestamp),
            )
        }

        // Otherwise return saved delegations for current cycle
        return repository.findAllByValidatorAndRewardPeriodAndCycle(
            validatorId,
            RewardPeriod.ALL,
            cached.currentCycle,
        )
    }

    fun getOrFetchRewardsNewCycle(
        validatorId: String,
        contractAddress: String,
        blockId: String,
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

        // 5. If nothing is missing, just return what we have
        if (missingIds.isEmpty()) {
            return rewardsFromDb.toList()
        }

        // 6. Need to call Thor for effective stakes
        val clauses = buildList {
            // Validator effective stake (cache it for the cycle)
            if (!validatorCycleCache.containsKey(validatorId)) {
                add(
                    ContractUtils.createClause(
                        contractAddress,
                        cachedGetValidatorsAbi["getDelegatorsEffectiveStake"]!!,
                        AddressUtils.toBigInt(validatorId),
                    )
                )
            }

            // Add each missing delegation effective stake clause
            missingIds.forEach { rewardId ->
                val tokenId = rewardId.substringAfter("$validatorId-")
                add(
                    ContractUtils.createClause(
                        contractAddress,
                        cachedGetValidatorsAbi["getEffectiveStake"]!!,
                        tokenId,
                    )
                )
            }
        }

        val inspectionResults = thorService.inspectClausesAtBlock(clauses, blockId)

        // 7. Decode results & build new TokenReward docs
        val newDocs = mutableListOf<TokenReward>()
        var resultIndex = 0

        val validatorStake = decodeEffectiveStake(inspectionResults[resultIndex++].data)
        validatorCycleCache[validatorId]!!.totalEffectiveDelegations = validatorStake

        // Each missing delegation effective stake
        missingIds.forEach { rewardId ->
            val tokenId = rewardId.substringAfter("$validatorId-")
            val stake = decodeEffectiveStake(inspectionResults[resultIndex++].data)

            val doc =
                TokenReward(
                    id = rewardId,
                    blockId = blockId,
                    blockNumber = 0L, // fill with real block number
                    blockTimestamp = 0L, // fill with real block timestamp
                    tokenId = tokenId,
                    cycle = validatorCycleCache[validatorId]!!.currentCycle, // from context
                    validator = validatorId,
                    rewards = stake, // raw effective stake or converted reward
                    effectiveStake = stake,
                    allTimeRewards = BigInteger.ZERO, // compute later
                    rewardPeriod = RewardPeriod.ALL,
                    date = time,
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
            newDocs.add(doc)
        }

        // 9. Return union of old + new
        return rewardsFromDb + newDocs
    }

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

        currentTokenRewards.forEach { rewardTracker ->
            val effectiveStake = rewardTracker.effectiveStake

            // Calculate reward share
            val rewardShare =
                if (cycleCache.totalEffectiveDelegations == BigInteger.ZERO) {
                    BigInteger.ZERO
                } else {
                    totalBlockReward
                        .multiply(effectiveStake)
                        .divide(cycleCache.totalEffectiveDelegations)
                }

            var updatedDailyReward = rewardTracker.dayReward ?: BigInteger.ZERO
            var updatedWeeklyReward = rewardTracker.weekReward ?: BigInteger.ZERO
            var updatedMonthlyReward = rewardTracker.monthReward ?: BigInteger.ZERO
            var updatedYearlyReward = rewardTracker.yearReward ?: BigInteger.ZERO
            var updatedCycleReward = rewardTracker.cycleReward ?: BigInteger.ZERO

            val blockDateTime = Instant.ofEpochSecond(blockTimestamp).atZone(ZoneOffset.UTC)
            val blockDate = blockDateTime.toLocalDate()

            val blockDay = blockDate.dayOfMonth.toLong()
            val blockWeek = blockDate.get(WeekFields.ISO.weekOfYear()).toLong()
            val blockMonth = blockDate.monthValue.toLong()
            val blockYear = blockDate.year.toLong()

            // Check if new docs need to be created for different reward periods
            if (
                rewardTracker.dayReward!! > BigInteger.ZERO &&
                    (rewardTracker.dayOfMonth != blockDay ||
                        rewardTracker.month != blockMonth ||
                        rewardTracker.year != blockYear)
            ) {
                // Create new daily reward doc
                val dailyReward =
                    createPeriodReward(
                        id =
                            "${rewardTracker.id}-day-${rewardTracker.year}-${rewardTracker.month}-${rewardTracker.dayOfMonth}",
                        period = RewardPeriod.DAY,
                        rewards = rewardTracker.dayReward!!,
                        mainTracker = rewardTracker,
                    )
                updatedRewards.add(dailyReward)

                // Reset daily reward in main tracker
                updatedDailyReward = rewardShare
            } else {
                updatedDailyReward = updatedDailyReward.add(rewardShare)
            }
            if (rewardTracker.weekOfYear != blockWeek || rewardTracker.year != blockYear) {
                createPeriodReward(
                    id =
                        "${rewardTracker.id}-week-${rewardTracker.year}-${rewardTracker.weekOfYear}",
                    period = RewardPeriod.WEEK,
                    rewards = rewardTracker.monthReward!!,
                    mainTracker = rewardTracker,
                )
                updatedWeeklyReward = rewardShare
            } else {
                updatedWeeklyReward = updatedWeeklyReward.add(rewardShare)
            }
            if (rewardTracker.month != blockMonth || rewardTracker.year != blockYear) {
                createPeriodReward(
                    id = "${rewardTracker.id}-month-${rewardTracker.month}-${rewardTracker.year}",
                    period = RewardPeriod.MONTH,
                    rewards = rewardTracker.monthReward!!,
                    mainTracker = rewardTracker,
                )
                updatedMonthlyReward = rewardShare
            } else {
                updatedMonthlyReward = updatedMonthlyReward.add(rewardShare)
            }
            if (rewardTracker.year != blockYear) {
                createPeriodReward(
                    id = "${rewardTracker.id}-year-${rewardTracker.year}",
                    period = RewardPeriod.YEAR,
                    rewards = rewardTracker.yearReward!!,
                    mainTracker = rewardTracker,
                )
                updatedYearlyReward = rewardShare
            } else {
                updatedYearlyReward = updatedYearlyReward.add(rewardShare)
            }
            if (rewardTracker.cycle != cycleCache.currentCycle) {
                createPeriodReward(
                    id = "${rewardTracker.id}-cycle-${rewardTracker.cycle}",
                    period = RewardPeriod.CYCLE,
                    rewards = rewardTracker.cycleReward!!,
                    mainTracker = rewardTracker,
                )
                updatedCycleReward = rewardShare
            } else {
                updatedCycleReward = updatedCycleReward.add(rewardShare)
            }

            // Update main reward tracker
            val updatedTracker =
                rewardTracker.copy(
                    blockId = blockId,
                    blockNumber = blockNumber,
                    blockTimestamp = blockTimestamp,
                    rewards = rewardTracker.rewards.add(rewardShare),
                    dayReward = updatedDailyReward,
                    weekReward = updatedWeeklyReward,
                    monthReward = updatedMonthlyReward,
                    yearReward = updatedYearlyReward,
                    cycleReward = updatedCycleReward,
                    date = blockDate,
                    dayOfMonth = blockDay,
                    weekOfYear = blockWeek,
                    month = blockMonth,
                    year = blockYear,
                )

            updatedRewards.add(updatedTracker)
        }

        return updatedRewards
    }

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
            date = mainTracker.date,
            dayOfMonth = mainTracker.dayOfMonth,
            weekOfYear = mainTracker.weekOfYear,
            month = mainTracker.month,
            year = mainTracker.year,
            dayReward = mainTracker.dayReward,
        )

    /** Resolve total VTHO issued = totalSupply + burned */
    fun getTotalVTHOIssued(decodedInfo: DecodedValidatorInfo?, blockId: String): BigInteger {
        if (decodedInfo == null) {
            return getTotalVTHOIssuedAtBlock(blockId)
        }
        return decodedInfo.vthoTotalSupply.add(decodedInfo.vthoBurned)
    }

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

    private fun decodeEffectiveStake(data: String): BigInteger {
        if (data.isBlank() || data == "0x") return BigInteger.ZERO
        val decoded =
            FunctionReturnDecoder.decode(
                data,
                listOf(InputOutput("uint256", "effectiveStake", "uint256")),
            )
        return decoded["effectiveStake"] as? BigInteger ?: BigInteger.ZERO
    }

    /** Convert hex string (with optional "0x" prefix) into BigInteger. */
    fun String.hexToBigInteger(): BigInteger = BigInteger(this.removePrefix("0x"), 16)

    private fun loadAllValidatorAbiFunctions(functionNames: List<String>) {
        if (cachedGetValidatorsAbi.isNotEmpty()) return // already loaded

        val abis = AbiLoader.load(basePath = "abis/stargate", names = functionNames)

        abis.forEach { abi -> cachedGetValidatorsAbi[abi.name!!] = abi }
    }

    private fun getTimeInfo(blockTimestamp: Long): LocalDate {
        val blockDateTime = Instant.ofEpochSecond(blockTimestamp).atZone(ZoneOffset.UTC)
        return blockDateTime.toLocalDate()
    }

    data class CycleCache(
        var nextCycleBlock: Long,
        var currentCycle: Long = 0L,
        var hasDelegations: Boolean,
        var totalEffectiveDelegations: BigInteger = BigInteger.ZERO,
    )
}
