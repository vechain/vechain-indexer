package org.vechain.indexer

import com.fasterxml.jackson.core.type.TypeReference
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
import org.vechain.indexer.repos.*
import org.vechain.indexer.utils.JsonUtils
import java.util.*


@RunWith(SpringRunner::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [VeWorldIndexerApiApplication::class]
)
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

    protected val TX_TYPE = object : TypeReference<Transaction>() {}
    protected val LIST_TX_TYPE = object : TypeReference<List<Transaction>>() {}
    protected val CONTRACT_TYPE = object : TypeReference<Contract>() {}
    protected val LIST_CONTRACT_TYPE = object : TypeReference<List<Contract>>() {}
    protected val LIST_NFT_TYPE = object : TypeReference<List<NFT>>() {}
    protected val BLOCK_TYPE = object : TypeReference<Block>() {}
    protected val BLOCKS_TYPE = object : TypeReference<List<Block>>() {}
    protected val LIST_TRANSFER_EVENT_TYPE = object : TypeReference<List<TransferEvent>>() {}
    protected val LIST_CLAUSE_TYPE = object : TypeReference<List<WrappedClause>>() {}

    protected val objectMapper = JsonUtils.mapper

    @Autowired
    lateinit var transactionRepository: TransactionRepo

    @Autowired
    lateinit var contractRepository: ContractRepo

    @Autowired
    lateinit var nftRepo: NFTRepo

    @Autowired
    lateinit var blockRepo: BlockRepo

    @Autowired
    lateinit var transferEventRepo: TransferEventRepo

    @Autowired
    lateinit var clauseRepo: ClauseRepo

    @BeforeAll
    fun setup() {

        val transactions: List<Transaction> =
            loadDataFromResources("/transactions.json", LIST_TX_TYPE)
        val contracts: List<Contract> =
            loadDataFromResources("/contracts.json", LIST_CONTRACT_TYPE)
        val nfts: List<NFT> =
            loadDataFromResources("/nfts.json", LIST_NFT_TYPE)
        val blocks: List<Block> =
            loadDataFromResources("/blocks.json", BLOCKS_TYPE)
        val transferEvents: List<TransferEvent> =
            loadDataFromResources("/transfers.json", LIST_TRANSFER_EVENT_TYPE)
        val clauses: List<WrappedClause> =
            loadDataFromResources("/clauses.json", LIST_CLAUSE_TYPE)

        val repos = listOf(transactionRepository, contractRepository, nftRepo, blockRepo, transferEventRepo, clauseRepo)
        repos.forEach { it.deleteAll() }

        transactionRepository.saveAll(transactions)
        contractRepository.saveAll(contracts)
        nftRepo.saveAll(nfts)
        blockRepo.saveAll(blocks)
        transferEventRepo.saveAll(transferEvents)
        clauseRepo.saveAll(clauses)
    }

    /**
     * Load json files from resources
     */
    private fun <T> loadDataFromResources(path: String, type: TypeReference<T>): T {
        val file = AbstractIntegrationTest::class.java.getResource(path)
            ?: throw Exception("File not found: $path")

        val rawJson: String = file.readText()

        if (rawJson.isEmpty()) throw Exception("Empty json file: $path")

        return objectMapper.readValue(rawJson, type)
    }

    internal class Initializer :
        ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            val mongoContainer: GenericContainer<*> = GenericContainer("mongo:6")
                .withExposedPorts(27017)
                .withReuse(true)

            mongoContainer.start()

            val mongoUri = "mongodb://${mongoContainer.host}:${mongoContainer.getMappedPort(27017)}"

            TestPropertyValues.of(
                "spring.data.mongodb.uri=${mongoUri}/vechain",
            ).applyTo(configurableApplicationContext.environment)
        }
    }
}