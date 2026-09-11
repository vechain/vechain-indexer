package org.vechain.indexer

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.explorer.BlockUsageProcessor
import org.vechain.indexer.explorer.BlockUsageService
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockProcessor
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockRepository
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockService
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockProcessor
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockRepository
import org.vechain.indexer.stargate.vetDelegated.VetDelegatedByBlockService
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockProcessor
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockRepository
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockService
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockProcessor
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockService
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockProcessor
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockService

/**
 * Each cache-bearing processor must invalidate its service's in-memory cache when rollback is
 * invoked, otherwise the next read returns a cached value that has drifted ahead of the persisted
 * state.
 */
class RollbackResetsCacheTest {

    private val checkpointService = mockk<CheckpointService>(relaxed = true)
    private val processorMetrics = mockk<ProcessorMetrics>(relaxed = true)

    @Test
    fun `VetStakedByBlockProcessor rollback resets service cache`() {
        val service = mockk<VetStakedByBlockService>(relaxed = true)
        val repository = mockk<VetStakedByBlockRepository>(relaxed = true)
        val processor =
            VetStakedByBlockProcessor(service, repository, checkpointService, processorMetrics)

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `VthoGeneratedByBlockProcessor rollback resets service cache`() {
        val service = mockk<VthoGeneratedByBlockService>(relaxed = true)
        val repository = mockk<VthoGeneratedByBlockRepository>(relaxed = true)
        val processor =
            VthoGeneratedByBlockProcessor(service, repository, checkpointService, processorMetrics)

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `NftHoldersByBlockProcessor rollback resets service cache`() {
        val service = mockk<NftHoldersByBlockService>(relaxed = true)
        val repository = mockk<NftHoldersByBlockRepository>(relaxed = true)
        val processor =
            NftHoldersByBlockProcessor(service, repository, checkpointService, processorMetrics)

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `VetDelegatedByBlockProcessor rollback resets service cache`() {
        val service = mockk<VetDelegatedByBlockService>(relaxed = true)
        val repository = mockk<VetDelegatedByBlockRepository>(relaxed = true)
        val processor =
            VetDelegatedByBlockProcessor(service, repository, checkpointService, processorMetrics)

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `VthoClaimedByBlockProcessor rollback resets service cache`() {
        val service = mockk<VthoClaimedByBlockService>(relaxed = true)
        val repository = mockk<VthoClaimedByBlockRepository>(relaxed = true)
        val processor =
            VthoClaimedByBlockProcessor(service, repository, checkpointService, processorMetrics)

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }

    @Test
    fun `BlockUsageProcessor rollback resets service cache`() {
        val service = mockk<BlockUsageService>(relaxed = true)
        val repository = mockk<BlockUsageRepository>(relaxed = true)
        val processor =
            BlockUsageProcessor(repository, service, checkpointService, processorMetrics)

        processor.rollback(100)

        verify(exactly = 1) { service.resetCache() }
    }
}
