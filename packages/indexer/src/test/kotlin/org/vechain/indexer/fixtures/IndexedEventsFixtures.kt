package org.vechain.indexer.fixtures

import com.fasterxml.jackson.core.type.TypeReference
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.JsonUtils

object IndexedEventsFixtures {
    private val objectMapper = JsonUtils.mapper

    val INDEXED_EVENTS_TRANSFERS =
        buildIndexedEventsFixture("indexed-events/indexed_events_transfers.json")

    val INDEXED_EVENTS_BLACKLIST =
        buildIndexedEventsFixture("indexed-events/indexed_events_blacklist.json")

    val INDEXED_EVENTS_WHITELIST =
        buildIndexedEventsFixture("indexed-events/indexed_events_whitelist.json")

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
