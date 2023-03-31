package org.vechain.indexer

import org.junit.runner.RunWith
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.*


@RunWith(SpringRunner::class)
@SpringBootTest
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

    companion object {
        val mongoContainer = GenericContainer("mongo:6")
            .withExposedPorts(27017)
            .withReuse(true)
            .waitingFor(
                (LogMessageWaitStrategy())
                    .withRegEx(".*(Waiting for connections).*")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofSeconds(180L))
            )

        val thorContainer = GenericContainer("vechain/thor:v2.0.0")
            .withCommand("solo --on-demand --api-addr 0.0.0.0:8669 --data-dir /data/thor --api-cors '*'")
            .withExposedPorts(8669)
            .withReuse(true)
            .waitingFor(
                (LogMessageWaitStrategy())
                    .withRegEx(".*(new block packed).*")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofSeconds(180L))
            )

        val transactionScript = GenericContainer(
            DockerImageName.parse("ghcr.io/vechainfoundation/thor-transactions-script:b58b67122686a5dbf0baad7c45d3cb848e9361c9")
        )
            //TODO: Create a wait strategy if we're waiting for transactions to be submitted
            .withReuse(true)
    }

    internal class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            mongoContainer.start()
            thorContainer.start()

            val mongoUri = "mongodb://${mongoContainer.host}:${mongoContainer.getMappedPort(27017)}"
            val thorUrl = "http://${thorContainer.host}:${thorContainer.getMappedPort(8669)}"

            transactionScript.withEnv("NODE_URL", thorUrl)
            transactionScript.start()

            TestPropertyValues.of(
                "spring.data.mongodb.uri=${mongoUri}",
                "thor.url=${thorUrl}"
            ).applyTo(configurableApplicationContext.environment)
        }
    }
}