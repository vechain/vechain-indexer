package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepo
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils.extractMasterChangeEvents
import org.vechain.indexer.utils.JsonUtils
import org.vechain.thor.model.Block

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

    @Transactional
    override fun processBlock(block: Block) {

        // Get the master change events from the block
        val masterChangeEvents = extractMasterChangeEvents(block)
        if (masterChangeEvents.isEmpty()) return

        // Check for existing documents
        val existingContracts = contractRepo.findAllById(masterChangeEvents.map { (event) -> event.address }).toList()

        // Parse the contracts
        val contracts = contractService.parseContracts(block, masterChangeEvents, existingContracts)

        // Save the NFTs and archive the old ones
        if (existingContracts.isNotEmpty()) {
            contractService.archive(existingContracts)
        }
        contractRepo.saveAll(contracts)
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
    @EventListener(ApplicationStartedEvent::class)
    private fun insertBuiltInContracts() {

        // Check if built-in contracts are already inserted
        contractRepo.findAllByBlockNumber(0).firstOrNull()?.let {
            logger.info("Built-in contracts already inserted")
            return
        }

        val genBlock = thorService.getBlock(0)

        val contracts = JsonUtils.mapper.readValue(contractsJson.inputStream, Array<IndexedContract>::class.java)

        contracts.forEach { contract ->
            contract.blockId = genBlock.id
        }

        logger.info("Saving ${contracts.size} built-in contracts")

        contractRepo.saveAll(contracts.toList())
    }
}
