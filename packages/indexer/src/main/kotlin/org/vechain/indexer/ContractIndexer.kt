package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedContract
import org.vechain.indexer.repository.ContractRepository
import org.vechain.indexer.service.ContractService
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.AddressUtils
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.JsonUtils
import org.vechain.thor.model.Block
import kotlin.jvm.optionals.getOrNull

@Profile("contracts")
@Component
open class ContractIndexer(
    private val thorService: ThorService,
    private val contractService: ContractService,
    private val contractRepository: ContractRepository,
    private val mongoTemplate: MongoTemplate,
    @Value("classpath:built-in-contracts.json") private val contractsJson: Resource,
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${indexer.startBlock.contracts}") private val startBlock: Long,
) : VeWorldIndexer(contractRepository, thorUrl, startBlock) {

    @OptIn(ExperimentalStdlibApi::class)
    override fun processBlock(block: Block) {

        val contracts: MutableList<IndexedContract> = mutableListOf()

        /**
         * Find all events that are contract deployments, paired with their transaction.
         */
        val masterChangeEvents = block.transactions
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

        /**
         * Process each master change event.
         */
        masterChangeEvents.forEach { (event, tx, clause) ->

            val contractAddress = event.address
            val master = AddressUtils.decode(event.data)
            val rawData = thorService.getAccountCode(contractAddress)

            val contract = contractRepository.findById(contractAddress).getOrNull()

            // If the contract is already indexed, update the master
            if (contract != null) {
                contract.previousMasters.add(contract.master)
                contract.master = master
                contractRepository.save(contract)
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

        if (contracts.isNotEmpty()) mongoTemplate.insert(contracts, IndexedContract::class.java)
    }

    @EventListener(ApplicationStartedEvent::class)
    fun insertBuiltInContracts() {

        // Check if built-in contracts are already inserted
        contractRepository.findAllByBlockNumber(0).firstOrNull()?.let {
            logger.info("Built-in contracts already inserted")
            return
        }

        val genBlock = thorService.getBlock(0)

        val contracts = JsonUtils.mapper.readValue(contractsJson.inputStream, Array<IndexedContract>::class.java)

        contracts.forEach { contract ->
            contract.blockId = genBlock.id
        }

        logger.info("Saving ${contracts.size} built-in contracts")

        contractRepository.saveAll(contracts.toList())
    }
}
