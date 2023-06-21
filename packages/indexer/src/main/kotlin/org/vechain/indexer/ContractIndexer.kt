package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepo
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.AddressUtils
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.JsonUtils
import org.vechain.thor.model.Block
import org.vechain.thor.model.Clause
import org.vechain.thor.model.Transaction
import org.vechain.thor.model.TxEvent
import kotlin.jvm.optionals.getOrNull

@Profile("contracts")
@Component
open class ContractIndexer(
    private val thorService: ThorService,
    private val contractService: ContractService,
    private val contractRepo: ContractRepo,
    @Value("classpath:built-in-contracts.json") private val contractsJson: Resource,
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${indexer.startBlock.contracts}") private val startBlock: Long,
) : VeWorldIndexer(contractRepo, thorUrl, startBlock) {

    private var builtInContractsLoaded = false

    @OptIn(ExperimentalStdlibApi::class)
    override fun processBlock(block: Block) {

        if (!builtInContractsLoaded) {
            insertBuiltInContracts()
        }

        val contracts: MutableList<IndexedContract> = mutableListOf()

        val masterChangeEvents = parseMasterChangeEvents(block)

        /**
         * Process each master change event.
         */
        masterChangeEvents.forEach { (event, tx, clause) ->

            val contractAddress = event.address
            val master = AddressUtils.decode(event.data)
            val rawData = thorService.getAccountCode(contractAddress)

            val contract = contractRepo.findById(contractAddress).getOrNull()

            // If the contract is already indexed, update the master
            if (contract != null) {
                contract.previousMasters.add(contract.master)
                contract.master = master
                contracts.add(contract)
            } else {

                val existing = contracts.find { it.address == contractAddress }

                //To handle events where master event is fired twice for the same contract
                if (existing != null) {
                    existing.previousMasters.add(existing.master)
                    existing.master = master
                } else {
                    contracts.add(
                        IndexedContract(
                            address = contractAddress,
                            blockId = block.id,
                            blockNumber = block.number,
                            blockTimestamp = block.timestamp,
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
        }

        if (contracts.isNotEmpty()) contractRepo.saveAll(contracts)
    }

    /**
     * Find all events that are contract deployments, paired with their transaction.
     */
    private fun parseMasterChangeEvents(block: Block): List<Triple<TxEvent, Transaction, Clause>> {
        return block.transactions
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
    }

    /**
     * Insert built-in contracts into the database.
     * This should only execute once per session to ensure the built-in contracts have been inserted.
     */
    fun insertBuiltInContracts() {

        // Check if built-in contracts are already inserted
        contractRepo.findAllByBlockNumber(0).firstOrNull()?.let {
            logger.info("Built-in contracts already inserted")
            builtInContractsLoaded = true
            return
        }

        val genBlock = thorService.getBlock(0)

        val contracts = JsonUtils.mapper.readValue(contractsJson.inputStream, Array<IndexedContract>::class.java)

        contracts.forEach { contract ->
            contract.blockId = genBlock.id
        }

        logger.info("Saving ${contracts.size} built-in contracts")

        contractRepo.saveAll(contracts.toList())

        builtInContractsLoaded = true
    }
}
