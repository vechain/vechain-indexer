package org.vechain.indexer.client

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.vechain.indexer.exception.NotFoundException
import org.vechain.indexer.model.NetworkType
import org.vechain.indexer.thor.model.Block

@Service
class ThorClient(private val thorRest: WebClient) {

    private val projectIdHeader = "X-Project-Id"
    private val projectIdVal = "veworld-indexer"

    fun getBestBlock(expanded: Boolean = true): Block {
        return thorRest
            .get()
            .uri("/blocks/best?expanded=$expanded")
            .header(projectIdHeader, projectIdVal)
            .retrieve()
            .bodyToMono(Block::class.java)
            .block() ?: throw NotFoundException("Best block not found")
    }

    fun getFinalizedBlock(expanded: Boolean = true): Block {
        return thorRest
            .get()
            .uri("/blocks/finalized?expanded=$expanded")
            .header(projectIdHeader, projectIdVal)
            .retrieve()
            .bodyToMono(Block::class.java)
            .block() ?: throw NotFoundException("Finalized block not found")
    }

    fun getBlock(blockNumber: Long, expanded: Boolean = true): Block {
        return thorRest
            .get()
            .uri("/blocks/${blockNumber}?expanded=$expanded")
            .header(projectIdHeader, projectIdVal)
            .retrieve()
            .bodyToMono(Block::class.java)
            .block() ?: throw NotFoundException("Block not found for block number $blockNumber")
    }

    fun getBlockById(blockId: String, expanded: Boolean = true): Block {
        return thorRest
            .get()
            .uri("/blocks/${blockId}?expanded=$expanded")
            .header(projectIdHeader, projectIdVal)
            .retrieve()
            .bodyToMono(Block::class.java)
            .block() ?: throw NotFoundException("Block not found for block id $blockId")
    }

    fun getNetworkType(): NetworkType {
        val genesis = getBlock(0, false)
        return NetworkType.fromGenesisId(genesis.id)
    }
}
