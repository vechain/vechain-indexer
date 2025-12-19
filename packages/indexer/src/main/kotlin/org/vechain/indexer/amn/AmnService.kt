package org.vechain.indexer.amn

import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.plusAssign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.insert
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.transaction.TransactionUtils.isSuccessWithData
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("authority-nodes")
@Service
open class AmnService(
    private val thorClient: ThorClient,
    private val amnRepository: AmnRepository,
    private val mongoTemplate: MongoTemplate,
    @param:Value("\${veworld.contract.authority-node.address}") private val contractAddress: String,
) {
    private val cachedAuthorityAbi: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    private val logger = LoggerFactory.getLogger(AmnService::class.java)

    open suspend fun syncEndorsersForAllNodes() {
        val nodeMasters = mutableListOf<String>()

        val bestBlock = thorClient.getBlockUnexpanded(BlockRevision.Keyword.BEST)

        var current = callFirst(bestBlock.id)
        var counter = 0
        while (current != "0x0000000000000000000000000000000000000000" && counter < 101) {
            nodeMasters.add(current)
            current = callNext(bestBlock.id, current)
            counter++
        }

        logger.info("Discovered ${nodeMasters.size} authority nodes via state walk")

        val getClauses = nodeMasters.map { AmnUtils.createGetClause(contractAddress, it) }
        val responses = thorClient.inspectClauses(getClauses, BlockRevision.Id(bestBlock.id))
        val results =
            responses.zip(nodeMasters).mapNotNull { (response, nodeMaster) ->
                try {
                    if (!isSuccessWithData(response)) {
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
                        blockNumber = bestBlock.number,
                        blockId = bestBlock.id,
                        blockTimestamp = bestBlock.timestamp,
                    )
                } catch (e: Exception) {
                    logger.error("Error decoding get() response for $nodeMaster", e)
                    null
                }
            }

        mongoTemplate.insert(results, AmnEndorser::class.java)
    }

    private suspend fun callFirst(blockId: String): String {
        val firstABI = getAuthorityAbiFunctions("first")
        val result =
            thorClient.inspectClauses(
                listOf(AmnUtils.createFirstClause(contractAddress)),
                BlockRevision.Id(blockId),
            )
        val decoded = FunctionReturnDecoder.decode(result.first().data, firstABI.outputs)
        if (decoded.entries.isEmpty()) {
            logger.warn("No Authority Master Nodes decoded for address: $contractAddress")
            return "0x0000000000000000000000000000000000000000"
        }
        return decoded.entries.firstOrNull()!!.value as String
    }

    private suspend fun callNext(blockId: String, current: String): String {
        val nextABI = getAuthorityAbiFunctions("next")
        val result =
            thorClient.inspectClauses(
                listOf(AmnUtils.createNextClause(contractAddress, current)),
                BlockRevision.Id(blockId),
            )
        val decoded = FunctionReturnDecoder.decode(result.first().data, nextABI.outputs)
        if (decoded.entries.isEmpty()) {
            logger.warn("No Authority Master Nodes decoded for address: $contractAddress")
            return "0x0000000000000000000000000000000000000000"
        }
        return decoded.entries.firstOrNull()!!.value as String
    }

    open suspend fun processCandidateEvents(events: List<IndexedEvent>): List<AmnEndorser> {
        if (events.isEmpty()) return emptyList()

        val toSave = mutableListOf<AmnEndorser>()

        for (event in events) {
            val nodeMaster = event.params.getAsString("nodeMaster") ?: continue
            val action = event.params.getAsString("action")

            when (action) {
                AmnUtils.ACTION_ADDED -> {
                    val existing =
                        withContext(Dispatchers.IO) { amnRepository.findById(nodeMaster) }
                            .orElse(null)
                    if (existing != null) {
                        logger.info("Authority Node $nodeMaster already exists. Skipping insert.")
                        continue
                    }

                    val clause = AmnUtils.createGetClause(contractAddress, nodeMaster)
                    val response =
                        thorClient
                            .inspectClauses(listOf(clause), BlockRevision.Id(event.blockId))
                            .firstOrNull()

                    if (response == null || !isSuccessWithData(response)) {
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
                    withContext(Dispatchers.IO) { amnRepository.deleteById(nodeMaster) }
                }

                else -> {
                    logger.warn(
                        "Unknown action '${event.params.getAsString("action")}' for node: $nodeMaster"
                    )
                }
            }
        }

        return toSave
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(toSave: List<AmnEndorser>) {
        if (toSave.isNotEmpty()) {
            mongoTemplate.insert<AmnEndorser>(toSave)
        }
    }

    private fun getAuthorityAbiFunctions(name: String): AbiElement =
        cachedAuthorityAbi[name]
            ?: run {
                val abis =
                    AbiLoader.loadFunctions(basePath = "abis/amn", functionNames = listOf(name))

                val abi =
                    abis.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException(
                            "Function '$name' not found in authority-node ABI"
                        )
                cachedAuthorityAbi[name] = abi
                abi
            }
}
