package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.b3tr.shared.EntityType

/**
 * The newest row of each key the indexer has written, so an entry reads back only the keys it has
 * not touched itself. Written to once the rows are committed and emptied on rollback, so it never
 * holds a row the schema does not.
 */
@Profile("b3tr", "b3tr-actions")
@Component
open class ActionLedgerCache(
    @Value("\${indexer.b3tr-action.ledger-cache-entries:100000}") private val maxEntries: Int
) {
    private val entities = lru<Triple<ActionPeriod, EntityType, String>, EntityActionSummary>()
    private val appUsers = lru<Triple<ActionPeriod, String, String>, AppUserActionSummary>()

    open fun entity(period: ActionPeriod, key: Pair<EntityType, String>): EntityActionSummary? =
        entities[Triple(period, key.first, key.second)]

    open fun appUser(period: ActionPeriod, key: Pair<String, String>): AppUserActionSummary? =
        appUsers[Triple(period, key.first, key.second)]

    /** The last row of a key in [update] is its newest: they are built in block order. */
    open fun remember(update: ActionSummaryUpdate) {
        update.entities.forEach { entities[Triple(it.period, it.entityType, it.entity)] = it }
        update.appUsers.forEach { appUsers[Triple(it.period, it.appId, it.user)] = it }
    }

    open fun clear() {
        entities.clear()
        appUsers.clear()
    }

    private fun <K, V> lru(): MutableMap<K, V> =
        object : LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) =
                size > maxEntries
        }

    private companion object {
        const val INITIAL_CAPACITY = 1024
        const val LOAD_FACTOR = 0.75f
    }
}
