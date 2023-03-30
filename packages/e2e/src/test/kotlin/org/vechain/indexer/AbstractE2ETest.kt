package org.vechain.indexer

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.time.Duration

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractE2ETest {

    @Container
    val MONGO_COMPOSE_CONTAINER: DockerComposeContainer<*>

    @Container
    val MONGO_SETUP_CONTAINER: DockerComposeContainer<*>

    @Container
    val THOR_COMPOSE: DockerComposeContainer<*>

    @Container
    val APPLICATION_COMPOSE_CONTAINER: DockerComposeContainer<*>

    @AfterAll
    fun afterAll() {
        APPLICATION_COMPOSE_CONTAINER.stop()
        MONGO_COMPOSE_CONTAINER.stop()
        MONGO_SETUP_CONTAINER.stop()
        THOR_COMPOSE.stop()
    }

    fun getApiURL(): String {
        val apiOptional = APPLICATION_COMPOSE_CONTAINER.getContainerByServiceName("vechain-indexer-api")

        if (!apiOptional.isPresent) {
            throw RuntimeException("Could not find API container")
        }

        val apiContainer = apiOptional.get()

        if (!apiContainer.isHealthy || !apiContainer.isRunning) {
            throw RuntimeException("API container is not healthy")
        }

        val port = apiContainer.getMappedPort(8080)

        return "http://${apiContainer.host}:${port}"
    }

    init {
        val e2ePath = System.getProperty("user.dir")
        val rootPath = e2ePath.substring(0, e2ePath.indexOf("/packages/e2e"))

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
        THOR_COMPOSE = DockerComposeContainer(File("${rootPath}/thor/docker-compose.yaml"))
            .waitingFor(
                "thor-solo", LogMessageWaitStrategy()
                    .withRegEx(".*(new block packed).*")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofSeconds(180L))
            )
            .waitingFor(
                "thor-tx-script", LogMessageWaitStrategy()
                    .withRegEx(".*(Thor TX Script successfully executed).*")
                    .withTimes(1)
                    .withStartupTimeout(Duration.ofSeconds(180L))
            )
            .withLocalCompose(true)
            .withBuild(true)

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
            .withOptions("--compatibility --env-file ${rootPath}/.env.example")

        try {
            MONGO_COMPOSE_CONTAINER.start()
            MONGO_SETUP_CONTAINER.start()
            THOR_COMPOSE.start()
            APPLICATION_COMPOSE_CONTAINER.start()

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}