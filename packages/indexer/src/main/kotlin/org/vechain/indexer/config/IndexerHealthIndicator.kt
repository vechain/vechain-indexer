package org.vechain.indexer.config

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.vechain.indexer.Indexer

@Component
class IndexerHealthIndicator(private val indexers: List<Indexer>) : HealthIndicator {

    companion object {
        private const val STATUS_UP = "UP"
        private const val STATUS_DOWN = "DOWN"
        private const val PROCESS_TIMEOUT = 60L
    }

    data class IndexerHealth(val indexerName: String, val status: String, val syncStatus: Any)

    override fun health(): Health {
        val key = "IndexersHealth"

        val indexerHealths =
            indexers.map { indexer ->
                IndexerHealth(
                    indexerName = indexer.name,
                    status = getIndexerHealth(indexer),
                    syncStatus = indexer.status
                )
            }

        val badIndexers = indexerHealths.filter { it.status == STATUS_DOWN }

        return if (badIndexers.isNotEmpty()) {
            Health.down().withDetail(key, badIndexers).build()
        } else {
            Health.up().withDetail(key, indexerHealths).build()
        }
    }

    private fun getIndexerHealth(indexer: Indexer): String {
        val timeNow = LocalDateTime.now(ZoneOffset.UTC)

        val timeLastProcessed = indexer.timeLastProcessed
        return if (timeNow.minusSeconds(PROCESS_TIMEOUT) > timeLastProcessed) {
            STATUS_DOWN
        } else {
            STATUS_UP
        }
    }
}
