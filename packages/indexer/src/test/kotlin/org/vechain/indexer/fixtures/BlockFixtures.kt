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
    val BLOCK_RANDOM_TX = buildBlockFixture("random_tx")
    val BLOCK_SEMI_FUNGIBLE_TOKENS = buildBlockFixture("semi_fungible_tokens")
    val BLOCK_SEMI_FUNGIBLE_TOKENS_2 = buildBlockFixture("semi_fungible_tokens_2")
    val BLOCK_VET_TRANSFER = buildBlockFixture("vet_transfer")
    val BLOCK_MASTER_EVENT_UPDATE = buildBlockFixture("master_event_update")
    val BLOCK_B3TR_ACTION = buildBlockFixture("b3tr_action")
    val BLOCK_BATCH_TRANSFERS_1 = buildBlockFixture("batch_transfers_1")
    val BLOCK_BATCH_TRANSFERS_2 = buildBlockFixture("batch_transfers_2")
    val BLOCK_BATCH_TRANSFERS_3 = buildBlockFixture("batch_transfers_3")
    val BLOCK_ERC1155_VIP210_CONTRACTS = buildBlockFixture("erc1155_vip210_contracts")
    val BLOCK_NFT_MINT = buildBlockFixture("nft_mint")
    val BLOCK_NFT_MINT_2 = buildBlockFixture("nft_mint_2")
    val BLOCK_NFT_MINT_REVERTED = buildBlockFixture("nft_mint_reverted")
    val BLOCK_DEX = buildBlockFixture("dex")
    val BLOCK_MP_SALES = buildBlockFixture("marketplace_sales")
    val BLOCK_STARGATE_STAKE = buildBlockFixture("stargate_stake")
    val BLOCK_STARGATE_UNSTAKE = buildBlockFixture("stargate_unstake")
    val BLOCK_STARGATE_BASE_REWARD = buildBlockFixture("stargate_claim_base_reward")
    val BLOCK_STARGATE_DELEGATE_REWARD = buildBlockFixture("stargate_claim_delegate_reward")
    val BLOCK_STARGATE_STAKE_DELEGATE = buildBlockFixture("stargate_stake_delegate")
    val BLOCK_STARGATE_UNDELEGATE = buildBlockFixture("stargate_undelegate")

    private fun buildBlockFixture(name: String): Block =
        objectMapper.readValue(
            BlockFixtures::class.java.getResource("/block_$name.json")!!.readText(),
            Block::class.java,
        )
}
