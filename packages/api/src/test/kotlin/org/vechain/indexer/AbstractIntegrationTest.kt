package org.vechain.indexer

import com.fasterxml.jackson.core.type.TypeReference
import java.util.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.containers.GenericContainer
import org.vechain.indexer.model.*
import org.vechain.indexer.model.rest.PaginatedResponse
import org.vechain.indexer.repository.*
import org.vechain.indexer.utils.JsonUtils

@RunWith(SpringRunner::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [VeWorldIndexerApiApplication::class]
)
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

    protected val TX_TYPE = object : TypeReference<IndexedTransaction>() {}
    protected val LIST_TX_TYPE = object : TypeReference<List<IndexedTransaction>>() {}
    protected val PAGINATED_TXS_TYPE =
        object : TypeReference<PaginatedResponse<IndexedTransaction>>() {}
    protected val CONTRACT_TYPE = object : TypeReference<IndexedContract>() {}
    protected val LIST_CONTRACT_TYPE = object : TypeReference<List<IndexedContract>>() {}
    protected val PAGINATED_CONTRACTS_TYPE =
        object : TypeReference<PaginatedResponse<IndexedContract>>() {}
    protected val LIST_NFT_TYPE = object : TypeReference<List<IndexedNFT>>() {}
    protected val PAGINATED_NFTS_TYPES = object : TypeReference<PaginatedResponse<IndexedNFT>>() {}
    protected val PAGINATED_NFT_CONTRACTS_TYPE =
        object : TypeReference<PaginatedResponse<String>>() {}
    protected val BLOCK_TYPE = object : TypeReference<IndexedBlock>() {}
    protected val BLOCKS_TYPE = object : TypeReference<List<IndexedBlock>>() {}
    protected val LIST_TRANSFER_EVENT_TYPE = object : TypeReference<List<IndexedTransferEvent>>() {}
    protected val PAGINATED_TRANSFER_EVENTS_TYPE =
        object : TypeReference<PaginatedResponse<IndexedTransferEvent>>() {}
    protected val LIST_CLAUSE_TYPE = object : TypeReference<List<IndexedClause>>() {}
    protected val PAGINATED_CLAUSES_TYPE =
        object : TypeReference<PaginatedResponse<IndexedClause>>() {}
    protected val PAGINATED_FUNGIBLE_TOKENS_CONTRACTS_TYPE =
        object : TypeReference<PaginatedResponse<String>>() {}

    protected val objectMapper = JsonUtils.mapper

    @Autowired lateinit var transactionRepository: TransactionRepository

    @Autowired lateinit var contractRepository: ContractRepository

    @Autowired lateinit var nftRepository: NFTRepository

    @Autowired lateinit var blockRepository: BlockRepository

    @Autowired lateinit var transferEventRepository: TransferEventRepository

    @Autowired lateinit var clauseRepository: ClauseRepository

    @BeforeAll
    fun setup() {

        val transactions: List<IndexedTransaction> =
            loadDataFromResources("/transactions.json", LIST_TX_TYPE)
        val contracts: List<IndexedContract> =
            loadDataFromResources("/contracts.json", LIST_CONTRACT_TYPE)
        val nfts: List<IndexedNFT> = loadDataFromResources("/nfts.json", LIST_NFT_TYPE)
        val blocks: List<IndexedBlock> = loadDataFromResources("/blocks.json", BLOCKS_TYPE)
        val transferEvents: List<IndexedTransferEvent> =
            loadDataFromResources("/transfers.json", LIST_TRANSFER_EVENT_TYPE)
        val clauses: List<IndexedClause> = loadDataFromResources("/clauses.json", LIST_CLAUSE_TYPE)

        val repos =
            listOf(
                transactionRepository,
                contractRepository,
                nftRepository,
                blockRepository,
                transferEventRepository,
                clauseRepository
            )
        repos.forEach { it.deleteAll() }

        transactionRepository.saveAll(transactions)
        contractRepository.saveAll(contracts)
        nftRepository.saveAll(nfts)
        blockRepository.saveAll(blocks)
        transferEventRepository.saveAll(transferEvents)
        clauseRepository.saveAll(clauses)
    }

    /** Load json files from resources */
    private fun <T> loadDataFromResources(path: String, type: TypeReference<T>): T {
        val file =
            AbstractIntegrationTest::class.java.getResource(path)
                ?: throw Exception("File not found: $path")

        val rawJson: String = file.readText()

        if (rawJson.isEmpty()) throw Exception("Empty json file: $path")

        return objectMapper.readValue(rawJson, type)
    }

    internal class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            val mongoContainer: GenericContainer<*> =
                GenericContainer("mongo:6").withExposedPorts(27017).withReuse(true)

            mongoContainer.start()

            val mongoUri = "mongodb://${mongoContainer.host}:${mongoContainer.getMappedPort(27017)}"

            TestPropertyValues.of(
                    "spring.data.mongodb.uri=${mongoUri}/vechain",
                )
                .applyTo(configurableApplicationContext.environment)
        }
    }
}
