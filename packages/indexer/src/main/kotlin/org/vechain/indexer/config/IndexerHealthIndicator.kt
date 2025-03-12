package org.vechain.indexer.config

import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Status

@Component
class IndexerHealthIndicator(
    private val indexers: List<BlockIndexer>,
) : HealthIndicator {
    companion object {
        private const val STATUS_UP = "UP"
        private const val STATUS_DOWN = "DOWN"
        private const val PROCESS_TIMEOUT = 60L
    }

    data class IndexerHealth(
        val indexerName: String,
        val status: String,
        val syncStatus: Status,
        val currentBlock: String,
    )

    override fun health(): Health {
        val key = "IndexersHealth"

        val indexerHealths =
            indexers.map { indexer ->
                IndexerHealth(
                    indexerName = indexer.name,
                    status = getIndexerHealth(indexer),
                    syncStatus = indexer.status,
                    currentBlock =
                        NumberFormat.getNumberInstance(Locale.US)
                            .format(indexer.currentBlockNumber ?: 0),
                )
            }

        val badIndexers = indexerHealths.filter { it.status == STATUS_DOWN }

        return if (badIndexers.isNotEmpty()) {
            Health.down().withDetail(key, badIndexers).build()
        } else {
            Health.up().withDetail(key, indexerHealths).build()
        }
    }

    private fun getIndexerHealth(indexer: BlockIndexer): String {
        val timeNow = LocalDateTime.now(ZoneOffset.UTC)

        val timeLastProcessed = indexer.timeLastProcessed
        return if (timeNow.minusSeconds(PROCESS_TIMEOUT) > timeLastProcessed) {
            STATUS_DOWN
        } else {
            STATUS_UP
        }
    }
}
