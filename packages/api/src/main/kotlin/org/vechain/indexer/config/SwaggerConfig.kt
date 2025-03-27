package org.vechain.indexer.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class SwaggerConfig {

    @Value("\${app.version:UNKNOWN}") lateinit var appVersion: String

    @Bean
    open fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("VeWorld Indexer API")
                    .version(appVersion)
                    .description("Blockchain data indexed for fast querying")
            )
            .servers(listOf(Server().url("/").description("VeWorld Indexer")))
    }
}
