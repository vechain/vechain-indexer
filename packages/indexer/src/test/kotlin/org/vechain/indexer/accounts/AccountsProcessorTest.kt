package org.vechain.indexer.accounts

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.genesis.GenesisVetBalanceLoader
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.postgres.IndexerStateRepository

class AccountsProcessorTest {
    private val overviewService = mockk<AccountOverviewService>()
    private val totalsService = mockk<AccountTotalsSeriesService>(relaxed = true)
    private val repository = mockk<AccountsWriteRepository>(relaxed = true)
    private val genesisLoader = mockk<GenesisVetBalanceLoader>()
    private val processor =
        AccountsProcessor(
            overviewService,
            totalsService,
            repository,
            genesisLoader,
            mockk<IndexerStateRepository>(relaxed = true),
            CheckpointProperties(),
            InlineVersioningProperties(),
            ProcessorMetrics(SimpleMeterRegistry()),
        )

    private val alice = "0x" + "a".repeat(40)
    private val genesis =
        BlockFixtures.BLOCK_NO_CLAUSES.copy(number = 0L, transactions = emptyList())

    @Test
    fun `bootstrap preloads the genesis allocations into an empty schema`() {
        every { repository.hasOverviews() } returns false
        every { genesisLoader.loadGenesisAllocations() } returns
            GenesisVetBalanceLoader.LoadedGenesis(
                network = "mainnet",
                launchTime = genesis.timestamp,
                genesisBlock = genesis,
                allocations = listOf(GenesisVetBalanceLoader.GenesisAllocation(alice, "1000")),
            )

        processor.bootstrap()

        verify(exactly = 1) {
            repository.saveGenesis(
                listOf(
                    AccountOverview(
                        address = alice,
                        blockId = genesis.id,
                        blockNumber = 0L,
                        blockTimestamp = genesis.timestamp,
                        firstSeen = genesis.timestamp,
                        lastSeen = genesis.timestamp,
                        vetBalance = BigInteger.valueOf(1000),
                    )
                ),
                listOf(
                    VetBalance(alice, genesis.id, 0L, genesis.timestamp, BigInteger.valueOf(1000))
                ),
            )
        }
    }

    @Test
    fun `bootstrap leaves a populated schema alone`() {
        every { repository.hasOverviews() } returns true

        processor.bootstrap()

        verify(exactly = 0) { genesisLoader.loadGenesisAllocations() }
        verify(exactly = 0) { repository.saveGenesis(any(), any()) }
    }

    @Test
    fun `bootstrap skips the preload when the genesis resource is missing`() {
        every { repository.hasOverviews() } returns false
        every { genesisLoader.loadGenesisAllocations() } returns null

        processor.bootstrap()

        verify(exactly = 0) { repository.saveGenesis(any(), any()) }
    }

    @Test
    fun `the Hayabusa settlement runs ahead of the block, whose tables are saved together`() {
        val block = BlockFixtures.BLOCK_NO_CLAUSES.copy(number = 1000L)
        val overview =
            AccountOverview(
                alice,
                block.id,
                block.number,
                block.timestamp,
                block.timestamp,
                block.timestamp,
            )
        val balance = VetBalance(alice, block.id, block.number, block.timestamp, BigInteger.ONE)
        val totals = AccountTotalsSeries(block.id, block.number, block.timestamp, 7)
        every { overviewService.isHayabusaBlock(1000L) } returns true
        every { overviewService.settleHayabusa(block) } just Runs
        coEvery { overviewService.processBlock(block, emptyList()) } returns
            AccountOverviewService.Update(listOf(overview), listOf(balance))
        every { totalsService.processBlock(block, emptyList()) } returns
            AccountTotalsSeriesService.Update(listOf(alice), totals)

        runBlocking {
            processor.process(
                IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
            )
        }

        verifyOrder {
            overviewService.settleHayabusa(block)
            repository.save(
                AccountsUpdate(1000L, listOf(overview), listOf(balance), listOf(alice), totals)
            )
            totalsService.saved(totals)
        }
    }

    @Test
    fun `an entry without the full block is refused`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                processor.processEntry(IndexingResult.LogResult(5L, emptyList(), Status.SYNCING))
            }
        }
    }

    @Test
    fun `a rollback forgets the last totals row`() {
        processor.rollback(5L)

        verify(exactly = 1) { totalsService.resetCache() }
        verify(exactly = 1) { repository.rollbackFrom(5L) }
    }
}
