package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.ChangeUnit
import io.mongock.api.annotations.Execution
import io.mongock.api.annotations.RollbackExecution
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.BuiltInContractsLoader

@ChangeUnit(id = "built-in-contracts", order = "6", author = "nawfal-labrahmi")
class BuiltInContractsInitializerChangeUnit {

    /**
     * Insert built-in contracts into the database. This should only execute once per session to
     * ensure the built-in contracts have been inserted.
     */
    @Execution
    fun execution(contractsLoader: BuiltInContractsLoader) {
        contractsLoader.loadBuiltInContracts()
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate, contractRepository: ContractRepository) {
        contractRepository.deleteAll()
    }
}
