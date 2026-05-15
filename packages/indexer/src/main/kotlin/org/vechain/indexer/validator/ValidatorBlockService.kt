package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.explorer.TimestampUtils.calculateTimeBoundary
import org.vechain.indexer.explorer.TimestampUtils.isDailyChange
import org.vechain.indexer.explorer.TimestampUtils.isHourlyChange
import org.vechain.indexer.explorer.TimestampUtils.isMonthlyChange
import org.vechain.indexer.explorer.TimestampUtils.isWeeklyChange
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger

/**
 * Per-block validator reward ledger.
 *
 * Reads everything it needs from `Validator` (signer's delegation flag, missed-slot attribution,
 * online recovery) plus a single builtin `Energy.totalSupply()` chain call. No aggregator, no
 * per-validator chain fan-out — the missed-slot detection that V1 did by iterating every validator
 * per block is replaced by a targeted query on V2's already-derived `lastMissedBlockNumber` /
 * `lastProposedBlockNumber` fields.
 *
 * Wire shape on `validator_block_rewards` is unchanged: same VALIDATED / MISSED documents, same
 * fields, same id scheme (`"$blockNumber-$validatorId"`).
 */
@Profile("validator & validator-reward")
@Service
open class ValidatorBlockService(
    private val repository: ValidatorBlockRepository,
    private val validatorRepository: ValidatorRepository,
    private val thorClient: ThorClient,
    @param:Value("\${indexer.start-block.validator}") private val validatorStartBlock: Long,
) {
    private val hourlyCache = ConcurrentHashMap<String, Long>()
    private val dailyCache = ConcurrentHashMap<String, Long>()
    private val weeklyCache = ConcurrentHashMap<String, Long>()
    private val monthlyCache = ConcurrentHashMap<String, Long>()

    /**
     * Validators currently in an open offline period: id → block number at which their open MISSED
     * record was written. Used to (a) suppress duplicate MISSED records for continuing offline runs
     * and (b) close out the prior MISSED record when the validator next produces.
     */
    private val offlineValidators = ConcurrentHashMap<String, Long>()

    /** Cached VTHO total supply from the previous block to calculate deltas. */
    @Volatile private var vthoTotalSupply: BigInteger = BigInteger.ZERO

    init {
        preloadLatestAggregates()
        preLoadOfflineValidators()
    }

    open suspend fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<ValidatorBlock> {
        if (block.number < validatorStartBlock) return emptyList()

        val blockTotalSupply =
            TokenRewardService.decodeTotalSupply(callResponses) ?: return emptyList()

        val validationInfo = getValidationInfo(block, blockTotalSupply)
        val missedSlots = getValidatorsWithMissedSlots(block)
        val recovered = getRecoveredValidators(block)

        return listOfNotNull(validationInfo) + missedSlots + recovered
    }

    @Transactional
    open fun save(records: List<ValidatorBlock>) {
        repository.saveAll(records)
        records.forEach {
            if (it.isHourly == true) hourlyCache[it.validator] = it.blockTimestamp
            if (it.isDaily == true) dailyCache[it.validator] = it.blockTimestamp
            if (it.isWeekly == true) weeklyCache[it.validator] = it.blockTimestamp
            if (it.isMonthly == true) monthlyCache[it.validator] = it.blockTimestamp
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Reward attribution for block.signer
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds the VALIDATED record for [block.signer]. Returns `null` when the signer isn't a
     * tracked validator yet (e.g. cold start before `Validator` has caught up).
     */
    suspend fun getValidationInfo(block: Block, blockTotalSupply: BigInteger): ValidatorBlock? {
        val signer = block.signer
        val validator = validatorRepository.findByIdOrNull(signer) ?: return null
        val hasDelegations = (validator.delegatorVetStaked ?: BigDecimal.ZERO) > BigDecimal.ZERO

        // Cold-start: seed prev-supply from the parent block.
        if (vthoTotalSupply == BigInteger.ZERO) {
            vthoTotalSupply = getTotalVTHOIssuedAtBlock(block.parentID)
        }

        val blockReward = blockTotalSupply.subtract(vthoTotalSupply)
        vthoTotalSupply = blockTotalSupply

        val priorityRewards: BigInteger =
            block.transactions
                .map { it.reward }
                .map { it.hexToBigInteger() }
                .fold(BigInteger.ZERO, BigInteger::add)

        val delegationRewards =
            if (hasDelegations) {
                blockReward.multiply(BigInteger("7")).divide(BigInteger("10"))
            } else {
                BigInteger.ZERO
            }

        return ValidatorBlock(
            id = "${block.number}-$signer",
            blockNumber = block.number,
            blockId = block.id,
            blockTimestamp = block.timestamp,
            validator = signer,
            blockReward = blockReward,
            priorityReward = priorityRewards,
            total = blockReward.add(priorityRewards),
            status = BlockStatus.VALIDATED,
            delegatorRewards = delegationRewards,
            validatorRewards = blockReward.add(priorityRewards).subtract(delegationRewards),
            isHourly =
                calculateTimeBoundary(hourlyCache[signer] ?: 0L, block.timestamp, ::isHourlyChange),
            isDaily =
                calculateTimeBoundary(dailyCache[signer] ?: 0L, block.timestamp, ::isDailyChange),
            isWeekly =
                calculateTimeBoundary(weeklyCache[signer] ?: 0L, block.timestamp, ::isWeeklyChange),
            isMonthly =
                calculateTimeBoundary(
                    monthlyCache[signer] ?: 0L,
                    block.timestamp,
                    ::isMonthlyChange,
                ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Missed-slot detection (V2-driven)
    // ---------------------------------------------------------------------------------------------

    /**
     * Validators whose `lastMissedBlockNumber == block.number` AND aren't already tracked as
     * offline. V2's `ValidatorService.updateLiveness` updates `lastMissedBlockNumber` for every
     * missed slot, so this query returns the set of just-missed validators directly — no full
     * validator-list iteration needed.
     */
    fun getValidatorsWithMissedSlots(block: Block): List<ValidatorBlock> {
        val justMissed = validatorRepository.findByLastMissedBlockNumber(block.number)
        return justMissed.mapNotNull { v ->
            if (offlineValidators.containsKey(v.id)) return@mapNotNull null
            offlineValidators[v.id] = block.number
            ValidatorBlock(
                id = "${block.number}-${v.id}",
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                validator = v.id,
                status = BlockStatus.MISSED,
            )
        }
    }

    /**
     * Currently-offline validators who proposed at [block]. For each, close out their open MISSED
     * record with `blocksOffline` + `onlineBlock`, drop them from the offline set.
     */
    fun getRecoveredValidators(block: Block): List<ValidatorBlock> {
        if (offlineValidators.isEmpty()) return emptyList()
        val recovered =
            validatorRepository
                .findByLastProposedBlockNumberAndIdIn(block.number, offlineValidators.keys)
                .map { it.id }
        if (recovered.isEmpty()) return emptyList()

        return recovered.mapNotNull { validatorId ->
            val offlineStartBlock = offlineValidators.remove(validatorId) ?: return@mapNotNull null
            val offlineDoc =
                repository.findByIdOrNull("$offlineStartBlock-$validatorId")
                    ?: return@mapNotNull null
            offlineDoc.copy(
                blocksOffline = block.number - offlineDoc.blockNumber,
                onlineBlock = block.number,
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // VTHO supply fallback
    // ---------------------------------------------------------------------------------------------

    /** Cold-start fallback: read the parent block's `Energy.totalSupply()` directly. */
    suspend fun getTotalVTHOIssuedAtBlock(blockId: String): BigInteger {
        val response =
            thorClient.inspectClauses(
                listOf(TokenRewardService.energyTotalSupplyClause()),
                BlockRevision.Id(blockId),
            )
        // Fail fast — a silent zero baseline would make the very next block's reward equal the
        // entire VTHO supply, corrupting the cumulative delta forever.
        return TokenRewardService.decodeTotalSupply(response)
            ?: throw IllegalStateException(
                "Energy.totalSupply() decode failed at block $blockId (response=$response)"
            )
    }

    // ---------------------------------------------------------------------------------------------
    // Bootstrap caches
    // ---------------------------------------------------------------------------------------------

    private fun preloadLatestAggregates() {
        repository.findLatestHourly().forEach { hourlyCache[it._id.validator] = it.blockTimestamp }
        repository.findLatestDaily().forEach { dailyCache[it._id.validator] = it.blockTimestamp }
        repository.findLatestWeekly().forEach { weeklyCache[it._id.validator] = it.blockTimestamp }
        repository.findLatestMonthly().forEach {
            monthlyCache[it._id.validator] = it.blockTimestamp
        }
    }

    private fun preLoadOfflineValidators() {
        repository.findLatestMissed().forEach { offlineValidators[it.validator] = it.blockNumber }
    }
}
