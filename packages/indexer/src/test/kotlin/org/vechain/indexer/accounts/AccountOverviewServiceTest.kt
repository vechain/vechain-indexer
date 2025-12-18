package org.vechain.indexer.accounts

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

@ExtendWith(MockKExtension::class)
internal class AccountOverviewServiceTest {
    @MockK lateinit var repository: AccountOverviewRepository

    @MockK lateinit var archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>

    @MockK lateinit var pruner: TargetedPruner<AccountOverview, AccountOverviewArchive>

    private lateinit var service: TestableService

    private class TestableService(
        repository: AccountOverviewRepository,
        archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>,
        pruner: TargetedPruner<AccountOverview, AccountOverviewArchive>,
    ) : AccountOverviewService(repository, archiveService, pruner) {
        fun callResolveAccountOverviewForUpdateAndArchive(
            recordId: String,
            block: Block,
            updated: MutableMap<String, AccountOverview>,
            archived: MutableMap<String, AccountOverview>,
        ): AccountOverview =
            resolveAccountOverviewForUpdateAndArchive(recordId, block, updated, archived)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TestableService(repository, archiveService, pruner)
    }

    private fun block(number: Long = 1L) =
        Block(
            id = "0xBLOCK",
            number = number,
            timestamp = 1234567890,
            parentID = "0xPARENT",
            size = 0,
            gasLimit = 0,
            baseFeePerGas = null,
            beneficiary = "0xBENEFICIARY",
            gasUsed = 0,
            totalScore = 0,
            txsRoot = "0xTXROOT",
            txsFeatures = 0,
            stateRoot = "0xSTATEROOT",
            receiptsRoot = "0xRECEIPTSROOT",
            signer = "0xSIGNER",
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
            com = false,
        )

    private fun existingAccountOverview(address: String, version: Int = 3) =
        AccountOverview(
            address = address,
            blockId = "0xOLD_BLOCK",
            blockNumber = 10L,
            blockTimestamp = 100L,
            version = version,
            firstSeen = BlockIdentifier(number = 1L, id = "0xFIRST"),
            lastSeen = BlockIdentifier(number = 10L, id = "0xOLD_BLOCK"),
            transactionsSent = 1L,
            clausesSent = 2L,
            vthoGenerated = BigInteger.ZERO,
            vthoBurned = BigInteger.ZERO,
            vthoDelegated = BigInteger.ZERO,
            gasUsed = BigInteger.ZERO,
            vetSent = BigInteger.ZERO,
            vetReceived = BigInteger.ZERO,
        )

    @Test
    fun `returns cached record when already updated`() {
        val recordId = "0xACC"
        val cached = existingAccountOverview(recordId, version = 7)

        val updated = mutableMapOf(recordId to cached)
        val archived = mutableMapOf<String, AccountOverview>()

        val resolved =
            service.callResolveAccountOverviewForUpdateAndArchive(
                recordId,
                block(),
                updated,
                archived,
            )

        assertSame(cached, resolved)
        assertSame(cached, updated[recordId])
        assertTrue(archived.isEmpty())
    }

    @Test
    fun `archives existing record and returns version bumped copy`() {
        val recordId = "0xACC"
        val existing = existingAccountOverview(recordId, version = 3)
        every { repository.findByIdOrNull(recordId) } returns existing

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        val resolved =
            service.callResolveAccountOverviewForUpdateAndArchive(
                recordId,
                block(),
                updated,
                archived,
            )

        assertEquals(4, resolved.version)
        assertSame(resolved, updated[recordId])
        assertSame(existing, archived[recordId])
    }

    @Test
    fun `creates new record when none exists`() {
        val recordId = "0xNEW"
        val b = block(number = 42L)
        every { repository.findByIdOrNull(recordId) } returns null

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        val resolved =
            service.callResolveAccountOverviewForUpdateAndArchive(recordId, b, updated, archived)

        assertEquals(recordId, resolved.address)
        assertEquals(0, resolved.version)
        assertEquals(b.id, resolved.blockId)
        assertEquals(b.number, resolved.blockNumber)
        assertEquals(b.timestamp, resolved.blockTimestamp)
        assertEquals(BlockIdentifier(b.number, b.id), resolved.firstSeen)
        assertEquals(BlockIdentifier(b.number, b.id), resolved.lastSeen)

        assertSame(resolved, updated[recordId])
        assertTrue(archived.isEmpty())
    }

    @Test
    fun `does not bump version twice within same block`() {
        val recordId = "0xACC"
        val existing = existingAccountOverview(recordId, version = 3)
        every { repository.findByIdOrNull(recordId) } returns existing

        val updated = mutableMapOf<String, AccountOverview>()
        val archived = mutableMapOf<String, AccountOverview>()

        val first =
            service.callResolveAccountOverviewForUpdateAndArchive(
                recordId,
                block(),
                updated,
                archived,
            )
        val second =
            service.callResolveAccountOverviewForUpdateAndArchive(
                recordId,
                block(),
                updated,
                archived,
            )

        assertSame(first, second)
        assertEquals(4, second.version)
        assertSame(existing, archived[recordId])
    }
}
