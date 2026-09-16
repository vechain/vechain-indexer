package org.vechain.indexer.config

import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.HealthContributorNameFactory
import org.springframework.boot.actuate.health.Status.DOWN
import org.springframework.boot.actuate.health.Status.UP
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status

class IndexerLivenessHealthIndicatorTest {

    private val stallThreshold = 1800L
    private val bootstrapState = IndexBootstrapState()

    private fun indicator(vararg indexers: Indexer) =
        IndexerLivenessHealthIndicator(indexers.toList(), bootstrapState, stallThreshold)

    private fun blockIndexer(status: Status, secondsSinceProgress: Long) =
        mockk<BlockIndexer> {
            every { getStatus() } returns status
            every { timeLastProcessed } returns
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(secondsSinceProgress)
        }

    @Test
    fun `stalled indexers are ignored until bootstrap is ready`() {
        val health = indicator(blockIndexer(Status.SYNCING, stallThreshold + 600)).health()

        assertThat(health.status).isEqualTo(UP)
        assertThat(health.details["message"] as String).contains("NOT_STARTED")
    }

    @Test
    fun `every running indexer past the threshold is down`() {
        bootstrapState.markReady()

        val health =
            indicator(
                    blockIndexer(Status.SYNCING, stallThreshold + 600),
                    blockIndexer(Status.FULLY_SYNCED, stallThreshold + 60),
                )
                .health()

        assertThat(health.status).isEqualTo(DOWN)
        assertThat(health.details["message"] as String)
            .contains("more than $stallThreshold seconds ago")
        assertThat(health.details["runningIndexers"]).isEqualTo(2)
    }

    @Test
    fun `one indexer still progressing keeps the process alive`() {
        bootstrapState.markReady()

        // The case the readiness check would kill: 27 indexers blocked behind one slow write.
        val health =
            indicator(
                    blockIndexer(Status.SYNCING, stallThreshold + 600),
                    blockIndexer(Status.FAST_SYNCING, stallThreshold + 600),
                    blockIndexer(Status.FULLY_SYNCED, 5),
                )
                .health()

        assertThat(health.status).isEqualTo(UP)
    }

    @Test
    fun `an indexer just inside the threshold is up`() {
        bootstrapState.markReady()

        val health = indicator(blockIndexer(Status.SYNCING, stallThreshold - 60)).health()

        assertThat(health.status).isEqualTo(UP)
    }

    @Test
    fun `indexers parked outside the sync loop are not measured`() {
        bootstrapState.markReady()

        val health =
            indicator(
                    blockIndexer(Status.READY_TO_SYNC, stallThreshold + 600),
                    blockIndexer(Status.READY_TO_FAST_SYNC, stallThreshold + 600),
                    blockIndexer(Status.NOT_INITIALISED, stallThreshold + 600),
                    blockIndexer(Status.SHUT_DOWN, stallThreshold + 600),
                )
                .health()

        assertThat(health.status).isEqualTo(UP)
        assertThat(health.details["message"]).isEqualTo("No indexer is running")
    }

    @Test
    fun `indexers without a block cursor are not measured`() {
        bootstrapState.markReady()
        val logsOnly = mockk<Indexer> { every { getStatus() } returns Status.SYNCING }

        val health = indicator(logsOnly).health()

        assertThat(health.status).isEqualTo(UP)
        assertThat(health.details["message"]).isEqualTo("No indexer is running")
    }

    @Test
    fun `the liveness group includes this indicator`() {
        // Spring drops an unknown group member silently, which would leave liveness as ping alone.
        val beanName =
            IndexerLivenessHealthIndicator::class.simpleName!!.replaceFirstChar { it.lowercase() }
        val contributor = HealthContributorNameFactory.INSTANCE.apply(beanName)
        val livenessInclude =
            Regex("liveness:\\s*\\n\\s*include:\\s*(.+)")
                .find(File("src/main/resources/application.yaml").readText())
                ?.groupValues
                ?.get(1)

        assertThat(livenessInclude).isNotNull()
        assertThat(livenessInclude!!.split(",").map { it.trim() }).contains(contributor)
    }
}
