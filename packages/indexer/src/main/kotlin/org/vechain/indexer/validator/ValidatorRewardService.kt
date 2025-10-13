package org.vechain.indexer.validator

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.ExecutableMapReduceOperation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.ValidatorUtils.hasAbiData

@Profile("validator", "validator-reward")
@Service
open class ValidatorRewardService(
    private val repository: ValidatorRewardRepository,
    private val thorService: ThorService,
    private val executableMapReduceOperation: ExecutableMapReduceOperation,
) {
    /** Cached VTHO total supply from the previous block to calculate deltas. */
    private var vthoTotalSupply: BigInteger = BigInteger.ZERO

    open fun processBlock(block: Block, callResponses: List<InspectionResult>): ValidatorReward? {
        // Need at least totalSupply and burned responses
        if (callResponses.isEmpty() || !callResponses[0].hasAbiData()) return null

        // Get total VTHO issued at this block
        val blockTotalSupply = getTotalVTHOIssued(callResponses)

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

        return ValidatorReward(
            blockNumber = block.number,
            blockId = block.id,
            blockTimestamp = block.timestamp,
            validator = block.signer,
            blockReward = blockReward,
            priorityReward = priorityRewards,
            total = blockReward.add(priorityRewards),
        )
    }

    @Transactional
    open fun save(record: ValidatorReward) {
        repository.save(record)
    }

    /** Resolve total VTHO issued = totalSupply + burned */
    fun getTotalVTHOIssued(responses: List<InspectionResult>): BigInteger {
        if (responses.size < 2 || !responses[0].hasAbiData() || !responses[1].hasAbiData()) {
            return BigInteger.ZERO
        }

        val decodedTotalSupply =
            FunctionReturnDecoder.decode(
                responses[0].data,
                listOf(InputOutput("uint256", "vthoTotalSupply", "uint256")),
            )
        val decodedBurned =
            FunctionReturnDecoder.decode(
                responses[1].data,
                listOf(InputOutput("uint256", "vthoBurned", "uint256")),
            )

        val totalSupply = decodedTotalSupply["vthoTotalSupply"] as? BigInteger ?: BigInteger.ZERO
        val burned = decodedBurned["vthoBurned"] as? BigInteger ?: BigInteger.ZERO

        return totalSupply.add(burned)
    }

    fun getTotalVTHOIssuedAtBlock(blockId: String): BigInteger {
        val response =
            thorService.inspectClausesAtBlock(ValidatorUtils.buildVTHOTotalsClauses(), blockId)

        if (response.size < 2) {
            return BigInteger.ZERO
        }

        val inspectionResults =
            listOf(
                InspectionResult(response[0].data, emptyList(), emptyList(), false, null),
                InspectionResult(response[1].data, emptyList(), emptyList(), false, null),
            )

        return getTotalVTHOIssued(inspectionResults)
    }

    /** Convert hex string (with optional "0x" prefix) into BigInteger. */
    fun String.hexToBigInteger(): BigInteger = BigInteger(this.removePrefix("0x"), 16)
}
