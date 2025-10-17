package org.vechain.indexer.transfer

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.client.ThorClient
import org.vechain.indexer.config.NetworkType
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
internal class OfficialTokenServiceTest {

    @MockK lateinit var thorClient: ThorClient
    @MockK lateinit var officialTokenRepoRest: WebClient
    @MockK lateinit var requestHeadersUriSpec: WebClient.RequestHeadersUriSpec<*>
    @MockK lateinit var responseSpec: WebClient.ResponseSpec

    private lateinit var service: OfficialTokenService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = OfficialTokenService(thorClient, officialTokenRepoRest)
    }

    // Test getOfficialTokenAddresses - Happy path
    @Test
    fun `getOfficialTokenAddresses returns token list excluding VTHO when API succeeds`() {
        // Given
        val tokenList =
            listOf(
                createTokenRegistry(VTHO_CONTRACT_ADDRESS, "VTHO"),
                createTokenRegistry("0xabc123", "TOKEN1"),
                createTokenRegistry("0xdef456", "TOKEN2"),
            )

        every { thorClient.getNetworkType() } returns NetworkType.MAIN
        mockWebClientResponse(tokenList)

        // When
        val result = service.getOfficialTokenAddresses()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains("0xabc123"))
        assertTrue(result.contains("0xdef456"))
        assertTrue(!result.contains(VTHO_CONTRACT_ADDRESS))
    }

    @Test
    fun `getOfficialTokenAddresses returns empty list when no tokens except VTHO`() {
        // Given
        val tokenList = listOf(createTokenRegistry(VTHO_CONTRACT_ADDRESS, "VTHO"))

        every { thorClient.getNetworkType() } returns NetworkType.MAIN
        mockWebClientResponse(tokenList)

        // When
        val result = service.getOfficialTokenAddresses()

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `getOfficialTokenAddresses returns empty list when token list is empty`() {
        // Given
        every { thorClient.getNetworkType() } returns NetworkType.MAIN
        mockWebClientResponse(emptyList())

        // When
        val result = service.getOfficialTokenAddresses()

        // Then
        assertEquals(0, result.size)
    }

    // Test getNetworkType
    @Test
    fun `getNetworkType returns MAIN when thorClient succeeds`() {
        // Given
        every { thorClient.getNetworkType() } returns NetworkType.MAIN

        // When
        val result = service.getNetworkType()

        // Then
        assertEquals(NetworkType.MAIN, result)
    }

    @Test
    fun `getNetworkType returns TEST when thorClient succeeds`() {
        // Given
        every { thorClient.getNetworkType() } returns NetworkType.TEST

        // When
        val result = service.getNetworkType()

        // Then
        assertEquals(NetworkType.TEST, result)
    }

    @Test
    fun `getNetworkType returns OTHER when thorClient throws exception`() {
        // Given
        every { thorClient.getNetworkType() } throws RuntimeException("Network error")

        // When
        val result = service.getNetworkType()

        // Then
        assertEquals(NetworkType.OTHER, result)
    }

    // Test loadTokenRegistry
    @Test
    fun `loadTokenRegistry loads from API when API call succeeds`() {
        // Given
        val tokenList =
            listOf(
                createTokenRegistry("0xabc123", "TOKEN1"),
                createTokenRegistry("0xdef456", "TOKEN2"),
            )

        mockWebClientResponse(tokenList)

        // When
        val result = service.loadTokenRegistry(NetworkType.MAIN)

        // Then
        assertEquals(2, result.size)
        assertEquals("TOKEN1", result[0].symbol)
        assertEquals("TOKEN2", result[1].symbol)
    }

    @Test
    fun `loadTokenRegistry falls back to JSON when API call fails`() {
        // Given
        mockWebClientError()

        // When
        val result = service.loadTokenRegistry(NetworkType.TEST)

        // Then
        // The test network JSON file should be loaded
        assertTrue(result.isNotEmpty())
        // Verify first token is VTHO from test.json
        assertEquals("VTHO", result.first().symbol)
        assertEquals(VTHO_CONTRACT_ADDRESS, result.first().address)
    }

    @Test
    fun `loadTokenRegistry returns empty list for OTHER network type`() {
        // Given
        mockWebClientError()

        // When
        val result = service.loadTokenRegistry(NetworkType.OTHER)

        // Then
        assertEquals(0, result.size)
    }

    // Test filterTokenAddresses
    @Test
    fun `filterTokenAddresses excludes VTHO address`() {
        // Given
        val tokenList =
            listOf(
                createTokenRegistry(VTHO_CONTRACT_ADDRESS, "VTHO"),
                createTokenRegistry("0xabc123", "TOKEN1"),
                createTokenRegistry("0xdef456", "TOKEN2"),
            )

        // When
        val result = service.filterTokenAddresses(tokenList)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains("0xabc123"))
        assertTrue(result.contains("0xdef456"))
        assertTrue(!result.contains(VTHO_CONTRACT_ADDRESS))
    }

    @Test
    fun `filterTokenAddresses returns empty list when only VTHO present`() {
        // Given
        val tokenList = listOf(createTokenRegistry(VTHO_CONTRACT_ADDRESS, "VTHO"))

        // When
        val result = service.filterTokenAddresses(tokenList)

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `filterTokenAddresses returns empty list when input is empty`() {
        // When
        val result = service.filterTokenAddresses(emptyList())

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `filterTokenAddresses handles multiple tokens correctly`() {
        // Given
        val tokenList =
            listOf(
                createTokenRegistry(VTHO_CONTRACT_ADDRESS, "VTHO"),
                createTokenRegistry("0xtoken1", "TOKEN1"),
                createTokenRegistry("0xtoken2", "TOKEN2"),
                createTokenRegistry("0xtoken3", "TOKEN3"),
                createTokenRegistry("0xtoken4", "TOKEN4"),
            )

        // When
        val result = service.filterTokenAddresses(tokenList)

        // Then
        assertEquals(4, result.size)
        assertEquals(listOf("0xtoken1", "0xtoken2", "0xtoken3", "0xtoken4"), result)
    }

    // Test getTokenRegistryInfoFromJson
    @Test
    fun `getTokenRegistryInfoFromJson loads MAIN network tokens successfully`() {
        // When
        val result = service.getTokenRegistryInfoFromJson(NetworkType.MAIN)

        // Then
        assertTrue(result.isNotEmpty())
        // All tokens should have required fields
        result.forEach { token ->
            assertTrue(token.address.isNotEmpty())
            assertTrue(token.symbol.isNotEmpty())
            assertTrue(token.name.isNotEmpty())
        }
    }

    @Test
    fun `getTokenRegistryInfoFromJson loads TEST network tokens successfully`() {
        // When
        val result = service.getTokenRegistryInfoFromJson(NetworkType.TEST)

        // Then
        assertTrue(result.isNotEmpty())
        // Verify first token is VTHO
        assertEquals("VTHO", result.first().symbol)
        assertEquals(VTHO_CONTRACT_ADDRESS, result.first().address)
    }

    @Test
    fun `getTokenRegistryInfoFromJson returns empty list for OTHER network`() {
        // When
        val result = service.getTokenRegistryInfoFromJson(NetworkType.OTHER)

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `getTokenRegistryInfoFromJson returns empty list for SOLO network`() {
        // When
        val result = service.getTokenRegistryInfoFromJson(NetworkType.SOLO)

        // Then
        assertEquals(0, result.size)
    }

    // Test getTokenRegistryInfoFromApi
    @Test
    fun `getTokenRegistryInfoFromApi loads tokens from API successfully`() {
        // Given
        val tokenList =
            listOf(
                createTokenRegistry("0xabc123", "TOKEN1"),
                createTokenRegistry("0xdef456", "TOKEN2"),
            )

        mockWebClientResponse(tokenList)

        // When
        val result = service.getTokenRegistryInfoFromApi(NetworkType.MAIN)

        // Then
        assertEquals(2, result.size)
        assertEquals("TOKEN1", result[0].symbol)
        assertEquals("TOKEN2", result[1].symbol)
        verify(exactly = 1) { officialTokenRepoRest.get() }
    }

    @Test
    fun `getTokenRegistryInfoFromApi returns empty list for OTHER network`() {
        // When
        val result = service.getTokenRegistryInfoFromApi(NetworkType.OTHER)

        // Then
        assertEquals(0, result.size)
        verify(exactly = 0) { officialTokenRepoRest.get() }
    }

    @Test
    fun `getTokenRegistryInfoFromApi returns empty list for SOLO network`() {
        // When
        val result = service.getTokenRegistryInfoFromApi(NetworkType.SOLO)

        // Then
        assertEquals(0, result.size)
        verify(exactly = 0) { officialTokenRepoRest.get() }
    }

    @Test
    fun `getTokenRegistryInfoFromApi throws exception when API returns null`() {
        // Given
        every { officialTokenRepoRest.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri("/main.json") } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every {
            responseSpec.bodyToMono(any<ParameterizedTypeReference<List<TokenRegistry>>>())
        } returns Mono.empty()

        // When/Then
        val exception =
            assertThrows(Exception::class.java) {
                service.getTokenRegistryInfoFromApi(NetworkType.MAIN)
            }

        assertTrue(exception.message!!.contains("Call to token registry API failed"))
    }

    @Test
    fun `getTokenRegistryInfoFromApi throws exception when API call fails`() {
        // Given
        mockWebClientError()

        // When/Then
        assertThrows(RuntimeException::class.java) {
            service.getTokenRegistryInfoFromApi(NetworkType.MAIN)
        }
    }

    // Integration test - Full flow with API failure and JSON fallback
    @Test
    fun `full flow - API fails and falls back to JSON for TEST network`() {
        // Given
        every { thorClient.getNetworkType() } returns NetworkType.TEST
        mockWebClientError()

        // When
        val result = service.getOfficialTokenAddresses()

        // Then
        assertTrue(result.isNotEmpty())
        // VTHO should be filtered out
        assertTrue(!result.contains(VTHO_CONTRACT_ADDRESS))
    }

    @Test
    fun `full flow - returns empty list when network is OTHER and JSON fallback fails`() {
        // Given
        every { thorClient.getNetworkType() } returns NetworkType.OTHER
        mockWebClientError()

        // When
        val result = service.getOfficialTokenAddresses()

        // Then
        assertEquals(0, result.size)
    }

    // Helper methods
    private fun createTokenRegistry(address: String, symbol: String): TokenRegistry {
        return TokenRegistry(
            name = "Test $symbol",
            symbol = symbol,
            decimals = 18,
            address = address,
            desc = "Test token",
            icon = "test.png",
            totalSupply = "1000000",
        )
    }

    private fun mockWebClientResponse(tokenList: List<TokenRegistry>) {
        every { officialTokenRepoRest.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every {
            responseSpec.bodyToMono(any<ParameterizedTypeReference<List<TokenRegistry>>>())
        } returns Mono.just(tokenList)
    }

    private fun mockWebClientError() {
        every { officialTokenRepoRest.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every {
            responseSpec.bodyToMono(any<ParameterizedTypeReference<List<TokenRegistry>>>())
        } throws RuntimeException("API call failed")
    }
}
