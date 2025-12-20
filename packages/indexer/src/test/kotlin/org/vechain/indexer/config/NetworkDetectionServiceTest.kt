package org.vechain.indexer.config

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision

@ExtendWith(MockKExtension::class)
internal class NetworkDetectionServiceTest {
    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: NetworkDetectionService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = NetworkDetectionService(thorClient)
    }

    @Test
    fun `detect returns MAINNET for known mainnet genesis block id`() = runBlocking {
        val block = genesisBlock(id = NetworkDetectionService.MAINNET_GENESIS_BLOCK_ID)
        coEvery { thorClient.getBlock(BlockRevision.Number(0)) } returns block

        val detected = service.detect()
        assertEquals(VeChainNetwork.MAINNET, detected.network)
        assertEquals(block, detected.genesisBlock)
    }

    @Test
    fun `detect returns TESTNET for known testnet genesis block id`() = runBlocking {
        val block = genesisBlock(id = NetworkDetectionService.TESTNET_GENESIS_BLOCK_ID)
        coEvery { thorClient.getBlock(BlockRevision.Number(0)) } returns block

        val detected = service.detect()
        assertEquals(VeChainNetwork.TESTNET, detected.network)
        assertEquals(block, detected.genesisBlock)
    }

    @Test
    fun `detect returns CUSTOM for unknown genesis block id`() = runBlocking {
        val block = genesisBlock(id = "0x" + "1".repeat(64))
        coEvery { thorClient.getBlock(BlockRevision.Number(0)) } returns block

        val detected = service.detect()
        assertEquals(VeChainNetwork.CUSTOM, detected.network)
        assertEquals(block, detected.genesisBlock)
    }

    private fun genesisBlock(id: String): Block =
        Block(
            id = id,
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
}
