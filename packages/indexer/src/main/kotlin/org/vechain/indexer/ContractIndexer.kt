package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.constants.MASTER_EVENT_SIGNATURE
import org.vechain.indexer.model.Contract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.specifications.Contracts
import org.vechain.indexer.utils.ContractUtils

@Profile("contract-indexer", "prod")
@Component
class ContractIndexer(
    private val thorService: ThorService, private val contractRepo: ContractRepo,
    private val contractUtils: ContractUtils
) : Indexer() {
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        val contracts: MutableList<Contract> = mutableListOf()
        block.transactions.forEach { tx ->
            if (tx.reverted == false) {
                tx.outputs.forEachIndexed { index, outputs ->
                    outputs.events.forEach { event ->
                        if (event.topics[0] === MASTER_EVENT_SIGNATURE) {
                            val clause = tx.clauses.getOrNull(index)
                            contracts.add(
                                Contract(
                                    address = outputs.events[0].address,
                                    blockId = block.id,
                                    blockNumber = block.number,
                                    txId = tx.id,
                                    clauseIndex = index,
                                    creator = tx.origin,
                                    factoryContract = clause?.to,
                                    rawData = clause?.data,
                                    isErc20 = contractUtils.isContractType(Contracts.ERC20, clause?.data)
                                )
                            )
                        }
                    }
                }
            }
        }

        if (contracts.isNotEmpty()) contractRepo.saveAll(contracts)
    }

    override fun getStartingBlock(): Long {
        return contractRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

}