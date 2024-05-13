package org.vechain.indexer.fixtures

import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.JsonUtils

object BlockFixtures {

    private val objectMapper = JsonUtils.mapper

    val BLOCK_NO_CLAUSES = buildBlockFixture("no_clauses")
    val BLOCK_SINGLE_CLAUSE = buildBlockFixture("single_clause")
    val BLOCK_VIP180_CONTRACTS = buildBlockFixture("vip180_contracts")
    val BLOCK_VIP181_CONTRACTS = buildBlockFixture("vip181_contracts")
    val BLOCK_MULTIPLE_TXS = buildBlockFixture("multiple_txs")
    val BLOCK_SEMI_FUNGIBLE_TOKENS = buildBlockFixture("semi_fungible_tokens")
    val BLOCK_VET_TRANSFER = buildBlockFixture("vet_transfer")
    val BLOCK_MASTER_EVENT_UPDATE = buildBlockFixture("master_event_update")
    val BLOCK_BATCH_TRANSFERS_1 = buildBlockFixture("batch_transfers_1")
    val BLOCK_BATCH_TRANSFERS_2 = buildBlockFixture("batch_transfers_2")
    val BLOCK_BATCH_TRANSFERS_3 = buildBlockFixture("batch_transfers_3")
    val BLOCK_ERC1155_VIP210_CONTRACTS = buildBlockFixture("erc1155_vip210_contracts")
    val BLOCK_NFT_MINT = buildBlockFixture("nft_mint")
    val BLOCK_NFT_MINT_2 = buildBlockFixture("nft_mint_2")
    val BLOCK_NFT_MINT_REVERTED = buildBlockFixture("nft_mint_reverted")

    private fun buildBlockFixture(name: String): Block {
        return objectMapper.readValue(
            BlockFixtures::class.java.getResource("/block_${name}.json")!!.readText(),
            Block::class.java
        )
    }
}
