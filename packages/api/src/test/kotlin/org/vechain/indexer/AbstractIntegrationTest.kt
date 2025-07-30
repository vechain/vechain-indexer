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
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.nft.NftRepository
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.transaction.IndexedTransaction
import org.vechain.indexer.transaction.TransactionRepository
import org.vechain.indexer.transfer.IndexedTransferEvent
import org.vechain.indexer.transfer.TransferEventRepository
import org.vechain.indexer.utils.JsonUtils

@RunWith(SpringRunner::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [VeWorldIndexerApiApplication::class],
)
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

    protected val TX_TYPE = object : TypeReference<IndexedTransaction>() {}
    protected val LIST_TX_TYPE = object : TypeReference<List<IndexedTransaction>>() {}
    protected val PAGINATED_TXS_TYPE =
        object : TypeReference<PaginatedResponse<IndexedTransaction>>() {}
    protected val LIST_NFT_TYPE = object : TypeReference<List<IndexedNft>>() {}
    protected val PAGINATED_NFTS_TYPES = object : TypeReference<PaginatedResponse<IndexedNft>>() {}
    protected val PAGINATED_NFT_CONTRACTS_TYPE =
        object : TypeReference<PaginatedResponse<String>>() {}
    protected val LIST_TRANSFER_EVENT_TYPE = object : TypeReference<List<IndexedTransferEvent>>() {}
    protected val PAGINATED_TRANSFER_EVENTS_TYPE =
        object : TypeReference<PaginatedResponse<IndexedTransferEvent>>() {}
    protected val PAGINATED_FUNGIBLE_TOKENS_CONTRACTS_TYPE =
        object : TypeReference<PaginatedResponse<String>>() {}

    protected val objectMapper = JsonUtils.mapper

    @Autowired lateinit var transactionRepository: TransactionRepository

    @Autowired lateinit var nftRepository: NftRepository

    @Autowired lateinit var transferEventRepository: TransferEventRepository

    @BeforeAll
    fun setup() {

        val transactions: List<IndexedTransaction> =
            loadDataFromResources("/transactions.json", LIST_TX_TYPE)
        val nfts: List<IndexedNft> = loadDataFromResources("/nfts.json", LIST_NFT_TYPE)
        val transferEvents: List<IndexedTransferEvent> =
            loadDataFromResources("/transfers.json", LIST_TRANSFER_EVENT_TYPE)

        val repos = listOf(transactionRepository, nftRepository, transferEventRepository)
        repos.forEach { it.deleteAll() }

        transactionRepository.saveAll(transactions)
        nftRepository.saveAll(nfts)
        transferEventRepository.saveAll(transferEvents)
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
                GenericContainer("mongo:8").withExposedPorts(27017).withReuse(true)

            mongoContainer.start()

            val mongoUri = "mongodb://${mongoContainer.host}:${mongoContainer.getMappedPort(27017)}"

            TestPropertyValues.of("spring.data.mongodb.uri=${mongoUri}/vechain")
                .applyTo(configurableApplicationContext.environment)
        }
    }
}
