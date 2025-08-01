package org.vechain.indexer.historical

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.contracts.abi.HistoricalProposalABI
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.utils.ContractUtils

@Profile("historical-proposals")
@Service
open class HistoricalProposalsService(
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
            if (!isValidContractAddress(contractAddress)) {
                logger.warn("Event from unknown contract address: $contractAddress")
                return null
            }

            val isSteeringCommittee = contractAddress.equals(steeringCommitteeAddress, true)
            val params = event.params.getReturnValues()
            val proposalId = params["proposalId"]?.toString() ?: return null
            val proposalType = params["ptype"] as? Int

            val contractData = fetchContractData(contractAddress, proposalId, isSteeringCommittee)

            return HistoricalProposals(
                id = "$contractAddress-$proposalId",
                proposalId = proposalId,
                createdDate = event.blockTimestamp.toString(),
                title = contractData.basicInfo?.get("title") as? String,
                proposalType = proposalType,
                choices = extractChoices(contractData.basicInfo, contractAddress),
                createTime = (contractData.basicInfo?.get("createTime") as? Number)?.toLong(),
                votingStartTime =
                    (contractData.condition?.get("votingStartTime") as? Number)?.toLong(),
                votingEndTime = (contractData.condition?.get("votingEndTime") as? Number)?.toLong(),
                voteTallies = extractVoteTallies(contractData.tally, contractAddress),
                totalVotes = calculateTotalVotes(contractData.tally, contractAddress),
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )
        } catch (e: Exception) {
            logger.error("Error processing proposal event: ${e.message}", e)
            return null
        }
    }

    private fun isValidContractAddress(contractAddress: String): Boolean =
        contractAddress.equals(steeringCommitteeAddress, true) ||
            contractAddress.equals(allStakeholdersAddress, true)

    private data class ContractData(
        val basicInfo: Map<String, Any?>?,
        val condition: Map<String, Any?>?,
        val tally: Map<String, Any?>?,
    )

    private fun fetchContractData(
        contractAddress: String,
        proposalId: String,
        isSteeringCommittee: Boolean,
    ): ContractData {
        val basicInfoFunction = if (isSteeringCommittee) "getBasicInfoSC" else "getBasicInfo"
        val conditionFunction = if (isSteeringCommittee) "getConditionSC" else "getCondition"
        val tallyFunction = if (isSteeringCommittee) "getTallySC" else "getTally"

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

        return ContractData(
            basicInfo = decodeResponse(responses.getOrNull(0)?.data, basicInfoFunction),
            condition = decodeResponse(responses.getOrNull(1)?.data, conditionFunction),
            tally = decodeResponse(responses.getOrNull(2)?.data, tallyFunction),
        )
    }

    private fun decodeResponse(data: String?, functionName: String): Map<String, Any?>? {
        if (data.isNullOrBlank() || data == "0x") return null

        return try {
            val abi = getAbiFunction(functionName)
            FunctionReturnDecoder.decode(data, abi.outputs)
        } catch (ex: Exception) {
            logger.error("Failed to decode $functionName response: ${ex.message}")
            null
        }
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
    ): List<String>? =
        HistoricalUtils.extractChoices(
            basicInfo,
            contractAddress,
            steeringCommitteeAddress,
            allStakeholdersAddress,
        )

    private fun extractVoteTallies(
        tally: Map<String, Any?>?,
        contractAddress: String,
    ): List<Long>? =
        HistoricalUtils.extractVoteTallies(
            tally,
            contractAddress,
            steeringCommitteeAddress,
            allStakeholdersAddress,
        )

    private fun calculateTotalVotes(tally: Map<String, Any?>?, contractAddress: String): Long =
        HistoricalUtils.calculateTotalVotes(
            tally,
            contractAddress,
            steeringCommitteeAddress,
            allStakeholdersAddress,
        )
}
