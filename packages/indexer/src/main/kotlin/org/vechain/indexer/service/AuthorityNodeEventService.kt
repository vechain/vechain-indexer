package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.contracts.abi.CommonABI
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.AuthorityNodeEndorser
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.utils.TransactionUtils
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Type

@Profile("authority-nodes")
@Service
open class AuthorityNodeEventService(
    private val authorityNodeRepository: AuthorityNodeRepository,
    private val thorService: ThorService,
    @Value("\${veworld.contract.authority_node.address}") private val contractAddress: String,
) {
    private val logger = LoggerFactory.getLogger(AuthorityNodeEventService::class.java)
    private val outputTypes =
        listOf(object : TypeReference<Address>() {}) as List<TypeReference<Type<*>>>

    companion object {
        const val ACTION_ADDED =
            "0x6164646564000000000000000000000000000000000000000000000000000000"
        const val ACTION_REVOKED =
            "0x7265766f6b656400000000000000000000000000000000000000000000000000"
    }

    // takes a list of all node masters
    private fun getEndorsersForNodes(nodeMasters: List<String>): Map<String, String?> {
        if (nodeMasters.isEmpty()) return emptyMap()

        val clauses =
            nodeMasters.map { nodeMaster ->
                ContractUtils.createClause(
                    address = contractAddress,
                    function = CommonABI.get,
                    args = arrayOf(nodeMaster),
                )
            }

        return thorService
            .executeReadOnlyCode(clauses)
            .zip(nodeMasters)
            .mapNotNull { (response, nodeMaster) ->
                try {
                    if (TransactionUtils.isSuccessWithData(response)) {
                        val decoded = FunctionReturnDecoder.decode(response.data, outputTypes)
                        nodeMaster to (decoded[0] as Address).value
                    } else {
                        nodeMaster to null
                    }
                } catch (e: Exception) {
                    logger.error("Failed to parse contract response for node: $nodeMaster", e)
                    nodeMaster to null
                }
            }
            .toMap()
    }

    @Transactional
    open fun syncEndorsersForAllNodes() {
        val nodesToCheck = authorityNodeRepository.findAll().toList()
        if (nodesToCheck.isEmpty()) return

        val endorsersByNode = getEndorsersForNodes(nodesToCheck.map { it.nodeMaster })

        val updates =
            nodesToCheck.mapNotNull { node ->
                endorsersByNode[node.nodeMaster]?.let { endorser -> node.copy(endorser = endorser) }
            }

        if (updates.isNotEmpty()) {
            authorityNodeRepository.saveAll(updates)
            logger.info("Synced ${updates.size} nodes")
        }
    }

    @Transactional
    open fun processCandidateEvents(events: List<IndexedEvent>, fullySynced: Boolean) {
        if (events.isEmpty()) return

        // collect the new nodes to save, and revoke in one pass
        val toSave = mutableListOf<AuthorityNodeEndorser>()

        events.forEach { event ->
            val nodeMaster = event.params.getAsString("nodeMaster") ?: return@forEach
            when (event.params.getAsString("action")) {
                ACTION_ADDED -> {
                    val node =
                        AuthorityNodeEndorser(
                            nodeMaster = nodeMaster,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                        )

                    // when fully synced
                    val nodeToSave =
                        if (fullySynced) {
                            getEndorsersForNodes(listOf(nodeMaster))[nodeMaster]?.let {
                                node.copy(endorser = it)
                            } ?: node
                        } else node

                    toSave += nodeToSave
                }
                ACTION_REVOKED -> {
                    authorityNodeRepository.deleteById(nodeMaster)
                    logger.info("AMN revoked: $nodeMaster")
                }

                else -> {
                    logger.warn(
                        "Unknown action: ${event.params.getAsString("action")} for node: $nodeMaster"
                    )
                }
            }
        }
        //  used to batch save multiple nodes in one database.
        if (toSave.isNotEmpty()) {
            authorityNodeRepository.saveAll(toSave)
            logger.info("Saved ${toSave.size} new AuthorityNodeEndorsers")
        }
    }
}
