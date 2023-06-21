package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
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

    @Transactional
    override fun processBlock(block: Block) {

        // Load built-in contracts if not loaded yet
        if (!builtInContractsLoaded) {
            insertBuiltInContracts()
        }

        // Get the master change events from the block
        val masterChangeEvents = parseMasterChangeEvents(block)
        if (masterChangeEvents.isEmpty()) return

        // Check for existing documents
        val existingContracts = contractRepo.findAllById(masterChangeEvents.map { (event) -> event.address }).toList()

        // Parse the contracts
        val contracts = parseContracts(block, masterChangeEvents, existingContracts)

        // Save the NFTs and archive the old ones
        contractService.save(existingContracts)
        contractRepo.saveAll(contracts)
    }

    private fun parseContracts(
        block: Block,
        masterChangeEvents: List<Triple<TxEvent, Transaction, Clause>>,
        existingContracts: List<IndexedContract>
    ): List<IndexedContract> {
        val contracts: MutableList<IndexedContract> = mutableListOf()

        masterChangeEvents.forEach { (event, tx, clause) ->

            val contractAddress = event.address
            val master = AddressUtils.decode(event.data)
            val rawData = thorService.getAccountCode(contractAddress)

            // Handle case of two master change events for the same contract
            val multipleMasterChangeContract = contracts.find { it.address == contractAddress }
            if (multipleMasterChangeContract != null) {
                multipleMasterChangeContract.previousMasters.add(multipleMasterChangeContract.master)
                multipleMasterChangeContract.master = master
            } else {
                // If the contract is already indexed, update the master
                val contract = existingContracts.find { it.address == contractAddress }
                if (contract != null) {
                    contracts.add(
                        IndexedContract(
                            address = contractAddress,
                            version = contract.version + 1,
                            blockId = block.id,
                            blockNumber = block.number,
                            blockTimestamp = block.timestamp,
                            txId = tx.id,
                            creator = tx.origin,
                            master = master,
                            rawData = rawData,
                            isVip180 = contract.isVip180,
                            isVip181 = contract.isVip181,
                            isVip210 = contract.isVip210,
                            isErc20 = contract.isErc20,
                            isErc721 = contract.isErc721,
                            isErc1155 = contract.isErc1155,
                            previousMasters = contract.previousMasters.plus(contract.master).toMutableSet(),
                        )
                    )
                } else {
                    // If the contract is not indexed yet, index it
                    contracts.add(
                        IndexedContract(
                            address = contractAddress,
                            version = 1,
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
        return contracts
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

    @Transactional
    override fun rollback(blockNumber: Long) {
        //Get all contracts that were created in the block
        val contracts = contractRepo.findAllByBlockNumber(blockNumber)

        //Get previous version of contracts
        val previousVersions = mutableSetOf<IndexedContract>()
        contracts.forEach { contract ->
            if (contract.version > 1) {
                val previousVersion = contractService.getPreviousVersion(contract)
                previousVersions.add(previousVersion)
            }
        }

        // Remove contracts with version 1
        contractRepo.deleteAll(contracts.filter { it.version == 1 })

        // Save previous versions
        contractRepo.saveAll(previousVersions)
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
