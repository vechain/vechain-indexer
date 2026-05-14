@file:Suppress("DEPRECATION") // V1 validator pipeline — superseded by ValidatorV2.

package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.BigInteger
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.stargate.token.TokenLevelDecimalValues
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.logic.ValidatorCalculator.MAX_UINT32
import org.vechain.indexer.validator.logic.ValidatorCalculator.blocksPerYear
import org.vechain.indexer.validator.logic.ValidatorCalculator.calculateDelegatedNftLevelYieldsCurrentCycle
import org.vechain.indexer.validator.logic.ValidatorCalculator.calculateNextCycleBlock
import org.vechain.indexer.validator.logic.ValidatorCalculator.calculateNftLevelYieldsIfDelegatedNextCycle
import org.vechain.indexer.validator.logic.ValidatorCalculator.calculateValidatorYield
import org.vechain.indexer.validator.logic.ValidatorCalculator.computeOffline
import org.vechain.indexer.validator.logic.ValidatorCalculator.computeProbabilities
import org.vechain.indexer.validator.logic.ValidatorCalculator.computeStakes
import org.vechain.indexer.validator.logic.ValidatorCalculator.computeTVL
import org.vechain.indexer.validator.logic.ValidatorCalculator.determineVTHOIssuedPerBlock
import org.vechain.indexer.validator.logic.ValidatorCalculator.resolveStatus
import org.vechain.indexer.validator.models.DecodedValidatorRow

/** Holds queue position info for a QUEUED validator */
data class QueueInfo(val position: Long, val availableStartBlock: Long)

object ValidatorAssembler {
    fun unpackValidators(
        rows: List<DecodedValidatorRow>,
        persistedDocs: Map<String, Validator>,
        carriedDocs: Map<String, Validator> = persistedDocs,
        currentDelegatedLevelsByValidator: Map<String, Map<TokenLevel, Long>> = emptyMap(),
        totalWeight: BigInteger,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
    ): List<Validator> {
        val queueInfo = calculateQueueInfo(rows)
        val ids = rows.map { it.id }

        val nextPeriodTotalWeight =
            rows.map { it.totalNextPeriodWeight }.reduceOrNull(BigInteger::add) ?: BigInteger.ZERO

        val totalVETStaked =
            rows.fold(BigInteger.ZERO) { acc, row ->
                acc + row.validatorLockedVET + row.delegatorsStake
            }
        val totalVETStakedDecimal = NumberUtils.toVET(totalVETStaked)
        val vthoIssued = determineVTHOIssuedPerBlock(totalVETStakedDecimal)

        val totalNextPeriodVET =
            rows.fold(BigInteger.ZERO) { acc, row ->
                acc + row.validatorLockedVET + row.delegatorsStake + row.totalQueuedStake -
                    row.totalExitingStake
            }
        val vthoIssuedNextCycle = determineVTHOIssuedPerBlock(NumberUtils.toVET(totalNextPeriodVET))

        val active =
            rows.mapNotNull { row ->
                val candidate =
                    buildValidator(
                        row,
                        carriedDocs[row.id],
                        totalWeight,
                        vthoIssued,
                        vetPriceUsd,
                        vthoPriceUsd,
                        blockId,
                        blockNumber,
                        blockTimestamp,
                        nextPeriodTotalWeight,
                        totalNextPeriodVET,
                        vthoIssuedNextCycle,
                        currentDelegatedLevelsByValidator[row.id].orEmpty(),
                        queueInfo[row.id],
                    )

                val existing = persistedDocs[row.id]
                candidate.takeUnless { existing != null && candidate.isEquivalentTo(existing) }
            }

        val disappeared =
            persistedDocs.keys.minus(ids.toSet()).mapNotNull { id ->
                val oldVal = persistedDocs[id]
                if (oldVal != null && oldVal.status != Status.EXITED) {
                    oldVal.copy(
                        status = Status.EXITED,
                        blockNumber = blockNumber,
                        blockTimestamp = blockTimestamp,
                        validatorYield = BigDecimal.ZERO,
                        tvlBasedYield = BigDecimal.ZERO,
                        avgDelegatorYield = BigDecimal.ZERO,
                        nftYieldsIfDelegatedNextCycle = TokenLevelDecimalValues.empty(),
                        nftYields = TokenLevelDecimalValues.empty(),
                        nextCycleValidatorYield = BigDecimal.ZERO,
                        nextCycleTvlBasedYield = BigDecimal.ZERO,
                        nextCycleAvgDelegatorYield = BigDecimal.ZERO,
                        totalTvl = BigDecimal.ZERO,
                        delegatorTvl = BigDecimal.ZERO,
                        validatorTvl = BigDecimal.ZERO,
                        validatorQueuedVetStaked = BigDecimal.ZERO,
                        delegatorQueuedVetStaked = BigDecimal.ZERO,
                        validatorExitingVetStaked = BigDecimal.ZERO,
                        delegatorExitingVetStaked = BigDecimal.ZERO,
                        queuedVetStaked = BigDecimal.ZERO,
                        exitingVetStaked = BigDecimal.ZERO,
                        blockProbability = BigDecimal.ZERO,
                        blocksPerYear = BigDecimal.ZERO,
                        version = oldVal.version + 1,
                        queuePosition = null,
                        availableStartBlock = null,
                    )
                } else {
                    null
                }
            }

        return active + disappeared
    }

