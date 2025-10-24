package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.BigInteger
import org.bson.types.Decimal128
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeResponseInfo
import org.vechain.indexer.validator.logic.ValidatorCalculator.blocksPerYear
import org.vechain.indexer.validator.logic.ValidatorCalculator.calculateNextCycleBlock
import org.vechain.indexer.validator.logic.ValidatorCalculator.calculateNftLevelYields
import org.vechain.indexer.validator.logic.ValidatorCalculator.calculateValidatorYield
import org.vechain.indexer.validator.logic.ValidatorCalculator.computeOffline
import org.vechain.indexer.validator.logic.ValidatorCalculator.computeProbabilities
import org.vechain.indexer.validator.logic.ValidatorCalculator.computeStakes
import org.vechain.indexer.validator.logic.ValidatorCalculator.computeTVL
import org.vechain.indexer.validator.logic.ValidatorCalculator.resolveStatus
import org.vechain.indexer.validator.models.DecodedValidatorInfo
import org.vechain.indexer.validator.models.DecodedValidatorRow

object ValidatorAssembler {
    private var totalVTHOIssued: BigInteger = BigInteger.ZERO

    fun getLatestValidatorInfo(
        responses: List<InspectionResult>,
        validatorsAbi: Map<String, AbiElement>,
        existingDocs: Map<String, Validator>,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
    ): List<Validator> {
        val decodedInfo: DecodedValidatorInfo =
            decodeResponseInfo(responses, validatorsAbi) ?: return emptyList()

        val totalVTHOIssuedAtBlock = decodedInfo.vthoTotalSupply.add(decodedInfo.vthoBurned)
        val vthoIssuedBlock = totalVTHOIssuedAtBlock.minus(totalVTHOIssued)
        totalVTHOIssued = totalVTHOIssuedAtBlock

        return unpackValidators(
            decodedInfo.decodedValidators,
            existingDocs,
            decodedInfo.totalWeight,
            vthoIssuedBlock,
            decodedInfo.vetPriceUsd,
            decodedInfo.vthoPriceUsd,
            blockId,
            blockNumber,
            blockTimestamp,
        )
    }

