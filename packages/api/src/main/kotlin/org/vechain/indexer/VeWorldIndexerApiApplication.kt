package org.vechain.indexer

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@OpenAPIDefinition(
    info = Info(
        title =
        "VeWorld Indexer API",
        version = "0.0.1",
        description = "Blockchain data indexed for fast querying"
    ),
    servers = [
        Server(url = "https://", description = "HTTPS server"),
        Server(url = "http://", description = "HTTP server")
    ]
)
@SpringBootApplication(scanBasePackages = ["org.vechain.indexer"])
open class VeWorldIndexerApiApplication

fun main(args: Array<String>) {
    runApplication<VeWorldIndexerApiApplication>(*args)
}
