package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Contract
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.ContractUtils

@Profile("contract-indexer", "prod")
@Component
open class ContractIndexer(
    private val thorService: ThorService, private val contractRepo: ContractRepo,
    private val contractUtils: ContractUtils
) : Indexer() {
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        var contracts: List<Contract> = emptyList()
        block.transactions.forEach { tx ->
            if (tx.reverted != false) {
                tx.clauses.forEachIndexed { index, clause ->
                    if (tx.outputs.size >= index + 1) {
                        val outputs = tx.outputs[index]
                        if (contractUtils.isContractDeployment(clause, outputs)) {
                            contracts = contracts.plus(
                                Contract(
                                    outputs.contractAddress!!,
                                    block.id,
                                    block.number,
                                    tx.id,
                                    index,
                                    tx.origin,
                                    clause.data
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