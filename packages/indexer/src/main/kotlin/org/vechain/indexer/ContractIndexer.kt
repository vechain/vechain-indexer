package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.TxEvent
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.specifications.Contracts
import org.vechain.indexer.utils.ContractUtils
import kotlin.jvm.optionals.getOrNull

@Profile("contract-indexer", "prod")
@Component
class ContractIndexer(
    private val thorService: ThorService,
    private val contractRepo: ContractRepo,
    private val contractUtils: ContractUtils
) : Indexer() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        val contracts: MutableList<Contract> = mutableListOf()

        /**
         * Find all events that are contract deployments, paired with their transaction.
         */
        val masterChangeEvents = block.transactions
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
         * Process each master change event.
         */
        masterChangeEvents.forEach { event: Pair<TxEvent, WrappedTransaction> ->

            val rawData = if (event.first.address != null)
                thorService.getAccountCode(event.first.address!!)
            else null

            // If there is no contract data then we assume this is a change of master for an existing contract.
            // Else, this is a new contract deployment.
            if (rawData == null || rawData == "0x") {
                val contract = contractRepo.findById(event.first.address!!).getOrNull()
                if (contract != null && event.first.data != null) {
                    contract.master = event.first.data
                    contracts.add(contract)
                }

            } else
                contracts.add(
                    Contract(
                        address = event.first.address,
                        blockId = block.id,
                        blockNumber = block.number,
                        txId = event.second.id,
                        creator = event.second.origin,
                        master = event.second.origin,
                        rawData = rawData,
                        isErc20 = contractUtils.isContractType(Contracts.ERC20, rawData),
                        isVip180 = contractUtils.isContractType(Contracts.VIP180, rawData)
                    )
                )
        }

        if (contracts.isNotEmpty()) contractRepo.saveAll(contracts)
    }

    override fun getStartingBlock(): Long {
        return contractRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

}