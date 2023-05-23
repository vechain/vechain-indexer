package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.Contract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.AddressUtil
import org.vechain.indexer.utils.ContractUtils
import kotlin.jvm.optionals.getOrNull

@Profile("contract-indexer")
@Component
open class ContractIndexer(
    private val thorService: ThorService,
    private val contractService: ContractService,
    private val contractRepo: ContractRepo,
    mongoTemplate: MongoTemplate,
) : Indexer(thorService, contractRepo, mongoTemplate) {

    @OptIn(ExperimentalStdlibApi::class)
    override fun processBlock(block: Block) {

        val contracts: MutableList<Contract> = mutableListOf()

        /**
         * Find all events that are contract deployments, paired with their transaction.
         */
        val masterChangeEvents = block.transactions
            .filter { tx -> !tx.reverted }
            .flatMap { tx ->
                tx.outputs.flatMapIndexed { idx, output ->
                    output.events
                        .filter { event ->
                            ContractUtils.isMasterEvent(event)
                        }.map { event ->
                            Triple(event, tx, tx.clauses[idx])
                        }
                }
            }

        /**
         * Process each master change event.
         */
        masterChangeEvents.forEach { (event, tx, clause) ->

            val contractAddress = event.address
            val master = AddressUtil.decode(event.data)
            val rawData = thorService.getAccountCode(contractAddress)

            val contract = contractRepo.findById(contractAddress).getOrNull()

            // If the contract is already indexed, update the master
            if (contract != null) {
                contract.previousMasters.add(contract.master)
                contract.master = master
                contractRepo.save(contract)
            } else {
                contracts.add(
                    Contract(
                        address = contractAddress,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        txId = tx.id,
                        creator = tx.origin,
                        master = master,
                        rawData = rawData,
                        isVip180 = contractService.isVip180(contractAddress, rawData, clause),
                        isVip181 = contractService.isVip181(contractAddress, rawData, clause),
                        isVip210 = contractService.isVip210(contractAddress, rawData, clause),
                        isErc20 = contractService.isErc20(contractAddress, rawData, clause),
                        isErc721 = contractService.isErc721(contractAddress, rawData, clause),
                        isErc1155 = contractService.isErc1155(contractAddress, rawData, clause),
                        previousMasters = mutableSetOf(),
                    )
                )
            }
        }

        if (contracts.isNotEmpty()) insertAll(contracts, Contract::class.java)
    }

}