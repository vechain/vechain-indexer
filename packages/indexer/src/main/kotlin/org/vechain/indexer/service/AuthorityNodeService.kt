package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.contracts.abi.CommonABI
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.TransactionUtils
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.utils.Numeric

@Profile("authority-nodes")
@Service
open class AuthorityNodeService(
    private val authorityNodeRepository: AuthorityNodeRepository,
    private val thorService: ThorService,
    @Value("\${veworld.contract.authority_node.address}") private val contractAddress: String,
) {
    private val logger = LoggerFactory.getLogger(AuthorityNodeService::class.java)

    private val outputTypes =
        listOf(
            object : TypeReference<Bool>() {},
            object : TypeReference<Address>() {},
            object : TypeReference<Bytes32>() {},
            object : TypeReference<Bool>() {},
        )
            as List<TypeReference<Type<*>>>

    @Transactional
    open fun syncEndorsersForAllNodes() {
        val nodesToCheck = authorityNodeRepository.findAll().toList()

        if (nodesToCheck.isEmpty()) {
            logger.info("No nodes need endorser sync")
            return
        }

        logger.info("Starting sync for ${nodesToCheck.size} nodes")

        val clauses =
            nodesToCheck.map { node ->
                ContractUtils.createClause(
                    address = contractAddress,
                    function = CommonABI.get,
                    args = arrayOf(node.nodeMaster),
                )
            }

        val responses =
            clauses.chunked(50).flatMap { batch -> thorService.executeReadOnlyCode(batch) }

        val updates =
            nodesToCheck.zip(responses).mapNotNull { (node, result) ->
                if (TransactionUtils.isSuccessWithData(result)) {
                    try {
                        val decoded = FunctionReturnDecoder.decode(result.data, outputTypes)

                        node.copy(
                            listed = (decoded[0] as Bool).value,
                            endorser = (decoded[1] as Address).value,
                            identity = Numeric.toHexString((decoded[2] as Bytes32).value),
                            active = (decoded[3] as Bool).value,
                        )
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to parse contract response for node: ${node.nodeMaster}",
                            e,
                        )
                        null
                    }
                } else {
                    logger.warn("Contract call failed for node: ${node.nodeMaster}")
                    null
                }
            }

        if (updates.isNotEmpty()) {
            authorityNodeRepository.saveAll(updates)
            logger.info("Successfully synced ${updates.size} nodes")
        }
    }
}
