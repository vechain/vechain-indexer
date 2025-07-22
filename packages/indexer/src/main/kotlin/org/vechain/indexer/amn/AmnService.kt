package org.vechain.indexer.amn

import kotlin.collections.plusAssign
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.model.AmnEndorser
import org.vechain.indexer.repository.AmnRepository
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.utils.TransactionUtils

@Profile("authority-nodes")
@Service
open class AmnService(
    private val thorService: ThorService,
    private val amnRepository: AmnRepository,
    private val mongoTemplate: MongoTemplate,
    @Value("\${veworld.contract.authority_node.address}") private val contractAddress: String,
) {
    private val cachedAuthorityAbi: MutableMap<String, AbiElement> = mutableMapOf()

    private val logger = LoggerFactory.getLogger(AmnService::class.java)

    open fun syncEndorsersForAllNodes() {
        val nodeMasters = mutableListOf<String>()

        var current = callFirst()
        var counter = 0
        while (current != "0x0000000000000000000000000000000000000000" && counter < 101) {
            nodeMasters.add(current)
            current = callNext(current)
            counter++
        }

        logger.info("Discovered ${nodeMasters.size} authority nodes via state walk")

        val getClauses = nodeMasters.map { AmnUtils.createGetClause(contractAddress, it) }
        val responses = thorService.executeReadOnlyCode(getClauses)

        val bestBlockInfo = thorService.getBestBlock()
        val results =
            responses.zip(nodeMasters).mapNotNull { (response, nodeMaster) ->
                try {
                    if (!TransactionUtils.isSuccessWithData(response)) {
                        logger.warn("get() call for $nodeMaster failed or returned empty")
                        return@mapNotNull null
                    }

                    val output =
                        FunctionReturnDecoder.decode(
                            response.data,
                            getAuthorityAbiFunctions("get").outputs,
                        )

                    val endorser = output["endorsor"] as String
                    if (endorser == "0x0000000000000000000000000000000000000000") {
                        logger.warn("No endorser found for $nodeMaster")
                        return@mapNotNull null
                    }

                    AmnEndorser(
                        nodeMaster = nodeMaster,
                        endorser = endorser,
                        blockNumber = bestBlockInfo.number,
                        blockId = bestBlockInfo.id,
                        blockTimestamp = bestBlockInfo.timestamp,
                    )
                } catch (e: Exception) {
                    logger.error("Error decoding get() response for $nodeMaster", e)
                    null
                }
            }

        mongoTemplate.insert(results, AmnEndorser::class.java)
    }

    private fun callFirst(): String {
        val firstABI = getAuthorityAbiFunctions("first")
        val result =
            thorService.executeReadOnlyCode(listOf(AmnUtils.createFirstClause(contractAddress)))
        val decoded = FunctionReturnDecoder.decode(result.first().data, firstABI.outputs)
        if (decoded.entries.isEmpty()) {
            logger.warn("No Authority Master Nodes decoded for address: $contractAddress")
            return "0x0000000000000000000000000000000000000000"
        }
        return decoded.entries.firstOrNull()!!.value as String
    }

    private fun callNext(current: String): String {
        val nextABI = getAuthorityAbiFunctions("next")
        val result =
            thorService.executeReadOnlyCode(
                listOf(AmnUtils.createNextClause(contractAddress, current))
            )
        val decoded = FunctionReturnDecoder.decode(result.first().data, nextABI.outputs)
        if (decoded.entries.isEmpty()) {
            logger.warn("No Authority Master Nodes decoded for address: $contractAddress")
            return "0x0000000000000000000000000000000000000000"
        }
        return decoded.entries.firstOrNull()!!.value as String
    }

    @Transactional
    open fun processCandidateEvents(events: List<IndexedEvent>) {
        if (events.isEmpty()) return

        val toSave = mutableListOf<AmnEndorser>()

        for (event in events) {
            val nodeMaster = event.params.getAsString("nodeMaster") ?: continue
            val action = event.params.getAsString("action")

            when (action) {
                AmnUtils.ACTION_ADDED -> {
                    val existing = amnRepository.findById(nodeMaster).orElse(null)
                    if (existing != null) {
                        logger.info("Authority Node $nodeMaster already exists. Skipping insert.")
                        continue
                    }

                    val clause = AmnUtils.createGetClause(contractAddress, nodeMaster)
                    val response = thorService.executeReadOnlyCode(listOf(clause)).firstOrNull()

                    if (response == null || !TransactionUtils.isSuccessWithData(response)) {
                        logger.warn("get() call for $nodeMaster failed or returned empty")
                        continue
                    }

                    val output =
                        FunctionReturnDecoder.decode(
                            response.data,
                            getAuthorityAbiFunctions("get").outputs,
                        )
                    val endorser = output["endorsor"] as? String
                    if (
                        endorser == null || endorser == "0x0000000000000000000000000000000000000000"
                    ) {
                        logger.warn("No valid endorser found for $nodeMaster")
                        continue
                    }

                    toSave +=
                        AmnEndorser(
                            nodeMaster = nodeMaster,
                            endorser = endorser,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                        )
                }

                AmnUtils.ACTION_REVOKED -> {
                    amnRepository.deleteById(nodeMaster)
                }

                else -> {
                    logger.warn(
                        "Unknown action '${event.params.getAsString("action")}' for node: $nodeMaster"
                    )
                }
            }
        }

        if (toSave.isNotEmpty()) {
            mongoTemplate.insert(toSave, AmnEndorser::class.java)
        }
    }

    private fun getAuthorityAbiFunctions(name: String): AbiElement =
        cachedAuthorityAbi[name]
            ?: run {
                val abis =
                    AbiLoader.loadFunctions(
                        basePath = "abis/amn",
                        functionNames = listOf("authority"),
                    )

                val abi =
                    abis.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException(
                            "Function '$name' not found in authority-node ABI"
                        )
                cachedAuthorityAbi[name] = abi
                abi
            }
}
