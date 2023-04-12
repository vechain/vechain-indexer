package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Clause
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.TxEvent
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.ContractUtils
import kotlin.jvm.optionals.getOrNull

@Profile("contract-indexer", "prod")
@Component
class ContractIndexer(
    private val thorService: ThorService,
    private val contractRepo: ContractRepo,
    private val contractService: ContractService
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
        masterChangeEvents.forEach { event: Triple<TxEvent, WrappedTransaction, Clause> ->

            val contractAddress = event.first.address ?: return@forEach

            val rawData = thorService.getAccountCode(contractAddress)

            // If there is no contract data then we assume this is a change of master for an existing contract.
            // Else, this is a new contract deployment.
            if (rawData == null || rawData == "0x") {
                val contract = event.third.to?.let { contractRepo.findById(it).getOrNull() }
                if (contract != null && event.first.data != null) {
                    contract.master = ContractUtils.removeTopicPadding(event.first.data!!)
                    contracts.add(contract)
                }

            } else {

                contracts.add(
                    Contract(
                        address = contractAddress,
                        blockId = block.id,
                        blockNumber = block.number,
                        txId = event.second.id,
                        creator = event.second.origin,
                        master = ContractUtils.removeTopicPadding(event.first.data!!),
                        rawData = rawData,
                        //TODO: Add back ContractUtils check before contract service to reduce API calls,
                        isVip180 = contractService.isVip180(contractAddress),
                        isVip181 = contractService.isVip181(contractAddress),
                        isErc20 = contractService.isErc20(contractAddress),
                        isErc721 = contractService.isErc721(contractAddress),
                    )
                )
            }

        }

        if (contracts.isNotEmpty()) contractRepo.saveAll(contracts)
    }

    override fun getStartingBlock(): Long {
        return contractRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

}