package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.utils.JsonUtils

@Component
open class BuiltInContractsLoader(
    private val thorService: ThorService,
    private val contractRepository: ContractRepository,
    @Value("classpath:built-in-contracts.json") private val contractsJson: Resource
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    open fun loadBuiltInContracts() {
        // Check if built-in contracts are already inserted
        contractRepository.findAllByBlockNumber(0).firstOrNull()?.let {
            logger.info("Built-in contracts already inserted")
            return
        }

        val genesisBlock = thorService.getBlock(0)

        val contracts =
            JsonUtils.mapper.readValue(
                contractsJson.inputStream,
                Array<IndexedContract>::class.java
            )

        contracts.forEach { contract -> contract.blockId = genesisBlock.id }

        logger.info("Saving ${contracts.size} built-in contracts")

        contractRepository.saveAll(contracts.toList())
    }
}
