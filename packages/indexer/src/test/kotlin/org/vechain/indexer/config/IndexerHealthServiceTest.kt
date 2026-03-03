package org.vechain.indexer.config

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status

class IndexerHealthServiceTest {

    private val syncingThreshold = 120L
    private val notSyncingThreshold = 30L

    private lateinit var service: IndexerHealthService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        service = IndexerHealthService(syncingThreshold, notSyncingThreshold)
    }

    // --- Status-based early returns ---

    @Test
    fun `NOT_INITIALISED status returns UP`() {
        val indexer = mockk<Indexer>()
        every { indexer.getStatus() } returns Status.NOT_INITIALISED

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
        assertThat(message).isEqualTo("Indexer is not initialised")
    }

    @Test
    fun `INITIALISED status returns UP`() {
        val indexer = mockk<Indexer>()
        every { indexer.getStatus() } returns Status.INITIALISED

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
        assertThat(message).isEqualTo("Indexer is initialised but not started")
    }

    @Test
    fun `SHUT_DOWN status returns DOWN`() {
        val indexer = mockk<Indexer>()
        every { indexer.getStatus() } returns Status.SHUT_DOWN

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.DOWN)
        assertThat(message).isEqualTo("Indexer is shut down")
    }

    // --- Non-BlockIndexer (no timeLastProcessed) ---

    @Test
    fun `non-BlockIndexer with SYNCING status returns UNKNOWN`() {
        val indexer = mockk<Indexer>()
        every { indexer.getStatus() } returns Status.SYNCING

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UNKNOWN)
        assertThat(message).isEqualTo("No last processed time available")
    }

    @Test
    fun `non-BlockIndexer with FULLY_SYNCED status returns UNKNOWN`() {
        val indexer = mockk<Indexer>()
        every { indexer.getStatus() } returns Status.FULLY_SYNCED

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UNKNOWN)
        assertThat(message).isEqualTo("No last processed time available")
    }

    // --- Non-BlockIndexer falls through to UNKNOWN (no timeLastProcessed available) ---

    @Test
    fun `non-BlockIndexer with FAST_SYNCING status returns UNKNOWN`() {
        val indexer = mockk<Indexer>()
        every { indexer.getStatus() } returns Status.FAST_SYNCING

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UNKNOWN)
        assertThat(message).isEqualTo("No last processed time available")
    }

    // --- SYNCING threshold checks ---

    @Test
    fun `SYNCING BlockIndexer within threshold returns UP`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.SYNCING
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(syncingThreshold - 10)

        val (status, _) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
    }

    @Test
    fun `SYNCING BlockIndexer just inside threshold returns UP`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.SYNCING
        // 1 second inside the threshold to avoid time-drift flakiness
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(syncingThreshold - 1)

        val (status, _) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
    }

    @Test
    fun `SYNCING BlockIndexer over threshold returns DOWN`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.SYNCING
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(syncingThreshold + 10)

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.DOWN)
        assertThat(message).contains("more than $syncingThreshold seconds ago")
    }

    // --- FAST_SYNCING uses syncing threshold ---

    @Test
    fun `FAST_SYNCING BlockIndexer within threshold returns UP`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.FAST_SYNCING
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(syncingThreshold - 10)

        val (status, _) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
    }

    @Test
    fun `FAST_SYNCING BlockIndexer over threshold returns DOWN`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.FAST_SYNCING
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(syncingThreshold + 10)

        val (status, _) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.DOWN)
    }

    // --- FULLY_SYNCED uses not-syncing threshold ---

    @Test
    fun `FULLY_SYNCED BlockIndexer within not-syncing threshold returns UP`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.FULLY_SYNCED
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(notSyncingThreshold - 5)

        val (status, _) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
    }

    @Test
    fun `FULLY_SYNCED BlockIndexer just inside not-syncing threshold returns UP`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.FULLY_SYNCED
        // 1 second inside the threshold to avoid time-drift flakiness
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(notSyncingThreshold - 1)

        val (status, _) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
    }

    @Test
    fun `FULLY_SYNCED BlockIndexer over not-syncing threshold returns DOWN`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.FULLY_SYNCED
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(notSyncingThreshold + 10)

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.DOWN)
        assertThat(message).contains("more than $notSyncingThreshold seconds ago")
    }

    // --- Verify different thresholds are used correctly ---

    @Test
    fun `SYNCING uses syncing threshold not the not-syncing threshold`() {
        // Time is between not-syncing threshold and syncing threshold
        // Should be UP for syncing (within syncing threshold) but would be DOWN for not-syncing
        val timeBetweenThresholds = (notSyncingThreshold + syncingThreshold) / 2
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.SYNCING
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(timeBetweenThresholds)

        val (status, _) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
    }

    @Test
    fun `FULLY_SYNCED uses not-syncing threshold not the syncing threshold`() {
        // Time is between not-syncing threshold and syncing threshold
        // Should be DOWN for not-syncing but would be UP for syncing
        val timeBetweenThresholds = (notSyncingThreshold + syncingThreshold) / 2
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.FULLY_SYNCED
        every { indexer.timeLastProcessed } returns
            LocalDateTime.now(ZoneOffset.UTC).minusSeconds(timeBetweenThresholds)

        val (status, _) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.DOWN)
    }

    // --- Message content checks ---

    @Test
    fun `UP message includes last processed time`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.FULLY_SYNCED
        val recentTime = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(5)
        every { indexer.timeLastProcessed } returns recentTime

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.UP)
        assertThat(message).isEqualTo("Last processed at $recentTime")
    }

    @Test
    fun `DOWN message includes last processed time and timeout`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.getStatus() } returns Status.FULLY_SYNCED
        val oldTime = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(notSyncingThreshold + 60)
        every { indexer.timeLastProcessed } returns oldTime

        val (status, message) = service.getIndexerHealth(indexer)

        assertThat(status).isEqualTo(HealthStatus.DOWN)
        assertThat(message)
            .isEqualTo(
                "Last processed at $oldTime which is more than $notSyncingThreshold seconds ago"
            )
    }
}
