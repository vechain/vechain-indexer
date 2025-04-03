package org.vechain.indexer.fixtures

import com.fasterxml.jackson.core.type.TypeReference
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.JsonUtils

object IndexedEventsFixtures {
    private val objectMapper = JsonUtils.mapper

    val INDEXED_EVENTS_BLACKLIST =
        buildIndexedEventsFixture("indexed-events/indexed_events_blacklist.json")

    val INDEXED_EVENTS_DEBLACKLIST =
        buildIndexedEventsFixture("indexed-events/indexed_events_deblacklist.json")

    val INDEXED_EVENTS_BLACKLIST_MISSING_NFT_PARAM =
        buildIndexedEventsFixture("indexed-events/indexed_events_blacklist_missing_nft_param.json")

    val INDEXED_EVENTS_BLACKLIST_MISSING_ISBLACKLISTED_PARAM =
        buildIndexedEventsFixture(
            "indexed-events/indexed_events_blacklist_missing_isblacklisted_param.json"
        )

    val INDEXED_EVENTS_BLACKLIST_DUPLICATE =
        buildIndexedEventsFixture("indexed-events/indexed_events_blacklist_duplicate.json")

    private fun buildIndexedEventsFixture(name: String): List<IndexedEvent> {
        val resource =
            IndexedEventsFixtures::class.java.classLoader.getResource(name)
                ?: throw IllegalStateException("Resource not found: $name")

        return resource.openStream().use { inputStream ->
            objectMapper.readValue(inputStream, object : TypeReference<List<IndexedEvent>>() {})
        }
    }
}
