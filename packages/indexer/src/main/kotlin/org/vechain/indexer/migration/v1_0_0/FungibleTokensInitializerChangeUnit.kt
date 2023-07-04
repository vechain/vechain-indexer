package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.ChangeUnit
import io.mongock.api.annotations.Execution
import io.mongock.api.annotations.RollbackExecution
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.model.IndexedFungibleTokenContracts

@ChangeUnit(id = "fungible-tokens-initializer", order = "8", author = "nawfal-labrahmi")
class FungibleTokensInitializerChangeUnit {

    companion object {
        val FUNGIBLE_TOKENS = IndexedFungibleTokenContracts::class.java
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        mongoTemplate.createCollection(FUNGIBLE_TOKENS)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.dropCollection(FUNGIBLE_TOKENS)
    }
}
