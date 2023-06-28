package org.vechain.indexer.config

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.stereotype.Component
import org.vechain.indexer.Indexer

@Component
class IndexerHealthIndicator(private val indexers: List<Indexer>) : HealthIndicator {

    data class IndexerHealth(val indexerName: String, val status: Status)

    companion object {
        const val PROCESS_TIMEOUT = 60L
    }

    override fun health(): Health {
        val key = "IndexersHealth"

        val indexerHealths =
            indexers.map { indexer -> IndexerHealth(indexer.name, getIndexerHealth(indexer)) }

        val badIndexers = indexerHealths.filter { it.status == Status.DOWN }

        return if (badIndexers.isNotEmpty()) {
            Health.down().withDetail(key, badIndexers).build()
        } else {
            Health.up().withDetail(key, indexerHealths).build()
        }
    }

    private fun getIndexerHealth(indexer: Indexer): Status {
        val timeNow = LocalDateTime.now(ZoneOffset.UTC)

        val timeLastProcessed = indexer.timeLastProcessed
        return if (timeNow.minusSeconds(PROCESS_TIMEOUT) > timeLastProcessed) {
            Status.DOWN
        } else {
            Status.UP
        }
    }
}
