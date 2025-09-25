package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.BigInteger
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.rest.ExecuteCodeResponse
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.utils.NumberUtils.toSafeDecimal128
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator

object ValidatorInfoDecoder {
    fun getLatestValidatorInfo(
        responses: List<ExecuteCodeResponse>,
        validatorsAbi: MutableMap<String, AbiElement>,
        existingDocs: Map<String, Validator>,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
    ): ValidatorCycleContext {
        val decodedInfo = decodeResponseInfo(responses, validatorsAbi)

        val (validators, remove) =
            unpackValidators(
                decodedInfo.decodedValidators,
                existingDocs,
                decodedInfo.totalWeight,
                decodedInfo.vthoTotalSupply,
                decodedInfo.vetPriceUsd,
                decodedInfo.vthoPriceUsd,
                blockId,
                blockNumber,
                blockTimestamp,
            )

        val validatorMap = validators.associateBy { it.id }.toMutableMap()
        remove.forEach { validatorMap.remove(it) }

        return ValidatorCycleContext(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            _validators = validatorMap,
        )
    }

    fun unpackValidators(
        decoded: Map<String, Any?>,
        existingDocs: Map<String, Validator>,
        totalWeight: BigInteger,
        totalVTHOSupply: BigInteger,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
    ): Pair<List<Validator>, List<String>> {
        val rows =
            (decoded["masters"] as List<String>).indices.map { i ->
                DecodedValidatorRow(
                    id = (decoded["masters"] as List<String>)[i],
                    endorser = (decoded["endorsors"] as List<String>)[i],
                    status = (decoded["statuses"] as List<BigInteger>)[i],
                    online = (decoded["onlines"] as List<Boolean>)[i],
                    offlineBlock = (decoded["offlineBlocks"] as List<BigInteger>)[i],
                    stakingPeriodLength = (decoded["stakingPeriodLengths"] as List<Int>)[i],
                    startBlock = (decoded["startBlocks"] as List<BigInteger>)[i],
                    exitBlock = (decoded["exitBlocks"] as List<BigInteger>)[i],
                    completedPeriods = (decoded["completedPeriods"] as List<BigInteger>)[i],
                    validatorLockedVET = (decoded["validatorLockedStakes"] as List<BigInteger>)[i],
                    validatorLockedWeight =
                        (decoded["validatorLockedWeights"] as List<BigInteger>)[i],
                    delegatorsStake = (decoded["delegatorsStake"] as List<BigInteger>)[i],
                )
            }

        val validators =
            rows.mapNotNull { row ->
                buildValidator(
                    row,
                    existingDocs[row.id],
                    totalWeight,
                    totalVTHOSupply,
                    vetPriceUsd,
                    vthoPriceUsd,
                    blockId,
                    blockNumber,
                    blockTimestamp,
                )
            }

        val toDelete = existingDocs.keys.minus(rows.map { it.id }.toSet()).toList()
        return validators to toDelete
    }

