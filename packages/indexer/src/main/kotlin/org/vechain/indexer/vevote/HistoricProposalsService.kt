package org.vechain.indexer.vevote

import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.contracts.abi.HistoricProposalABI
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("vevote-historic-proposals")
@Service
open class HistoricProposalsService(
    private val thorClient: ThorClient,
    private val repository: HistoricProposalsRepository,
    @param:Value("\${veworld.contract.historic-proposals.steering-committee}")
    private val steeringCommitteeAddress: String,
    @param:Value("\${veworld.contract.historic-proposals.all-stakeholders}")
    private val allStakeholdersAddress: String,
    private val testProposalsProps: TestProposalsProperties,
) {
    private val cachedAbi: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    open suspend fun processEvents(events: List<IndexedEvent>): List<HistoricProposals> {
        if (events.isEmpty()) return emptyList()

        val updates = events.filter { it.eventType == "LegacyVeVoteDescription" }
        val creations = events.filter { it.eventType != "LegacyVeVoteDescription" }

        val updatedProposals = processProposalDescription(updates)
        val newProposals = processNewProposals(creations)
        return updatedProposals + newProposals
    }

    suspend fun processNewProposals(events: List<IndexedEvent>): List<HistoricProposals> {
        if (events.isEmpty()) return emptyList()

        return events.mapNotNull { extractNewProposalEvent(it) }
    }

    fun processProposalDescription(events: List<IndexedEvent>): List<HistoricProposals> {
        if (events.isEmpty()) return emptyList()

        val ids = events.mapNotNull { it.params.getAsString("id") }.distinct()

        val proposals = repository.findAllById(ids).associateBy { it.id }
        val updatedDocs = mutableListOf<HistoricProposals>()

        for (event in events) {
            val proposalId = event.params.getAsString("id") ?: continue
            val existing = proposals[proposalId] ?: continue

            val newDesc = event.params.getAsString("ipfsHash")

            val updated = existing.copy(description = newDesc ?: existing.description)

            updatedDocs.add(updated)
        }

        return updatedDocs
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(events: List<HistoricProposals>) {
        repository.saveAll(events)
    }

    suspend fun extractNewProposalEvent(event: IndexedEvent): HistoricProposals? {
        try {
            val contractAddress = event.address ?: return null
            if (!isValidContractAddress(contractAddress)) {
                return null
            }

            val isSteeringCommittee = contractAddress.equals(steeringCommitteeAddress, true)
            val params = event.params.getReturnValues()
            val proposalId = params["proposalId"]?.toString() ?: return null
            val proposalType = params["ptype"] as? Int

            val contractData = fetchContractData(contractAddress, proposalId, isSteeringCommittee)

            return HistoricProposals(
                id = "$contractAddress-$proposalId",
                proposalId = proposalId,
                contractAddress = contractAddress,
                createdDate = event.blockTimestamp.toString(),
                proposer = contractData.basicInfo?.get("creator") as? String,
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
                description = "",
                test = isTest(contractAddress, proposalId.toIntOrNull() ?: -1),
            )
        } catch (e: Exception) {
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

    private suspend fun fetchContractData(
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
                    if (isSteeringCommittee) {
                        HistoricProposalABI.getBasicInfoSC
                    } else {
                        HistoricProposalABI.getBasicInfo
                    },
                    proposalId.toBigInteger(),
                ),
                ContractUtils.createClause(
                    contractAddress,
                    if (isSteeringCommittee) {
                        HistoricProposalABI.getConditionSC
                    } else {
                        HistoricProposalABI.getCondition
                    },
                    proposalId.toBigInteger(),
                ),
                ContractUtils.createClause(
                    contractAddress,
                    if (isSteeringCommittee) {
                        HistoricProposalABI.getTallySC
                    } else {
                        HistoricProposalABI.getTally
                    },
                    proposalId.toBigInteger(),
                ),
            )

        val responses = thorClient.inspectClauses(clauses)

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
            null
        }
    }

    private fun getAbiFunction(name: String): AbiElement =
        cachedAbi[name]
            ?: run {
                val basePath = "abis/historic-proposals"
                val abis =
                    AbiLoader.loadFunctions(basePath = basePath, functionNames = listOf(name))
                val abi =
                    abis.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException("Function '$name' not found in $basePath")
                cachedAbi[name] = abi
                abi
            }

    private fun extractChoices(
        basicInfo: Map<String, Any?>?,
        contractAddress: String,
    ): List<String>? =
        HistoricUtils.extractChoices(
            basicInfo,
            contractAddress,
            steeringCommitteeAddress,
            allStakeholdersAddress,
        )

    private fun extractVoteTallies(
        tally: Map<String, Any?>?,
        contractAddress: String,
    ): List<Long>? =
        HistoricUtils.extractVoteTallies(
            tally,
            contractAddress,
            steeringCommitteeAddress,
            allStakeholdersAddress,
        )

    private fun calculateTotalVotes(tally: Map<String, Any?>?, contractAddress: String): Long =
        HistoricUtils.calculateTotalVotes(
            tally,
            contractAddress,
            steeringCommitteeAddress,
            allStakeholdersAddress,
        )

    private fun isTest(contractAddress: String, proposalId: Int): Boolean {
        val normalized = contractAddress.lowercase()
        return testProposalsProps.testProposals[normalized]?.contains(proposalId) == true
    }
}
