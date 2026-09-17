package org.vechain.indexer.history

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.config.postgres.PostgresProperties
import org.vechain.indexer.postgres.ConcurrentIndexBuilder

/** Builds the `findActions` indexes V32 leaves to us on a populated table, once liveness is up. */
@Component
@Profile("history")
@ConditionalOnPostgres
class HistoryIndexMaintenance(private val properties: PostgresProperties) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        val thread = Thread(::run, "history-index-builder")
        thread.isDaemon = true
        thread.start()
    }

    private fun run() {
        try {
            ConcurrentIndexBuilder(properties)
                .ensure(HistoryActionIndexes.SCHEMA, HistoryActionIndexes.INDEXES)
        } catch (e: Exception) {
            logger.error(
                "history: building the B3TR_ACTION indexes failed; the next start retries",
                e,
            )
        }
    }
}
