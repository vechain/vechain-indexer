package org.vechain.indexer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.ContractRepo
import org.vechain.indexer.repos.TransactionRepo
import java.util.*


@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIntegrationTest {

    protected val TX_TYPE = object : TypeReference<List<WrappedTransaction>>() {}
    protected val CONTRACT_TYPE = object : TypeReference<List<Contract>>() {}

    protected val objectMapper = ObjectMapper()

    init {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Autowired
    lateinit var transactionRepository: TransactionRepo

    @Autowired
    lateinit var contractRepository: ContractRepo

    @BeforeAll
    fun setup() {

        val transactions: List<WrappedTransaction> =
            loadDataFromResources("/transactions.json", TX_TYPE)
        val contracts: List<Contract> =
            loadDataFromResources("/contracts.json", CONTRACT_TYPE)

        transactionRepository.saveAll(transactions)
        contractRepository.saveAll(contracts)

    }


    /**
     * Load json files from resources
     */
    fun <T> loadDataFromResources(path: String, type: TypeReference<T>): T {
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
                "spring.data.mongodb.uri=${mongoUri}",
            ).applyTo(configurableApplicationContext.environment)
        }
    }
}