    fun buildValidator(
        row: DecodedValidatorRow,
        existingDoc: Validator?,
        totalWeight: BigInteger,
        totalVTHOSupply: BigInteger,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        rententionPeriod: Long = 777600, // 3 Months
    ): Validator? {
        val exitBlock = row.exitBlock.toLong()
        if ((exitBlock + rententionPeriod) < blockNumber) return null

        val vetPrice = ValidatorCalculations.toUsdPrice(vetPriceUsd)
        val vthoPrice = ValidatorCalculations.toUsdPrice(vthoPriceUsd)

        val validatorVET = NumberUtils.toVET(row.validatorLockedVET)
        val delegatorVET = NumberUtils.toVET(row.delegatorsStake)
        val totalVET = validatorVET + delegatorVET

        val validatorTvl = validatorVET * vetPrice
        val delegatorTvl = delegatorVET * vetPrice
        val totalTvl = validatorTvl + delegatorTvl

        val vthoSupply = NumberUtils.toVET(totalVTHOSupply)

        val blocksOffline =
            ValidatorCalculations.calculateOfflineBlocks(
                existingDoc?.offlineBlocks,
                row.online,
                row.offlineBlock.toLong(),
                blockNumber,
            )

        val percentageOffline =
            ValidatorCalculations.calculatePercentageOffline(
                blocksOffline,
                row.startBlock.toLong(),
                blockNumber,
            )

        val prevTotalVTHOSupply = existingDoc?.totalVTHOSupply?.bigDecimalValue() ?: vthoSupply
        val blockProbability = NumberUtils.toProbabilityOf(row.validatorLockedWeight, totalWeight)
        val blocksPerYear = ValidatorCalculations.blocksPerYear(blockProbability)

        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            ValidatorCalculations.calculateValidatorYield(
                validatorTvl,
                delegatorTvl,
                row.delegatorsStake > BigInteger.ZERO,
                blocksPerYear,
                vthoSupply,
                prevTotalVTHOSupply,
                vthoPrice,
            )

        val status = ValidatorCalculations.resolveStatus(row.exitBlock, row.status)

        return Validator(
            id = row.id,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            endorser = row.endorser,
            status = Status.fromCode(status),
            online = row.online,
            offlineBlocks = blocksOffline,
            cyclePeriodLength = row.stakingPeriodLength.toLong(),
            startBlock = row.startBlock.toLong(),
            completedPeriods = row.completedPeriods.toLong(),
            vetStaked = toSafeDecimal128(totalVET),
            validatorVetStaked = toSafeDecimal128(validatorVET),
            delegatorVetStaked = toSafeDecimal128(delegatorVET),
            totalWeight = toSafeDecimal128(NumberUtils.toVET(row.validatorLockedWeight)),
            blockProbability = toSafeDecimal128(blockProbability),
            blocksPerEpoch = toSafeDecimal128(blockProbability * BigDecimal(180)),
            blocksPerYear = toSafeDecimal128(blocksPerYear),
            validatorTvl = toSafeDecimal128(validatorTvl),
            delegatorTvl = toSafeDecimal128(delegatorTvl),
            totalTvl = toSafeDecimal128(totalTvl),
            delegations = existingDoc?.delegations ?: emptyMap(),
            delegationInfo = existingDoc?.delegationInfo ?: emptyMap(),
            delegationIdList = existingDoc?.delegationIdList ?: emptyList(),
            validatorYield = toSafeDecimal128(validatorYield),
            tvlBasedYield = toSafeDecimal128(tvlBasedYield),
            avgDelegatorYield = toSafeDecimal128(avgDelegatorYield),
            totalVTHOSupply = toSafeDecimal128(vthoSupply),
            percentageOffline = toSafeDecimal128(percentageOffline),
            version = (existingDoc?.version ?: 0) + 1,
            cycleEndBlock =
                ValidatorCalculations.calculateNextCycleBlock(
                    row.startBlock,
                    row.completedPeriods,
                    row.stakingPeriodLength,
                ),
        )
    }

    fun decodeResponseInfo(
        responses: List<ExecuteCodeResponse>,
        validatorsAbi: Map<String, AbiElement>,
    ): DecodedValidatorInfo {
        val decodedValidators =
            FunctionReturnDecoder.decode(
                responses[0].data,
                validatorsAbi["getValidators"]!!.outputs,
            )

        val decodedTotalStake =
            FunctionReturnDecoder.decode(responses[1].data, validatorsAbi["totalStake"]!!.outputs)
        val totalWeight = decodedTotalStake["totalWeight"] as BigInteger

        val decodedTotalSupply =
            FunctionReturnDecoder.decode(
                responses[2].data,
                validatorsAbi["vthoTotalSupply"]!!.outputs,
            )
        val vthoTotalSupply = decodedTotalSupply["vthoTotalSupply"] as BigInteger

        val decodedVetPriceUsd =
            FunctionReturnDecoder.decode(
                responses[3].data,
                validatorsAbi["getVetPriceUsd"]!!.outputs,
            )
        val vetPriceUsd = decodedVetPriceUsd["vetPriceUsd"] as BigInteger

        val decodedVthoPriceUsd =
            FunctionReturnDecoder.decode(
                responses[4].data,
                validatorsAbi["getVthoPriceUsd"]!!.outputs,
            )
        val vthoPriceUsd = decodedVthoPriceUsd["vthoPriceUsd"] as BigInteger

        return DecodedValidatorInfo(
            decodedValidators,
            totalWeight,
            vthoTotalSupply,
            vetPriceUsd,
            vthoPriceUsd,
        )
    }

    data class DecodedValidatorInfo(
        val decodedValidators: Map<String, Any?>,
        val totalWeight: BigInteger,
        val vthoTotalSupply: BigInteger,
        val vetPriceUsd: BigInteger,
        val vthoPriceUsd: BigInteger,
    )

    data class DecodedValidatorRow(
        val id: String,
        val endorser: String,
        val status: BigInteger,
        val online: Boolean,
        val offlineBlock: BigInteger,
        val stakingPeriodLength: Int,
        val startBlock: BigInteger,
        val exitBlock: BigInteger,
        val completedPeriods: BigInteger,
        val validatorLockedVET: BigInteger,
        val validatorLockedWeight: BigInteger,
        val delegatorsStake: BigInteger,
    )
}
