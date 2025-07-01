package org.vechain.indexer.fixtures

import com.fasterxml.jackson.core.type.TypeReference
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.utils.JsonUtils

object LogsFixtures {
    private val objectMapper = JsonUtils.mapper

    val LOGS_VIP180_CONTRACTS = buildLogsFixture("logs/logs_vip180_contracts.json")
    val LOGS_NFT_MINT_2 = buildLogsFixture("logs/logs_nft_mint_2.json")
    val LOGS_MULTIPLE_TXS = buildLogsFixture("logs/logs_multiple_txs.json")
    val LOGS_VEVOTE_COMMENTS = buildLogsFixture("logs/logs_vevote_comments.json")
    val LOGS_VEVOTE_RESULTS = buildLogsFixture("logs/logs_vevote_results.json")
    val LOGS_SEMI_FUNGIBLE_TOKENS = buildLogsFixture("logs/logs_semi_fungible_tokens.json")
    val LOGS_VET_TRANSFER_EVENTS = buildLogsFixture("logs/logs_vet_events.json")
    val LOGS_BATCH_TRANSFERS = buildLogsFixture("logs/logs_batch_transfers.json")
    val LOGS_BLACKLIST = buildLogsFixture("logs/logs_blacklist.json")
    val LOGS_AUTHORITY_NODE = buildLogsFixture("logs/logs_authority_node.json")

    private fun buildLogsFixture(name: String): List<EventLog> {
        val resource =
            LogsFixtures::class.java.classLoader.getResource(name)
                ?: throw IllegalStateException("Resource not found: $name")

        return resource.openStream().use { inputStream ->
            objectMapper.readValue(inputStream, object : TypeReference<List<EventLog>>() {})
        }
    }
}
