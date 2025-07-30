package org.vechain.indexer.historicalProposal

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.contracts.abi.HistoricalProposalABI
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.model.HistoricalProposals
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.utils.ContractUtils

@Profile("historical-proposals")
@Service
class HistoricalProposalsService(
    private val thorService: ThorService,
    @Value("\${veworld.contract.historical_proposals.steering_committee}")
    private val steeringCommitteeAddress: String,
    @Value("\${veworld.contract.historical_proposals.all_stakeholders}")
    private val allStakeholdersAddress: String,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val cachedAbi: MutableMap<String, AbiElement> = mutableMapOf()

    fun processNewProposals(events: List<IndexedEvent>): List<HistoricalProposals> =
        events.mapNotNull { extractNewProposalEvent(it) }

    fun extractNewProposalEvent(event: IndexedEvent): HistoricalProposals? {

        try {
            val contractAddress = event.address ?: return null
            if (
                !contractAddress.equals(steeringCommitteeAddress, true) &&
                    !contractAddress.equals(allStakeholdersAddress, true)
            ) {
                logger.warn("Event from unknown contract address: $contractAddress")
                return null
            }

            val isSteeringCommittee = contractAddress.equals(steeringCommitteeAddress, true)
            val basicInfoFunction = if (isSteeringCommittee) "getBasicInfoSC" else "getBasicInfo"
            val conditionFunction = if (isSteeringCommittee) "getConditionSC" else "getCondition"
            val tallyFunction = if (isSteeringCommittee) "getTallySC" else "getTally"

            val params = event.params.getReturnValues()
            val proposalId = params["proposalId"]?.toString() ?: return null
            val proposalType = params["ptype"] as? Int // keep as Int?
            val clauses =
                listOf(
                    ContractUtils.createClause(
                        contractAddress,
                        if (isSteeringCommittee) HistoricalProposalABI.getBasicInfoSC
                        else HistoricalProposalABI.getBasicInfo,
                        proposalId.toBigInteger(),
                    ),
                    ContractUtils.createClause(
                        contractAddress,
                        if (isSteeringCommittee) HistoricalProposalABI.getConditionSC
                        else HistoricalProposalABI.getCondition,
                        proposalId.toBigInteger(),
                    ),
                    ContractUtils.createClause(
                        contractAddress,
                        if (isSteeringCommittee) HistoricalProposalABI.getTallySC
                        else HistoricalProposalABI.getTally,
                        proposalId.toBigInteger(),
                    ),
                )

            val responses = thorService.executeReadOnlyCode(clauses)
            val basicInfo = decodeResponse(responses.getOrNull(0)?.data, basicInfoFunction)
            val condition = decodeResponse(responses.getOrNull(1)?.data, conditionFunction)
            val tally = decodeResponse(responses.getOrNull(2)?.data, tallyFunction)

            return HistoricalProposals(
                id = "$contractAddress-$proposalId",
                proposalId = proposalId,
                createdDate = event.blockTimestamp.toString(),
                title = basicInfo?.get("title") as? String,
                proposalType = proposalType,
                choices = extractChoices(basicInfo, contractAddress),
                createTime = (basicInfo?.get("createTime") as? Number)?.toLong(),
                votingStartTime = (condition?.get("votingStartTime") as? Number)?.toLong(),
                votingEndTime = (condition?.get("votingEndTime") as? Number)?.toLong(),
                voteTallies = extractVoteTallies(tally, contractAddress),
                totalVotes = calculateTotalVotes(tally, contractAddress),
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )
        } catch (e: Exception) {
            logger.error("Error processing proposal event: ${e.message}", e)
            return null
        }
    }

    private fun decodeResponse(data: String?, functionName: String): Map<String, Any?>? {
        if (data.isNullOrBlank()) return null

        val abiElement = getAbiFunction(functionName)
        // Decode the hex data
        return FunctionReturnDecoder.decode(data, abiElement.outputs)
    }

    private fun getAbiFunction(name: String): AbiElement {
        return cachedAbi[name]
            ?: run {
                val basePath = "abis/historical-proposals"

                val abis =
                    AbiLoader.loadFunctions(basePath = basePath, functionNames = listOf(name))
                val abi =
                    abis.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException("Function '$name' not found in $basePath")
                cachedAbi[name] = abi
                abi
            }
    }

    private fun extractChoices(
        basicInfo: Map<String, Any?>?,
        contractAddress: String,
    ): List<String>? {
        return when (contractAddress.lowercase()) {
            steeringCommitteeAddress.lowercase() -> {
                val options = basicInfo?.get("options") as? Array<*>
                options?.map { it.toString().trim { c -> c == '\u0000' } }
            }
            allStakeholdersAddress.lowercase() -> {
                val options = basicInfo?.get("options") as? List<*>
                options?.map { it.toString() }
            }
            else -> {
                logger.warn("Unknown contract address: $contractAddress")
                null
            }
        }
    }

    private fun extractVoteTallies(
        tally: Map<String, Any?>?,
        contractAddress: String,
    ): List<Long>? {
        return when (contractAddress.lowercase()) {
            steeringCommitteeAddress.lowercase() -> {
                val tallyArray = tally?.get("tally") as? Array<*>
                tallyArray?.map { (it as? Number)?.toLong() ?: 0L }
            }
            allStakeholdersAddress.lowercase() -> {
                val tallyList = tally?.get("tally") as? List<*>
                tallyList?.map { (it as? Number)?.toLong() ?: 0L }
            }
            else -> {
                logger.warn("Unknown contract address: $contractAddress")
                null
            }
        }
    }

    private fun calculateTotalVotes(tally: Map<String, Any?>?, contractAddress: String): Long {
        val tallies = extractVoteTallies(tally, contractAddress)
        return tallies?.sum() ?: 0L
    }
}
