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

    private fun getEndorserForNode(nodeMaster: String): String? {
        val clause =
            ContractUtils.createClause(
                address = contractAddress,
                function = CommonABI.get,
                args = arrayOf(nodeMaster),
            )

        return thorService
            .executeReadOnlyCode(listOf(clause))
            .firstOrNull()
            ?.takeIf { TransactionUtils.isSuccessWithData(it) }
            ?.let { response ->
                try {
                    val decoded = FunctionReturnDecoder.decode(response.data, outputTypes)
                    (decoded[0] as Address).value
                } catch (e: Exception) {
                    logger.error("Failed to parse contract response for node: $nodeMaster", e)
                    null
                }
            }
    }

    @Transactional
    open fun syncEndorsersForAllNodes() {
        val nodesToCheck = authorityNodeRepository.findAll().toList()
        if (nodesToCheck.isEmpty()) return

        val updates =
            nodesToCheck.mapNotNull { node ->
                getEndorserForNode(node.nodeMaster)?.let { endorser ->
                    node.copy(endorser = endorser)
                }
            }

        if (updates.isNotEmpty()) {
            authorityNodeRepository.saveAll(updates)
            logger.info("Synced ${updates.size} nodes")
        }
    }

    @Transactional
    open fun processCandidateEvents(events: List<IndexedEvent>, isFullySynced: Boolean) {
        if (events.isEmpty()) return

        for (event in events) {
            val nodeMaster = event.params.getAsString("nodeMaster") ?: continue
            val action = event.params.getAsString("action") ?: continue

            when (action) {
                ACTION_ADDED -> {
                    val node =
                        AuthorityNodeEndorser(
                            nodeMaster = nodeMaster,
                            blockId = event.blockId,
                            blockNumber = event.blockNumber,
                            blockTimestamp = event.blockTimestamp,
                        )

                    val nodeToSave =
                        getEndorserForNode(nodeMaster)?.let { endorser ->
                            node.copy(endorser = endorser)
                        } ?: node

                    authorityNodeRepository.save(nodeToSave)
                }
                ACTION_REVOKED -> {
                    authorityNodeRepository.deleteById(nodeMaster)
                    logger.info("AMN revoked: $nodeMaster")
                }
                else -> logger.warn("Unknown action: $action for node: $nodeMaster")
            }
        }
    }
}