    fun unpackValidators(
        decoded: Map<String, Any?>,
        existingDocs: Map<String, Validator>,
        totalWeight: BigInteger,
        vthoIssuedBlock: BigInteger,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
    ): List<Validator> {
        val ids = decoded.listOf<String>("masters")
        val endorsers = decoded.listOf<String>("endorsors")
        val statuses = decoded.listOf<BigInteger>("statuses")
        val onlines = decoded.listOf<Boolean>("onlines")
        val offlineBlocks = decoded.listOf<BigInteger>("offlineBlocks")
        val stakingPeriodLengths = decoded.listOf<Int>("stakingPeriodLengths")
        val startBlocks = decoded.listOf<BigInteger>("startBlocks")
        val exitBlocks = decoded.listOf<BigInteger>("exitBlocks")
        val completedPeriods = decoded.listOf<BigInteger>("completedPeriods")
        val lockedVET = decoded.listOf<BigInteger>("validatorLockedStakes")
        val lockedWeight = decoded.listOf<BigInteger>("validatorLockedWeights")
        val delegatorsStake = decoded.listOf<BigInteger>("delegatorsStake")
        val queuedStake = decoded.listOf<BigInteger>("totalQueuedStakes")
        val validatorQueuedStakes = decoded.listOf<BigInteger>("validatorQueuedStakes")
        val exitingStake = decoded.listOf<BigInteger>("totalExitingStakes")
        val totalNextPeriodWeights = decoded.listOf<BigInteger>("totalNextPeriodWeights")
        val nextPeriodDelegationStakes = decoded.listOf<BigInteger>("nextPeriodDelegationStakes")

        val rows =
            ids.indices.map { i ->
                DecodedValidatorRow(
                    id = ids[i],
                    endorser = endorsers[i],
                    status = statuses[i],
                    online = onlines[i],
                    offlineBlock = offlineBlocks[i],
                    stakingPeriodLength = stakingPeriodLengths[i],
                    startBlock = startBlocks[i],
                    exitBlock = exitBlocks[i],
                    completedPeriods = completedPeriods[i],
                    validatorLockedVET = lockedVET[i],
                    validatorLockedWeight = lockedWeight[i],
                    delegatorsStake = delegatorsStake[i],
                    validatorQueuedStake = validatorQueuedStakes[i],
                    totalQueuedStake = queuedStake[i],
                    totalExitingStake = exitingStake[i],
                    totalNextPeriodWeight = totalNextPeriodWeights[i],
                    nextPeriodDelegationStake = nextPeriodDelegationStakes[i],
                )
            }

        val nextPeriodTotalWeight =
            totalNextPeriodWeights.reduceOrNull(BigInteger::add) ?: BigInteger.ZERO

        val active =
            rows.mapNotNull { row ->
                val candidate =
                    buildValidator(
                        row,
                        existingDocs[row.id],
                        totalWeight,
                        vthoIssuedBlock,
                        vetPriceUsd,
                        vthoPriceUsd,
                        blockId,
                        blockNumber,
                        blockTimestamp,
                        nextPeriodTotalWeight,
                    )

                val existing = existingDocs[row.id]
                candidate.takeUnless { existing != null && candidate.isEquivalentTo(existing) }
            }

        val disappeared =
            existingDocs.keys.minus(ids.toSet()).mapNotNull { id ->
                val oldVal = existingDocs[id]
                if (oldVal != null && oldVal.status != Status.EXITED) {
                    oldVal.copy(
                        status = Status.EXITED,
                        blockNumber = blockNumber,
                        blockTimestamp = blockTimestamp,
                        validatorYield = Decimal128(0),
                        tvlBasedYield = Decimal128(0),
                        avgDelegatorYield = Decimal128(0),
                        nftYieldsNextCycle = emptyMap(),
                        nextCycleValidatorYield = Decimal128(0),
                        nextCycleTvlBasedYield = Decimal128(0),
                        nextCycleAvgDelegatorYield = Decimal128(0),
                        totalTvl = Decimal128(0),
                        delegatorTvl = Decimal128(0),
                        validatorTvl = Decimal128(0),
                        queuedVetStaked = Decimal128(0),
                        exitingVetStaked = Decimal128(0),
                        blockProbability = Decimal128(0),
                        blocksPerYear = Decimal128(0),
                        version = oldVal.version + 1,
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
        vthoIssued: BigInteger,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        nextPeriodTotalWeight: BigInteger,
    ): Validator {
        val vetPrice = NumberUtils.toVET(vetPriceUsd)
        val vthoPrice = NumberUtils.toVET(vthoPriceUsd)
        val vthoIssuedBD = NumberUtils.toVET(vthoIssued)

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
                vthoIssuedBD,
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
                    vthoIssuedBD,
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
            vetStaked = NumberUtils.toSafeDecimal128(stakes.totalVET),
            validatorVetStaked = NumberUtils.toSafeDecimal128(stakes.validatorVET),
            delegatorVetStaked = NumberUtils.toSafeDecimal128(stakes.delegatorVET),
            queuedVetStaked = NumberUtils.toSafeDecimal128(stakes.queuedStake),
            exitingVetStaked = NumberUtils.toSafeDecimal128(stakes.exitingStake),
            totalWeight =
                NumberUtils.toSafeDecimal128(NumberUtils.toVET(row.validatorLockedWeight)),
            blockProbability = NumberUtils.toSafeDecimal128(probabilities.blockProbability),
            blocksPerEpoch =
                NumberUtils.toSafeDecimal128(probabilities.blockProbability * BigDecimal(180)),
            blocksPerYear = NumberUtils.toSafeDecimal128(probabilities.blocksPerYear),
            validatorTvl = NumberUtils.toSafeDecimal128(tvl.validatorTvl),
            delegatorTvl = NumberUtils.toSafeDecimal128(tvl.delegatorTvl),
            totalTvl = NumberUtils.toSafeDecimal128(tvl.totalTvl),
            validatorYield = NumberUtils.toSafeDecimal128(validatorYield),
            tvlBasedYield = NumberUtils.toSafeDecimal128(tvlBasedYield),
            avgDelegatorYield = NumberUtils.toSafeDecimal128(avgDelegatorYield),
            nextCycleValidatorYield = NumberUtils.toSafeDecimal128(nextCycleValidatorYield),
            nextCycleTvlBasedYield = NumberUtils.toSafeDecimal128(nextCycleTvlBasedYield),
            nextCycleAvgDelegatorYield = NumberUtils.toSafeDecimal128(nextCycleAvgDelegatorYield),
            nftYieldsNextCycle =
                calculateNftLevelYields(
                    stakes.totalVET,
                    NumberUtils.toVET(row.nextPeriodDelegationStake),
                    NumberUtils.toVET(nextPeriodTotalWeight),
                    vthoIssuedBD,
                    vthoPrice,
                    vetPrice,
                    status,
                ),
            percentageOffline = NumberUtils.toSafeDecimal128(offline.percentageOffline),
            version = (existingDoc?.version ?: 0) + 1,
            cycleEndBlock = cycleEndBlock,
        )
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> Map<String, Any?>.listOf(key: String): List<T> =
        this[key] as? List<T>
            ?: throw IllegalArgumentException(
                "Expected List<${T::class.simpleName}> for key '$key'"
            )
}
