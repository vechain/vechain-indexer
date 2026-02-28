package org.vechain.indexer.openapi

import java.io.File
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(
    "test",
    "nfts",
    "transactions",
    "transfers",
    "history",
    "stargate",
    "authority-nodes",
    "vevote",
    "vevote-results",
    "vevote-historic-proposals",
    "vevote-comments",
    "b3tr",
    "b3tr-proposal",
    "b3tr-actions",
    "b3tr-x-alloc",
    "b3tr-gm-nft",
    "b3tr-balance",
    "explorer",
    "block-usage",
    "accounts",
    "validator",
    "contracts",
)
@TestPropertySource(properties = ["de.flapdoodle.mongodb.embedded.version=7.0.14"])
@Tag("openapi")
class GenerateOpenApiSpecTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `export OpenAPI spec to api-docs json`() {
        val result = mockMvc.perform(get("/api-docs")).andExpect(status().isOk).andReturn()

        val json = result.response.contentAsString

        val outputPath =
            System.getProperty("openapi.output.path")
                ?: error(
                    "System property 'openapi.output.path' not set. " +
                        "Run via: ./gradlew :packages:api:generateOpenApiSpec"
                )

        val outputFile = File(outputPath)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(json)

        println("OpenAPI spec written to ${outputFile.absolutePath}")
    }
}
