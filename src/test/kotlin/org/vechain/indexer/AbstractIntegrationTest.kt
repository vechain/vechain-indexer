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
import java.util.*


@RunWith(SpringRunner::class)
@SpringBootTest
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

    companion object {
        val mongoContainer = GenericContainer("mongo:6")
            .withExposedPorts(27017)

        val thorContainer = GenericContainer("vechain/thor:v2.0.0")
            .withCommand("solo --on-demand --api-addr 0.0.0.0:8669 --data-dir /data/thor --api-cors '*'")
            .withExposedPorts(8669)
    }

    internal class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(configurableApplicationContext: ConfigurableApplicationContext) {
            mongoContainer.start()
            thorContainer.start()

            val mongoUri = "mongodb://${mongoContainer.host}:${mongoContainer.getMappedPort(27017)}"
            val thorUrl = "http://${thorContainer.host}:${thorContainer.getMappedPort(8669)}"

            TestPropertyValues.of(
                "spring.data.mongodb.uri=${mongoUri}",
                "thor.url=${thorUrl}"
            ).applyTo(configurableApplicationContext.environment)
        }
    }
}