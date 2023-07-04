package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.ChangeUnit
import io.mongock.api.annotations.Execution
import io.mongock.api.annotations.RollbackExecution
import org.slf4j.LoggerFactory
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.BuiltInContractsLoader

@ChangeUnit(id = "built-in-contracts", order = "6", author = "nawfal-labrahmi")
class BuiltInContractsInitializerChangeUnit {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Insert built-in contracts into the database. This should only execute once per session to
     * ensure the built-in contracts have been inserted.
     */
    @Execution
    fun execution(contractsLoader: BuiltInContractsLoader, contractRepository: ContractRepository) {
        // Check if built-in contracts are already inserted
        contractRepository.findAllByBlockNumber(0).firstOrNull()?.let {
            logger.info("Built-in contracts already inserted")
            return
        }

        val contracts = contractsLoader.loadBuiltInContracts()

        logger.info("Saving ${contracts.size} built-in contracts")

        if (contracts.isNotEmpty()) contractRepository.saveAll(contracts.toList())
    }

    @RollbackExecution
    fun rollbackExecution(contractRepository: ContractRepository) {
        contractRepository.deleteAll()
    }
}
