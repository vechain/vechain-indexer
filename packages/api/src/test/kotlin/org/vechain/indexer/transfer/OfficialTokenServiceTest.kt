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
import org.vechain.indexer.config.DetectedNetwork
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.model.Block
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
internal class OfficialTokenServiceTest {

    @MockK lateinit var networkDetectionService: NetworkDetectionService
    @MockK lateinit var officialTokenRepoRest: WebClient
    @MockK lateinit var requestHeadersUriSpec: WebClient.RequestHeadersUriSpec<*>
    @MockK lateinit var responseSpec: WebClient.ResponseSpec

    private lateinit var service: OfficialTokenService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = OfficialTokenService(networkDetectionService, officialTokenRepoRest)
    }

    private fun mockBlock(): Block =
        Block(
            id = "0x00000000851caf3cfdb6e899cf5958bfb1ac3413d346d43539627e6be7ec1b4a",
            number = 0L,
            timestamp = 1530316800L,
            parentID = "0x" + "0".repeat(64),
            size = 0,
            gasLimit = 0,
            baseFeePerGas = null,
            beneficiary = "0x" + "0".repeat(40),
            gasUsed = 0,
            totalScore = 0,
            txsRoot = "0x" + "0".repeat(64),
            txsFeatures = 0,
            stateRoot = "0x" + "0".repeat(64),
            receiptsRoot = "0x" + "0".repeat(64),
            signer = "0x" + "0".repeat(40),
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
            com = false,
        )

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

        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.MAINNET, mockBlock())
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

        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.MAINNET, mockBlock())
        mockWebClientResponse(tokenList)

        // When
        val result = service.getOfficialTokenAddresses()

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `getOfficialTokenAddresses returns empty list when token list is empty`() {
        // Given
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.MAINNET, mockBlock())
        mockWebClientResponse(emptyList())

        // When
        val result = service.getOfficialTokenAddresses()

        // Then
        assertEquals(0, result.size)
    }

    // Test getNetworkType
    @Test
    fun `getNetworkType returns MAINNET when detection succeeds`() {
        // Given
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.MAINNET, mockBlock())

        // When
        val result = service.getNetworkType()

        // Then
        assertEquals(VeChainNetwork.MAINNET, result)
    }

    @Test
    fun `getNetworkType returns TESTNET when detection succeeds`() {
        // Given
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.TESTNET, mockBlock())

        // When
        val result = service.getNetworkType()

        // Then
        assertEquals(VeChainNetwork.TESTNET, result)
    }

    @Test
    fun `getNetworkType returns CUSTOM when detection throws exception`() {
        // Given
        every { networkDetectionService.detectBlocking() } throws RuntimeException("Network error")

        // When
        val result = service.getNetworkType()

        // Then
        assertEquals(VeChainNetwork.CUSTOM, result)
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
        val result = service.loadTokenRegistry(VeChainNetwork.MAINNET)

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
        val result = service.loadTokenRegistry(VeChainNetwork.TESTNET)

        // Then
        // The test network JSON file should be loaded
        assertTrue(result.isNotEmpty())
        // Verify first token is VTHO from test.json
        assertEquals("VTHO", result.first().symbol)
        assertEquals(VTHO_CONTRACT_ADDRESS, result.first().address)
    }

    @Test
    fun `loadTokenRegistry returns empty list for CUSTOM network type`() {
        // Given
        mockWebClientError()

        // When
        val result = service.loadTokenRegistry(VeChainNetwork.CUSTOM)

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
    fun `getTokenRegistryInfoFromJson loads MAINNET tokens successfully`() {
        // When
        val result = service.getTokenRegistryInfoFromJson(VeChainNetwork.MAINNET)

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
    fun `getTokenRegistryInfoFromJson loads TESTNET tokens successfully`() {
        // When
        val result = service.getTokenRegistryInfoFromJson(VeChainNetwork.TESTNET)

        // Then
        assertTrue(result.isNotEmpty())
        // Verify first token is VTHO
        assertEquals("VTHO", result.first().symbol)
        assertEquals(VTHO_CONTRACT_ADDRESS, result.first().address)
    }

    @Test
    fun `getTokenRegistryInfoFromJson returns empty list for CUSTOM network`() {
        // When
        val result = service.getTokenRegistryInfoFromJson(VeChainNetwork.CUSTOM)

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
        val result = service.getTokenRegistryInfoFromApi(VeChainNetwork.MAINNET)

        // Then
        assertEquals(2, result.size)
        assertEquals("TOKEN1", result[0].symbol)
        assertEquals("TOKEN2", result[1].symbol)
        verify(exactly = 1) { officialTokenRepoRest.get() }
    }

    @Test
    fun `getTokenRegistryInfoFromApi returns empty list for CUSTOM network`() {
        // When
        val result = service.getTokenRegistryInfoFromApi(VeChainNetwork.CUSTOM)

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
                service.getTokenRegistryInfoFromApi(VeChainNetwork.MAINNET)
            }

        assertTrue(exception.message!!.contains("Call to token registry API failed"))
    }

    @Test
    fun `getTokenRegistryInfoFromApi throws exception when API call fails`() {
        // Given
        mockWebClientError()

        // When/Then
        assertThrows(RuntimeException::class.java) {
            service.getTokenRegistryInfoFromApi(VeChainNetwork.MAINNET)
        }
    }

    // Integration test - Full flow with API failure and JSON fallback
    @Test
    fun `full flow - API fails and falls back to JSON for TEST network`() {
        // Given
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.TESTNET, mockBlock())
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
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.CUSTOM, mockBlock())
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
