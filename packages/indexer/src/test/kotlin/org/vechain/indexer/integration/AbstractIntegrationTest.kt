package org.vechain.indexer.integration

import java.time.Duration
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
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.utility.DockerImageName
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.LogsIndexer
import org.vechain.indexer.Status

@RunWith(SpringRunner::class)
@SpringBootTest
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {
    @Autowired lateinit var allBlockIndexer: List<BlockIndexer>

    @Autowired lateinit var allLogIndexer: List<LogsIndexer>

    fun waitForFullySynced() {
        for (i in 0..120) {
            if (
                allBlockIndexer.all { it.status == Status.FULLY_SYNCED } &&
                    allLogIndexer.all { it.status == Status.FULLY_SYNCED }
            ) {
                return
            }
            Thread.sleep(500)
        }

        throw Exception("Indexers not fully synced after 60 seconds")
    }

    companion object {
        val thorNetwork = Network.newNetwork()

        val mongoContainer: GenericContainer<*> =
            MongoDBContainer("mongo:8").withExposedPorts(27017).withReuse(true)

        val thorContainer: GenericContainer<*> =
            GenericContainer("vechain/thor:v2.2.1")
                .withCommand(
                    "solo --on-demand --api-addr 0.0.0.0:8669 --data-dir /data/thor --api-cors '*'"
                )
                .withExposedPorts(8669)
                .withReuse(true)
                .withNetwork(thorNetwork)
                .withCreateContainerCmdModifier { cmd -> cmd.withHostName("thor-node") }
                .waitingFor(
                    (LogMessageWaitStrategy())
                        .withRegEx(".*(new block packed).*")
                        .withTimes(1)
                        .withStartupTimeout(Duration.ofSeconds(180L))
                )

        val transactionScript: GenericContainer<*> =
            GenericContainer(
                    DockerImageName.parse(
                        "ghcr.io/vechain/thor-transactions-script:88bffbb8a24c118a1faa1671dcbb92cd733e7ab4"
                    )
                )
                .withNetwork(thorNetwork)
                .withEnv("NODE_URL", "http://thor-node:8669")
                .waitingFor(
                    LogMessageWaitStrategy()
                        .withRegEx(".*(Thor TX Script successfully executed).*")
                        .withTimes(1)
                        .withStartupTimeout(Duration.ofSeconds(180L))
                )
                .withReuse(true)
    }

    internal class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            mongoContainer.start()
            thorContainer.start()
            transactionScript.start()

            val mongoUri =
                "mongodb://${mongoContainer.host}:${mongoContainer.getMappedPort(27017)}/vechain"
            val thorUrl = "http://localhost:${thorContainer.getMappedPort(8669)}"

            // Extract the NFT Blacklist address from logs
            val logs = transactionScript.logs
            val regex = Regex("Deployed NFTBlacklist @ (0x[a-fA-F0-9]{40})")
            val match = regex.find(logs)

            val nftBlacklistAddress =
                match?.groupValues?.get(1)
                    ?: throw IllegalStateException(
                        "NFTBlacklist address not found in transactionScript logs"
                    )

            TestPropertyValues.of(
                    "spring.data.mongodb.uri=$mongoUri",
                    "thor.url=$thorUrl",
                    "indexer.blacklist.contract_address=$nftBlacklistAddress",
                )
                .applyTo(configurableApplicationContext.environment)
        }
    }
}
