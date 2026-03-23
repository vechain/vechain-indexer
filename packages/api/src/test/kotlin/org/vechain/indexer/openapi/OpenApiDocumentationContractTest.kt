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
import strikt.assertions.isFalse
import strikt.assertions.isTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = OpenApiActiveProfilesResolver::class)
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

    @Test
    fun `profile gated public routes are present in generated spec`() {
        val spec = fetchSpec()

        expectThat(spec.at("/paths/~1api~1v1~1validators").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1validators~1blocks~1missed").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1validators~1delegations~1count").isMissingNode)
            .isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1contracts~1{address}").isMissingNode).isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1contracts~1by-master~1{address}").isMissingNode)
            .isFalse()
        expectThat(spec.at("/paths/~1api~1v1~1vevote~1proposals~1comments").isMissingNode).isFalse()
    }

    @Test
    fun `project id header is documented on public endpoints`() {
        val parameters = specParameters("/paths/~1api~1v1~1validators/get/parameters")

        val projectIdHeader =
            parameters.firstOrNull {
                it.path("\$ref").asText() == "#/components/parameters/XProjectIdHeader"
            }

        expectThat(projectIdHeader != null).isTrue()
    }

    private fun specParameters(pointer: String): JsonNode = fetchSpec().at(pointer)

    private fun fetchSpec(): JsonNode {
        return objectMapper.readTree(fetchSpecResponse().contentAsString)
    }

    private fun fetchSpecResponse() =
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk).andReturn().response

    private fun findParameter(parameters: JsonNode, name: String, location: String): JsonNode =
        parameters.firstOrNull {
            it.path("name").asText() == name && it.path("in").asText() == location
        } ?: error("Parameter '$name' in '$location' not found")

    private fun contentTypes(content: JsonNode): List<String> =
        content.fieldNames().asSequence().toList()
}
