package org.vechain.indexer

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@OpenAPIDefinition(
    info = Info(
        title =
        "VeWorld Indexer API",
        version = "0.0.1",
        description = "Blockchain data indexed for fast querying"
    )
)
@SpringBootApplication(scanBasePackages = ["org.vechain.indexer"])
@EnableMongoRepositories(basePackages = ["org.vechain.indexer.repos"])
open class VeWorldIndexerApiApplication

fun main(args: Array<String>) {
    runApplication<VeWorldIndexerApiApplication>(*args)
}
