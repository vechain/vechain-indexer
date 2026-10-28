package org.vechain.indexer.config.metrics

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerRunner
import org.vechain.indexer.Status
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision

/** The alert rules and dashboards query these names, so they are pinned here. */
class IndexerCoreMetricsTest {

    @Test
    fun `indexer-core meters render under the names the alerts and dashboards query`() =
        runBlocking<Unit> {
            val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            val thorClient = mockk<ThorClient>()
            coEvery { thorClient.waitForBlock(any<BlockRevision>()) } coAnswers
                {
                    awaitCancellation()
                }

            var status = Status.NOT_INITIALISED
            val indexer = mockk<Indexer>(relaxed = true)
            every { indexer.name } returns "TransferIndexer"
            every { indexer.dependsOn } returns null
            every { indexer.getInspectionClauses() } returns null
            every { indexer.getCurrentBlockNumber() } returns 7L
            every { indexer.getStatus() } answers { status }
            every { indexer.initialise() } answers { status = Status.READY_TO_SYNC }

            val job =
                IndexerRunner.launch(
                    scope = this,
                    thorClient = thorClient,
                    indexers = listOf(indexer),
                    meterRegistry = registry,
                )
            withTimeout(5.seconds) {
                while ("indexer_runner_proximity_groups 1.0" !in registry.scrape()) {
                    delay(10.milliseconds)
                }
            }
            job.cancelAndJoin()

            val tag = "indexer_name=\"TransferIndexer\""
            assertThat(registry.scrape())
                .contains("indexer_current_block{$tag} 7.0")
                .contains("indexer_sync_status{$tag,status=\"READY_TO_SYNC\"} 1.0")
                .contains("indexer_blocks_processed_total{$tag}")
                .contains("indexer_runner_phase{phase=\"steady\"} 1.0")
                .contains("indexer_runner_proximity_group{$tag} 1.0")
                .contains("indexer_runner_catch_up_group{$tag} 0.0")
                .contains("indexer_runner_regroups_total{kind=\"proximity\"} 1.0")
        }
}
