package org.vechain.indexer.validator

import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.explorer.TimestampUtils.calculateTimeBoundary
import org.vechain.indexer.explorer.TimestampUtils.isDailyChange
import org.vechain.indexer.explorer.TimestampUtils.isHourlyChange
import org.vechain.indexer.explorer.TimestampUtils.isMonthlyChange
import org.vechain.indexer.explorer.TimestampUtils.isWeeklyChange
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger
import org.vechain.indexer.validator.domain.ValidatorDecoder.buildVTHOTotalsClauses
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeResponseInfo
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeVTHOIssued
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasDelegations
import org.vechain.indexer.validator.logic.ValidatorAssembler.listOf
import org.vechain.indexer.validator.models.DecodedValidatorInfo

@Profile("validator", "validator-reward")
@Service
open class ValidatorBlockService(
    private val repository: ValidatorBlockRepository,
    private val thorService: ThorService,
) {
    private val cachedGetValidatorsAbi: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    private val hourlyCache = ConcurrentHashMap<String, Long>()
    private val dailyCache = ConcurrentHashMap<String, Long>()
    private val weeklyCache = ConcurrentHashMap<String, Long>()
    private val monthlyCache = ConcurrentHashMap<String, Long>()
    private val offlineValidators = ConcurrentHashMap<String, Long>()

    /** Cached VTHO total supply from the previous block to calculate deltas. */
    @Volatile private var vthoTotalSupply: BigInteger = BigInteger.ZERO

    init {
        preloadLatestAggregates()

        preLoadOfflineValidators()
    }

    open fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<ValidatorBlock> {
        // Fetch ABIs for decoding
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

        val decodedInfo = decodeResponseInfo(callResponses, cachedGetValidatorsAbi)

        val validationInfo = getValidationInfo(block, decodedInfo)

        val missedSlots = getValidatorsWithMissedSlots(decodedInfo, block)

        return listOfNotNull(validationInfo) + missedSlots
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

    fun getValidationInfo(block: Block, decodedInfo: DecodedValidatorInfo?): ValidatorBlock? {
        // Get total VTHO issued at this block
        val blockTotalSupply = getTotalVTHOIssued(decodedInfo, block.id)

        // Initialize cache on restart using the previous block’s reward
        if (vthoTotalSupply == BigInteger.ZERO) {
            vthoTotalSupply = getTotalVTHOIssuedAtBlock(block.parentID)
        }

        val hasDelegations = decodedInfo?.hasDelegations(block.signer)

        if (hasDelegations == null || hasDelegations == -1) {
            return null
        }

        val blockReward = blockTotalSupply.subtract(vthoTotalSupply)
        vthoTotalSupply = blockTotalSupply // update cache

        // Sum all transaction rewards in this block
        val priorityRewards: BigInteger =
            block.transactions
                .map { it.reward }
                .map { it.hexToBigInteger() }
                .fold(BigInteger.ZERO, BigInteger::add)

        val delegationRewards =
            if (hasDelegations == 1) {
                blockReward.multiply(BigInteger("7")).divide(BigInteger("10"))
            } else {
                BigInteger.ZERO
            }

        return ValidatorBlock(
            id = "${block.number}-${block.signer}",
            blockNumber = block.number,
            blockId = block.id,
            blockTimestamp = block.timestamp,
            validator = block.signer,
            blockReward = blockReward,
            priorityReward = priorityRewards,
            total = blockReward.add(priorityRewards),
            status = BlockStatus.VALIDATED,
            delegatorRewards = delegationRewards,
            validatorRewards = blockReward.add(priorityRewards).subtract(delegationRewards),
            isHourly =
                calculateTimeBoundary(
                    hourlyCache[block.signer] ?: 0L,
                    block.timestamp,
                    ::isHourlyChange,
                ),
            isDaily =
                calculateTimeBoundary(
                    dailyCache[block.signer] ?: 0L,
                    block.timestamp,
                    ::isDailyChange,
                ),
            isWeekly =
                calculateTimeBoundary(
                    weeklyCache[block.signer] ?: 0L,
                    block.timestamp,
                    ::isWeeklyChange,
                ),
            isMonthly =
                calculateTimeBoundary(
                    monthlyCache[block.signer] ?: 0L,
                    block.timestamp,
                    ::isMonthlyChange,
                ),
        )
    }

    fun getValidatorsWithMissedSlots(
        decodedInfo: DecodedValidatorInfo?,
        block: Block,
    ): List<ValidatorBlock> {
        if (decodedInfo == null) {
            return emptyList()
        }

        val decodedValidators = decodedInfo.decodedValidators

        val ids = decodedValidators.listOf<String>("masters")
        val statuses = decodedValidators.listOf<BigInteger>("statuses")
        val online = decodedValidators.listOf<Boolean>("onlines")
        val offlineBlocks = decodedValidators.listOf<BigInteger>("offlineBlocks")

        return ids.indices.mapNotNull { i ->
            val validatorId = ids[i]
            val status = statuses[i].toInt()
            val wentOfflineAt = offlineBlocks[i].toLong()

            val isMissed = !online[i] && status == 2 && wentOfflineAt == block.number

            if (isMissed) {
                offlineValidators[validatorId] = block.number
                return@mapNotNull ValidatorBlock(
                    id = "${block.number}-$validatorId",
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    validator = validatorId,
                    status = BlockStatus.MISSED,
                )
            }

            if (online[i] && offlineValidators.containsKey(validatorId)) {
                val offlineStartBlock = offlineValidators.remove(validatorId)
                if (offlineStartBlock != null) {
                    val offlineDocId = "$offlineStartBlock-$validatorId"
                    val offlineDoc = repository.findByIdOrNull(offlineDocId)
                    return@mapNotNull offlineDoc?.copy(
                        blocksOffline = block.number - offlineDoc.blockNumber
                    )
                }
            }

            return@mapNotNull null
        }
    }

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

    private fun loadAllValidatorAbiFunctions(functionNames: List<String>) {
        if (cachedGetValidatorsAbi.isNotEmpty()) return // already loaded

        val abis = AbiLoader.load(basePath = "abis/stargate", names = functionNames)

        abis.forEach { abi -> cachedGetValidatorsAbi[abi.name!!] = abi }
    }
}
