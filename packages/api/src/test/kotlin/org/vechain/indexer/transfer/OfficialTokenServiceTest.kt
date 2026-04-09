package org.vechain.indexer.transfer

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.config.DetectedNetwork
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.config.VeChainNetwork
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.model.Block

@ExtendWith(MockKExtension::class)
internal class OfficialTokenServiceTest {

    @MockK lateinit var networkDetectionService: NetworkDetectionService

    private lateinit var service: OfficialTokenService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = OfficialTokenService(networkDetectionService)
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

    @Test
    fun `getOfficialTokenAddresses returns token list excluding VTHO for MAINNET`() {
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.MAINNET, mockBlock())

        val result = service.getOfficialTokenAddresses()

        assertTrue(result.isNotEmpty())
        assertTrue(!result.contains(VTHO_CONTRACT_ADDRESS))
    }

    @Test
    fun `getOfficialTokenAddresses returns token list excluding VTHO for TESTNET`() {
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.TESTNET, mockBlock())

        val result = service.getOfficialTokenAddresses()

        assertTrue(result.isNotEmpty())
        assertTrue(!result.contains(VTHO_CONTRACT_ADDRESS))
    }

    @Test
    fun `getNetworkType returns MAINNET when detection succeeds`() {
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.MAINNET, mockBlock())

        val result = service.getNetworkType()

        assertEquals(VeChainNetwork.MAINNET, result)
    }

    @Test
    fun `getNetworkType returns TESTNET when detection succeeds`() {
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.TESTNET, mockBlock())

        val result = service.getNetworkType()

        assertEquals(VeChainNetwork.TESTNET, result)
    }

    @Test
    fun `getNetworkType returns CUSTOM when detection throws exception`() {
        every { networkDetectionService.detectBlocking() } throws RuntimeException("Network error")

        val result = service.getNetworkType()

        assertEquals(VeChainNetwork.CUSTOM, result)
    }

    @Test
    fun `loadTokenRegistry loads MAINNET from local JSON`() {
        val result = service.loadTokenRegistry(VeChainNetwork.MAINNET)

        assertTrue(result.isNotEmpty())
        assertEquals("VTHO", result.first().symbol)
    }

    @Test
    fun `loadTokenRegistry loads TESTNET from local JSON`() {
        val result = service.loadTokenRegistry(VeChainNetwork.TESTNET)

        assertTrue(result.isNotEmpty())
        assertEquals("VTHO", result.first().symbol)
        assertEquals(VTHO_CONTRACT_ADDRESS, result.first().address)
    }

    @Test
    fun `loadTokenRegistry returns empty list for CUSTOM network type`() {
        val result = service.loadTokenRegistry(VeChainNetwork.CUSTOM)

        assertEquals(0, result.size)
    }

    @Test
    fun `filterTokenAddresses excludes VTHO address`() {
        val tokenList =
            listOf(
                createTokenRegistry(VTHO_CONTRACT_ADDRESS, "VTHO"),
                createTokenRegistry("0xabc123", "TOKEN1"),
                createTokenRegistry("0xdef456", "TOKEN2"),
            )

        val result = service.filterTokenAddresses(tokenList)

        assertEquals(2, result.size)
        assertTrue(result.contains("0xabc123"))
        assertTrue(result.contains("0xdef456"))
        assertTrue(!result.contains(VTHO_CONTRACT_ADDRESS))
    }

    @Test
    fun `filterTokenAddresses returns empty list when only VTHO present`() {
        val tokenList = listOf(createTokenRegistry(VTHO_CONTRACT_ADDRESS, "VTHO"))

        val result = service.filterTokenAddresses(tokenList)

        assertEquals(0, result.size)
    }

    @Test
    fun `filterTokenAddresses returns empty list when input is empty`() {
        val result = service.filterTokenAddresses(emptyList())

        assertEquals(0, result.size)
    }

    @Test
    fun `filterTokenAddresses handles multiple tokens correctly`() {
        val tokenList =
            listOf(
                createTokenRegistry(VTHO_CONTRACT_ADDRESS, "VTHO"),
                createTokenRegistry("0xtoken1", "TOKEN1"),
                createTokenRegistry("0xtoken2", "TOKEN2"),
                createTokenRegistry("0xtoken3", "TOKEN3"),
                createTokenRegistry("0xtoken4", "TOKEN4"),
            )

        val result = service.filterTokenAddresses(tokenList)

        assertEquals(4, result.size)
        assertEquals(listOf("0xtoken1", "0xtoken2", "0xtoken3", "0xtoken4"), result)
    }

    @Test
    fun `getTokenRegistryInfoFromJson loads MAINNET tokens successfully`() {
        val result = service.getTokenRegistryInfoFromJson(VeChainNetwork.MAINNET)

        assertTrue(result.isNotEmpty())
        result.forEach { token ->
            assertTrue(token.address.isNotEmpty())
            assertTrue(token.symbol.isNotEmpty())
            assertTrue(token.name.isNotEmpty())
        }
    }

    @Test
    fun `getTokenRegistryInfoFromJson loads TESTNET tokens successfully`() {
        val result = service.getTokenRegistryInfoFromJson(VeChainNetwork.TESTNET)

        assertTrue(result.isNotEmpty())
        assertEquals("VTHO", result.first().symbol)
        assertEquals(VTHO_CONTRACT_ADDRESS, result.first().address)
    }

    @Test
    fun `getTokenRegistryInfoFromJson returns empty list for CUSTOM network`() {
        val result = service.getTokenRegistryInfoFromJson(VeChainNetwork.CUSTOM)

        assertEquals(0, result.size)
    }

    @Test
    fun `validateRegistryOnStartup validates MAINNET registry`() {
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.MAINNET, mockBlock())

        service.validateRegistryOnStartup()
    }

    @Test
    fun `validateRegistryOnStartup validates TESTNET registry`() {
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.TESTNET, mockBlock())

        service.validateRegistryOnStartup()
    }

    @Test
    fun `validateRegistryOnStartup skips CUSTOM network`() {
        every { networkDetectionService.detectBlocking() } returns
            DetectedNetwork(VeChainNetwork.CUSTOM, mockBlock())

        service.validateRegistryOnStartup()
    }

    @Test
    fun `validateRegistryOnStartup throws when local registry cannot be loaded`() {
        val failingService =
            object : OfficialTokenService(networkDetectionService) {
                override fun getNetworkType(): VeChainNetwork = VeChainNetwork.MAINNET

                override fun getTokenRegistryInfoFromJson(
                    network: VeChainNetwork
                ): List<TokenRegistry> {
                    throw IllegalArgumentException("broken json")
                }
            }

        val exception =
            assertThrows(IllegalStateException::class.java) {
                failingService.validateRegistryOnStartup()
            }

        assertTrue(exception.message!!.contains("Failed to load local token registry"))
    }

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
}
