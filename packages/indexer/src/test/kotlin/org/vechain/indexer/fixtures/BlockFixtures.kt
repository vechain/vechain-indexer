package org.vechain.indexer.fixtures

import org.vechain.indexer.utils.JsonUtils
import org.vechain.thor.model.Block

object BlockFixtures {

    private val objectMapper = JsonUtils.mapper

    val BLOCK_3_NO_CLAUSES = buildBlockFixture(3L)
    val BLOCK_4_SINGLE_CLAUSE = buildBlockFixture(4L)
    val BLOCK_5_VIP180_CONTRACTS = buildBlockFixture(5L)
    val BLOCK_6_VIP181_CONTRACTS = buildBlockFixture(6L)
    val BLOCK_8_MULTIPLE_CLAUSES = buildBlockFixture(8L)
    val BLOCK_10_SEMI_FUNGIBLE_TOKENS = buildBlockFixture(10L)
    val BLOCK_14_VET_TRANSFER = buildBlockFixture(14L)
    val BLOCK_16_MASTER_EVENT_UPDATE = buildBlockFixture(16L)
    val BLOCK_17_BATCH_TRANSFERS_1 = buildBlockFixture(17L)
    val BLOCK_18_BATCH_TRANSFERS_2 = buildBlockFixture(18L)
    val BLOCK_19_BATCH_TRANSFERS_3 = buildBlockFixture(19L)
    val BLOCK_42_ERC1155_VIP210_CONTRACTS = buildBlockFixture(42L)

    private fun buildBlockFixture(blockNumber: Long): Block {
        return objectMapper.readValue(
            BlockFixtures::class.java.getResource("/block_${blockNumber}.json")!!.readText(),
            Block::class.java
        )
    }
}
