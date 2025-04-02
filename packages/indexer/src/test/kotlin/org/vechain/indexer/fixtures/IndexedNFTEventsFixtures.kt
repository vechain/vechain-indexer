package org.vechain.indexer.fixtures

import com.fasterxml.jackson.core.type.TypeReference
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.JsonUtils

object IndexedNFTEventsFixtures {
    private val objectMapper = JsonUtils.mapper

    val INDEXED_EVENTS_NFT_TRANSFER =
        buildIndexedEventsFixture("indexed-events/indexed_events_nft_transfer.json")

    val INDEXED_EVENTS_NFT_TRANSFER_MISSING_TO_PARAM =
        buildIndexedEventsFixture(
            "indexed-events/indexed_events_nft_transfer_missing_to_param.json"
        )

    val INDEXED_EVENTS_NFT_TRANSFER_MISSING_TOKEN_ID_PARAM =
        buildIndexedEventsFixture(
            "indexed-events/indexed_events_nft_transfer_missing_token_id_param.json"
        )

    val INDEXED_EVENTS_NFT_TRANSFER_DUPLICATE =
        buildIndexedEventsFixture("indexed-events/indexed_events_nft_transfer_duplicate.json")

    private fun buildIndexedEventsFixture(name: String): List<IndexedEvent> {
        val resource =
            IndexedNFTEventsFixtures::class.java.classLoader.getResource(name)
                ?: throw IllegalStateException("Resource not found: $name")

        return resource.openStream().use { inputStream ->
            objectMapper.readValue(inputStream, object : TypeReference<List<IndexedEvent>>() {})
        }
    }
}
