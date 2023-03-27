package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.TxEvent
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.ContractUtils

@Profile("contract-indexer", "prod")
@Component
class ContractIndexer(
    private val thorService: ThorService,
    private val contractRepo: ContractRepo,
    private val contractUtils: ContractUtils
) : Indexer() {
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        val contracts: MutableList<Contract> = mutableListOf()

        /**
         * Find all events that are contract deployments, paired with their transaction.
         */
        val contractEvents = block.transactions
            .filter { tx -> tx.reverted == false }
            .flatMap { tx ->
                tx.outputs.flatMap { output ->
                    output.events
                        .filter { event ->
                            contractUtils.isContractDeployment(event)
                        }.map { event ->
                            Pair(event, tx)
                        }
                }
            }

        /**
         * For each contract deployment, get the contract code and save it to the database.
         */
        contractEvents.forEach { event: Pair<TxEvent, WrappedTransaction> ->

            val rawData = if (event.first.address != null)
                thorService.getAccountCode(event.first.address!!)
            else null

            val contractType = contractUtils.getContractType(rawData)

            contracts.add(
                Contract(
                    address = event.first.address,
                    blockId = block.id,
                    blockNumber = block.number,
                    txId = event.second.id,
                    creator = event.second.origin,
                    rawData = rawData,
                    contractType = contractType
                )
            )
        }

        if (contracts.isNotEmpty()) contractRepo.saveAll(contracts)
    }

    override fun getStartingBlock(): Long {
        return contractRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

}