package org.vechain.indexer.openapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(
    "test",
    "nfts",
    "transactions",
    "transfers",
    "history",
    "stargate",
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
class OpenApiDocumentationContractTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `history address parameters are documented with address patterns`() {
        val spec = fetchSpec()
        val parameters = spec.at("/paths/~1api~1v2~1history~1{account}/get/parameters")

        expectThat(findParameter(parameters, "account", "path").at("/schema/pattern").asText())
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(
                findParameter(parameters, "contractAddress", "query").at("/schema/pattern").asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
    }

    @Test
    fun `transfer and nft address query parameters are documented with address patterns`() {
        val transferParameters = specParameters("/paths/~1api~1v1~1transfers/get/parameters")
        val nftParameters = specParameters("/paths/~1api~1v1~1nfts/get/parameters")

        expectThat(
                findParameter(transferParameters, "address", "query").at("/schema/pattern").asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(
                findParameter(transferParameters, "tokenAddress", "query")
                    .at("/schema/pattern")
                    .asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(findParameter(nftParameters, "address", "query").at("/schema/pattern").asText())
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
        expectThat(
                findParameter(nftParameters, "contractAddress", "query")
                    .at("/schema/pattern")
                    .asText()
            )
            .isEqualTo("^(0x)?[0-9a-fA-F]{40}$")
    }

    @Test
    fun `common api responses document problem json for 404 and 500`() {
        val spec = fetchSpec()
        val responses = spec.at("/paths/~1api~1v2~1history~1{account}/get/responses")

        expectThat(contentTypes(responses.at("/404/content")))
            .contains("application/json", "application/problem+json")
        expectThat(contentTypes(responses.at("/500/content")))
            .contains("application/json", "application/problem+json")
    }

    private fun specParameters(pointer: String): JsonNode = fetchSpec().at(pointer)

    private fun fetchSpec(): JsonNode {
        val response =
            mockMvc.perform(get("/api-docs")).andExpect(status().isOk).andReturn().response

        return objectMapper.readTree(response.contentAsString)
    }

    private fun findParameter(parameters: JsonNode, name: String, location: String): JsonNode =
        parameters.firstOrNull {
            it.path("name").asText() == name && it.path("in").asText() == location
        } ?: error("Parameter '$name' in '$location' not found")

    private fun contentTypes(content: JsonNode): List<String> =
        content.fieldNames().asSequence().toList()
}