    /** Create Validator using latest on-chain info and calculations */
    fun buildValidator(
        row: DecodedValidatorRow,
        existingDoc: Validator?,
        totalWeight: BigInteger,
        vthoIssued: BigDecimal,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        nextPeriodTotalWeight: BigInteger,
        totalNextPeriodVET: BigInteger,
        vthoIssuedNextCycle: BigDecimal,
        currentDelegatedLevels: Map<TokenLevel, Long>,
        queueInfo: QueueInfo?,
    ): Validator {
        val vetPrice = NumberUtils.toUSD(vetPriceUsd)
        val vthoPrice = NumberUtils.toUSD(vthoPriceUsd)
        val stakes = computeStakes(row, existingDoc, blockNumber)
        val tvl = computeTVL(stakes, vetPrice)
        val offline = computeOffline(existingDoc, row, blockNumber)
        val probabilities = computeProbabilities(row, totalWeight, nextPeriodTotalWeight)

        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            calculateValidatorYield(
                tvl.validatorTvl,
                tvl.delegatorTvl,
                row.delegatorsStake > BigInteger.ZERO,
                probabilities.blocksPerYear,
                vthoIssued,
                vthoPrice,
            )

        val status = Status.fromCode(resolveStatus(row.exitBlock, row.status))

        val cycleEndBlock =
            calculateNextCycleBlock(row.startBlock, row.completedPeriods, row.stakingPeriodLength)

        val (nextCycleValidatorYield, nextCycleTvlBasedYield, nextCycleAvgDelegatorYield) =
            if (status == Status.EXITING && cycleEndBlock >= row.exitBlock.toLong()) {
                Triple(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
            } else {
                calculateValidatorYield(
                    stakes.nextCycleValidatorStake * vetPrice,
                    stakes.nextCycleDelegationStake * vetPrice,
                    stakes.nextCycleDelegationStake > BigDecimal.ZERO,
                    blocksPerYear(probabilities.blockProbabilityNextCycle),
                    vthoIssuedNextCycle,
                    vthoPrice,
                )
            }

        return Validator(
            id = row.id,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            endorser = row.endorser,
            beneficiary = existingDoc?.beneficiary,
            status = status,
            online = row.online,
            offlineBlocks = offline.blocksOffline,
            cyclePeriodLength = row.stakingPeriodLength.toLong(),
            startBlock = row.startBlock.toLong(),
            completedPeriods = row.completedPeriods.toLong(),
            vetStaked = NumberUtils.toScaledDecimal(stakes.totalVET),
            validatorVetStaked = NumberUtils.toScaledDecimal(stakes.validatorVET),
            delegatorVetStaked = NumberUtils.toScaledDecimal(stakes.delegatorVET),
            queuedVetStaked = NumberUtils.toScaledDecimal(stakes.queuedStake),
            validatorQueuedVetStaked = NumberUtils.toScaledDecimal(stakes.queuedValidatorVetStaked),
            delegatorQueuedVetStaked =
                NumberUtils.toScaledDecimal(stakes.queuedDelegationVetStaked),
            validatorExitingVetStaked =
                NumberUtils.toScaledDecimal(stakes.exitingValidatorVetStaked),
            delegatorExitingVetStaked =
                NumberUtils.toScaledDecimal(stakes.exitingDelegationVetStaked),
            exitingVetStaked = NumberUtils.toScaledDecimal(stakes.exitingStake),
            totalWeight = NumberUtils.toScaledDecimal(NumberUtils.toVET(row.validatorLockedWeight)),
            blockProbability = NumberUtils.toScaledDecimal(probabilities.blockProbability),
            blocksPerEpoch =
                NumberUtils.toScaledDecimal(probabilities.blockProbability * BigDecimal(180)),
            blocksPerYear = NumberUtils.toScaledDecimal(probabilities.blocksPerYear),
            validatorTvl = NumberUtils.toScaledDecimal(tvl.validatorTvl),
            delegatorTvl = NumberUtils.toScaledDecimal(tvl.delegatorTvl),
            totalTvl = NumberUtils.toScaledDecimal(tvl.totalTvl),
            validatorYield = NumberUtils.toScaledDecimal(validatorYield),
            tvlBasedYield = NumberUtils.toScaledDecimal(tvlBasedYield),
            avgDelegatorYield = NumberUtils.toScaledDecimal(avgDelegatorYield),
            nextCycleValidatorYield = NumberUtils.toScaledDecimal(nextCycleValidatorYield),
            nextCycleTvlBasedYield = NumberUtils.toScaledDecimal(nextCycleTvlBasedYield),
            nextCycleAvgDelegatorYield = NumberUtils.toScaledDecimal(nextCycleAvgDelegatorYield),
            nftYieldsIfDelegatedNextCycle =
                TokenLevelDecimalValues.fromMap(
                    calculateNftLevelYieldsIfDelegatedNextCycle(
                        NumberUtils.toVET(row.totalNextPeriodWeight),
                        NumberUtils.toVET(totalNextPeriodVET),
                        NumberUtils.toVET(row.nextPeriodDelegationStake),
                        NumberUtils.toVET(nextPeriodTotalWeight),
                        vthoPrice,
                        vetPrice,
                        status,
                        stakes.nextCycleStake,
                    )
                ),
            nftYields =
                TokenLevelDecimalValues.fromMap(
                    calculateDelegatedNftLevelYieldsCurrentCycle(
                        currentDelegatedLevels = currentDelegatedLevels,
                        blocksPerYear = probabilities.blocksPerYear,
                        vthoIssued = vthoIssued,
                        vthoPriceUsd = vthoPrice,
                        vetPriceUsd = vetPrice,
                    )
                ),
            percentageOffline = NumberUtils.toScaledDecimal(offline.percentageOffline),
            version = (existingDoc?.version ?: 0) + 1,
            cycleEndBlock = cycleEndBlock,
            exitBlock = row.exitBlock.takeIf { it > BigInteger.ZERO && it != MAX_UINT32 }?.toLong(),
            queuePosition = queueInfo?.position,
            availableStartBlock = queueInfo?.availableStartBlock,
        )
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> Map<String, Any?>.listOf(key: String): List<T> =
        this[key] as? List<T>
            ?: throw IllegalArgumentException(
                "Expected List<${T::class.simpleName}> for key '$key'"
            )

    /**
     * Calculate queue positions and available start blocks for QUEUED validators.
     *
     * Logic:
     * - Queue positions are assigned from the order of QUEUED rows in the decoded snapshot
     * - No prior persisted queue ordering is consulted when deriving positions
     * - Available start block is determined by:
     *     - If they have a startBlock > 0, use that
     *     - Otherwise, match to exiting validators by position (1st queued -> 1st exiting's
     *       exitBlock)
     *     - If no matching exiting validator, 0 (unknown)
     */
    fun calculateQueueInfo(rows: List<DecodedValidatorRow>): Map<String, QueueInfo> {
        // Status 1 = QUEUED, Status 4 = EXITING
        val queuedRows = rows.filter { it.status.toInt() == 1 }
        if (queuedRows.isEmpty()) return emptyMap()

        // Get exiting validators sorted by exit block (earliest first)
        // 4294967295 (MAX_UINT32) means exit block is not set
        val exitingBlocks =
            rows
                .filter {
                    it.status.toInt() == 4 &&
                        it.exitBlock > BigInteger.ZERO &&
                        it.exitBlock < MAX_UINT32
                }
                .map { it.exitBlock }
                .sorted()

        // Calculate available start block and assign positions
        return queuedRows
            .mapIndexed { index, row ->
                val availableStart =
                    when {
                        row.startBlock > BigInteger.ZERO -> row.startBlock.toLong()
                        index < exitingBlocks.size -> exitingBlocks[index].toLong()
                        else -> 0L // No matching exiting validator
                    }
                row.id to
                    QueueInfo(position = (index + 1).toLong(), availableStartBlock = availableStart)
            }
            .toMap()
    }
}
