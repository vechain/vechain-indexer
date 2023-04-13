package org.vechain.indexer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
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

    protected val TX_TYPE = object : TypeReference<List<WrappedTransaction>>() {}
    protected val CONTRACT_TYPE = object : TypeReference<List<Contract>>() {}
    protected val NFT_TYPE = object : TypeReference<List<NFT>>() {}
    protected val BLOCKS_TYPE = object : TypeReference<List<Block>>() {}
    protected val TRANSFER_EVENT_TYPE = object : TypeReference<List<TransferEvent>>() {}
    protected val CLAUSES_TYPE = object : TypeReference<List<WrappedClause>>() {}
    protected val CLAUSES_RESPONSE_TYPE = object : TypeReference<PaginatedResponse<List<WrappedClause>>>() {}

    protected val objectMapper = ObjectMapper()

    init {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
    }

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

        val transactions: List<WrappedTransaction> =
            loadDataFromResources("/transactions.json", TX_TYPE)
        val contracts: List<Contract> =
            loadDataFromResources("/contracts.json", CONTRACT_TYPE)
        val nfts: List<NFT> =
            loadDataFromResources("/nfts.json", NFT_TYPE)
        val blocks: List<Block> =
            loadDataFromResources("/blocks.json", BLOCKS_TYPE)
        val transferEvents: List<TransferEvent> =
            loadDataFromResources("/transfers.json", TRANSFER_EVENT_TYPE)
        val clauses: List<WrappedClause> =
            loadDataFromResources("/clauses.json", CLAUSES_TYPE)

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

    companion object {
        val mongoContainer: GenericContainer<*> = GenericContainer("mongo:6")
            .withExposedPorts(27017)
            .withReuse(true)
    }

    internal class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {

            mongoContainer.start()

            val mongoUri = "mongodb://${mongoContainer.host}:${mongoContainer.getMappedPort(27017)}"

            TestPropertyValues.of(
                "spring.data.mongodb.uri=${mongoUri}/vechain",
            ).applyTo(configurableApplicationContext.environment)
        }
    }
}