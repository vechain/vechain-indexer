package org.vechain.indexer.validator

import java.math.BigInteger
import kotlin.collections.set
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.domain.ValidatorDecoder.buildVTHOTotalsClauses
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeResponseInfo
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeVTHOIssued
import org.vechain.indexer.validator.logic.ValidatorAssembler.listOf
import org.vechain.indexer.validator.models.DecodedValidatorInfo

@Profile("validator", "validator-reward")
@Service
open class ValidatorBlockService(
    private val repository: ValidatorBlockRepository,
    private val thorService: ThorService,
) {
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()

    /** Cached VTHO total supply from the previous block to calculate deltas. */
    private var vthoTotalSupply: BigInteger = BigInteger.ZERO

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
    }

    fun getValidationInfo(block: Block, decodedInfo: DecodedValidatorInfo?): ValidatorBlock? {
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
        val online = decodedValidators.listOf<Boolean>("onlines")
        val offlineBlocks = decodedValidators.listOf<BigInteger>("offlineBlocks")

        return ids.indices.mapNotNull { i ->
            if (!online[i] && offlineBlocks[i].toLong() <= block.number) {
                ValidatorBlock(
                    id = "${block.number}-${ids[i]}",
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    validator = ids[i],
                    status = BlockStatus.MISSED,
                )
            } else {
                null
            }
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

    /** Convert hex string (with optional "0x" prefix) into BigInteger. */
    fun String.hexToBigInteger(): BigInteger = BigInteger(this.removePrefix("0x"), 16)

    private fun loadAllValidatorAbiFunctions(functionNames: List<String>) {
        if (cachedGetValidatorsAbi.isNotEmpty()) return // already loaded

        val abis = AbiLoader.load(basePath = "abis/stargate", names = functionNames)

        abis.forEach { abi -> cachedGetValidatorsAbi[abi.name!!] = abi }
    }
}
