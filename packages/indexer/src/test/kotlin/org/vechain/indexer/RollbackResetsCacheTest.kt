package org.vechain.indexer

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.explorer.AverageFeesPerUserService
import org.vechain.indexer.explorer.BlockUsageService
import org.vechain.indexer.explorer.ExplorerProcessor
import org.vechain.indexer.explorer.ExplorerWriteRepository
import org.vechain.indexer.stargate.staking.StargateStakingProcessor
import org.vechain.indexer.stargate.staking.StargateStakingService
import org.vechain.indexer.stargate.staking.StargateStakingWriteRepository
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockService
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedProcessor
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedService
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedWriteRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockProcessor
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockService
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedWriteRepository
import org.vechain.indexer.validator.DelegationProcessor
import org.vechain.indexer.validator.DelegationService
import org.vechain.indexer.validator.DelegationWriteRepository

/**
 * Each cache-bearing processor must invalidate its service's in-memory cache when rollback is
 * invoked, otherwise the next read returns a cached value that has drifted ahead of the persisted
 * state.
 */
class RollbackResetsCacheTest {

    private val processorMetrics = mockk<ProcessorMetrics>(relaxed = true)

    @Test
    fun `StargateStakingProcessor rollback resets service cache`() {
        val service = mockk<StargateStakingService>(relaxed = true)
        val repository = mockk<StargateStakingWriteRepository>(relaxed = true)
        val processor =
            StargateStakingProcessor(
                service,
                repository,
                mockk(relaxed = true),
                CheckpointProperties(),
                processorMetrics,
            )

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `VthoGeneratedByBlockProcessor rollback resets service cache`() {
        val service = mockk<VthoGeneratedByBlockService>(relaxed = true)
        val repository = mockk<VthoGeneratedWriteRepository>(relaxed = true)
        val processor =
            VthoGeneratedByBlockProcessor(
                service,
                repository,
                mockk(relaxed = true),
                CheckpointProperties(),
                processorMetrics,
            )

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `DelegationProcessor rollback resets the VET-delegated series cache`() {
        val service = mockk<VetDelegatedByBlockService>(relaxed = true)
        val processor =
            DelegationProcessor(
                mockk<DelegationService>(relaxed = true),
                service,
                mockk<DelegationWriteRepository>(relaxed = true),
                mockk(relaxed = true),
                CheckpointProperties(),
                InlineVersioningProperties(),
                processorMetrics,
            )

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `VthoClaimedProcessor rollback resets service cache`() {
        val service = mockk<VthoClaimedService>(relaxed = true)
        val repository = mockk<VthoClaimedWriteRepository>(relaxed = true)
        val processor =
            VthoClaimedProcessor(
                service,
                repository,
                mockk(relaxed = true),
                CheckpointProperties(),
                InlineVersioningProperties(),
                processorMetrics,
            )

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `ExplorerProcessor rollback resets service cache`() {
        val service = mockk<BlockUsageService>(relaxed = true)
        val processor =
            ExplorerProcessor(
                service,
                mockk<AverageFeesPerUserService>(relaxed = true),
                mockk<ExplorerWriteRepository>(relaxed = true),
                mockk(relaxed = true),
                CheckpointProperties(),
                InlineVersioningProperties(),
                processorMetrics,
            )

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }
}
