package org.vechain.indexer

import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.time.Duration
import java.time.ZonedDateTime

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ContainerTests {

    @Container
    val MONGO_COMPOSE_CONTAINER: DockerComposeContainer<*>

    @Container
    val MONGO_SETUP_CONTAINER: DockerComposeContainer<*>

    @Container
    val THOR_NODE_CONTAINER: GenericContainer<*>

    @Container
    val APPLICATION_COMPOSE_CONTAINER: DockerComposeContainer<*>

    init {
        val e2ePath = System.getProperty("user.dir")
        val rootPath = e2ePath.substring(0, e2ePath.indexOf("/packages/e2e"))

        val thor = Network.newNetwork()

        /**
         * Mongo Infra
         */
        val mongoWaitStrategy = LogMessageWaitStrategy()
            .withRegEx(".*(Waiting for connections).*")
            .withTimes(1)
            .withStartupTimeout(Duration.ofSeconds(180L))

        MONGO_COMPOSE_CONTAINER = DockerComposeContainer(File("$rootPath/database/docker-compose-mongo.yaml"))
            .withExposedService("mongo-node1", 27017)
            .withExposedService("mongo-node2", 27018)
            .withExposedService("mongo-node3", 27019)
            .waitingFor("mongo-node1", mongoWaitStrategy)
            .waitingFor("mongo-node2", mongoWaitStrategy)
            .waitingFor("mongo-node3", mongoWaitStrategy)

        MONGO_SETUP_CONTAINER =
            DockerComposeContainer(File("${rootPath}/database/docker-compose-mongo-setup.yaml"))
                .waitingFor(
                    "mongo-setup", LogMessageWaitStrategy()
                        .withRegEx(".*(Loading file: /scripts/init.js).*")
                        .withTimes(1)
                        .withStartupTimeout(Duration.ofSeconds(180L))
                )
                .withLocalCompose(true)

        /**
         * Thor Infra
         */

        THOR_NODE_CONTAINER = GenericContainer("vechain/thor:v2.0.0")
            .withCommand("solo --on-demand --api-addr 0.0.0.0:8669 --data-dir /data/thor --api-cors '*'")
            .withExposedPorts(8669)
            .withReuse(true)
            .waitingFor(
                (LogMessageWaitStrategy())
                    .withRegEx(".*(new block packed).*")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofSeconds(180L))
            )
            .withNetwork(thor)
            .withNetworkAliases("thor-solo")


        val THOR_SCRIPT_CONTAINER = GenericContainer(
            DockerImageName.parse("ghcr.io/vechainfoundation/thor-transactions-script:b58b67122686a5dbf0baad7c45d3cb848e9361c9")
        )
            .waitingFor(
                (LogMessageWaitStrategy())
                    .withRegEx(".*(Thor TX Script successfully executed).*")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofSeconds(180L))
            )
            .withReuse(true)
            .withNetwork(thor)
            .withEnv("NODE_URL", "http://thor-solo:8669")
            .withFileSystemBind("$e2ePath/src/test/resources", "/app/.output")

        /**
         * Application
         */
        APPLICATION_COMPOSE_CONTAINER = DockerComposeContainer(File("${rootPath}/docker-compose.yaml"))
            .withExposedService("vechain-indexer-api", 8080)
            .waitingFor(
                "vechain-indexer-api", LogMessageWaitStrategy()
                    .withRegEx(".*(Started VeWorldIndexerApiApplicationKt in).*")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofSeconds(300L))
            )
            .waitingFor(
                "vechain-indexer", LogMessageWaitStrategy()
                    .withRegEx(".*(Started IndexerApplicationKt in).*")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofSeconds(300L))
            )
            .withBuild(true)
            .withLocalCompose(true)
        

        try {
            MONGO_COMPOSE_CONTAINER.start()
            MONGO_SETUP_CONTAINER.start()
            THOR_NODE_CONTAINER.start()
            THOR_SCRIPT_CONTAINER.start()

            //Print the time now
            val timeNow = ZonedDateTime.now()
            APPLICATION_COMPOSE_CONTAINER.start()

        } catch (e: Exception) {
            val time2 = ZonedDateTime.now()
            e.printStackTrace()
            throw e
        }
    }
}