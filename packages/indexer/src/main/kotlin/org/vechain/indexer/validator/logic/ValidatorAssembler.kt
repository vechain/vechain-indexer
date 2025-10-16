package org.vechain.indexer.validator.logic

import java.math.BigInteger
import org.bson.types.Decimal128
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.domain.ValidatorDecoder
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
            ValidatorDecoder.decodeResponseInfo(responses, validatorsAbi) ?: return emptyList()

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
        val exitingStake = decoded.listOf<BigInteger>("totalExitingStakes")
        val totalNextPeriodWeights = decoded.listOf<BigInteger>("totalNextPeriodWeights")

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
                    totalQueuedStake = queuedStake[i],
                    totalExitingStake = exitingStake[i],
                    totalNextPeriodWeight = totalNextPeriodWeights[i],
                )
            }

        val nextPeriodTotalWeight =
            totalNextPeriodWeights.reduceOrNull(BigInteger::add) ?: BigInteger.ZERO

        val active =
            rows.map { row ->
                ValidatorCalculator.buildValidator(
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

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> Map<String, Any?>.listOf(key: String): List<T> =
        this[key] as? List<T>
            ?: throw IllegalArgumentException(
                "Expected List<${T::class.simpleName}> for key '$key'"
            )
}
