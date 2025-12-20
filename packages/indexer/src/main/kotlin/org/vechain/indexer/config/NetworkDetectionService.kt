package org.vechain.indexer.config

import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision

enum class VeChainNetwork {
    MAINNET,
    TESTNET,
    CUSTOM,
}

data class DetectedNetwork(val network: VeChainNetwork, val genesisBlock: Block)

@Service
open class NetworkDetectionService(private val thorClient: ThorClient) {
    companion object {
        const val MAINNET_GENESIS_BLOCK_ID =
            "0x00000000851caf3cfdb6e899cf5958bfb1ac3413d346d43539627e6be7ec1b4a"
        const val TESTNET_GENESIS_BLOCK_ID =
            "0x000000000b2bce3c70bc649a02749e8687721b09ed2e15997f466536b20bb127"
    }

    open suspend fun detect(): DetectedNetwork {
        val genesisBlock = thorClient.getBlock(BlockRevision.Number(0))
        check(genesisBlock.number == 0L) {
            "Expected genesis block number 0 but got ${genesisBlock.number}"
        }

        val network =
            when (genesisBlock.id.lowercase()) {
                MAINNET_GENESIS_BLOCK_ID -> VeChainNetwork.MAINNET
                TESTNET_GENESIS_BLOCK_ID -> VeChainNetwork.TESTNET
                else -> VeChainNetwork.CUSTOM
            }

        return DetectedNetwork(network = network, genesisBlock = genesisBlock)
    }

    open fun detectBlocking(): DetectedNetwork = runBlocking { detect() }
}
