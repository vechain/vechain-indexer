package org.vechain.indexer.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.utils.JsonUtils

@Component
open class BuiltInContractsLoader(
    private val thorService: ThorService,
    @Value("classpath:built-in-contracts.json") private val contractsJson: Resource
) {

    open fun loadBuiltInContracts(): Array<IndexedContract> {
        val genesisBlock = thorService.getBlock(0)

        val contracts =
            JsonUtils.mapper.readValue(
                contractsJson.inputStream,
                Array<IndexedContract>::class.java
            )

        contracts.forEach { contract -> contract.blockId = genesisBlock.id }

        return contracts
    }
}
