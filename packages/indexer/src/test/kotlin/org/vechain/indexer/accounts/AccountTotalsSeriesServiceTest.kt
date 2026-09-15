package org.vechain.indexer.accounts

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.ParamUtils.getAsString

@ExtendWith(MockKExtension::class)
class AccountTotalsSeriesServiceTest {
    @MockK lateinit var repository: AccountsWriteRepository

    private lateinit var service: AccountTotalsSeriesService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = AccountTotalsSeriesService(repository)
        every { repository.findSeen(any()) } returns emptySet()
    }

    @Test
    fun `previousTotals is null for the genesis block`() {
        assertNull(service.previousTotals(0L))
    }

    @Test
    fun `previousTotals reads the schema until a row has been saved`() {
        val previous = totals(blockNumber = 90L, blockTimestamp = 1_526_403_590L)
        every { repository.findTotalsBefore(any()) } returns previous

        assertEquals(previous, service.previousTotals(100L))
        verify(exactly = 1) { repository.findTotalsBefore(100L) }

        service.saved(totals(blockNumber = 99L, blockTimestamp = 1_526_403_600L))
        assertEquals(99L, service.previousTotals(100L)?.blockNumber)
        assertEquals(previous, service.previousTotals(99L))

        service.resetCache()
        assertEquals(previous, service.previousTotals(100L))
        verify(exactly = 3) { repository.findTotalsBefore(any()) }
    }

    @Test
    fun `processBlock refuses a block whose predecessor was never written`() {
        every { repository.findTotalsBefore(100L) } returns null
        val block = BlockFixtures.BLOCK_NO_CLAUSES.copy(number = 100L)

        val exception =
            assertThrows<IllegalArgumentException> { service.processBlock(block, emptyList()) }

        assertTrue(
            exception.message!!.contains(
                "Previous account totals record should exist for block 100"
            )
        )
    }

    @Test
    fun `the genesis block opens every period and counts each address it names`() {
        val block = BlockFixtures.BLOCK_RANDOM_TX.copy(number = 0L, id = "0xgenesis")
        val expected = extractExpectedAccountIds(block, emptyList())

        val update = service.processBlock(block, emptyList())

        assertEquals(expected, update.newAccounts.toSet())
        assertEquals(expected.size.toLong(), update.totals?.totalAccounts)
        assertEquals(
            listOf(TimeFrame.HOUR, TimeFrame.DAY, TimeFrame.WEEK, TimeFrame.MONTH),
            update.totals?.timeFrames,
        )
    }

    @Test
    fun `processBlock counts only the addresses not yet seen`() {
        val block =
            BlockFixtures.BLOCK_RANDOM_TX.copy(number = 1L, id = "0x1", timestamp = 1_526_404_810L)
        val previous =
            totals(blockNumber = 0L, blockTimestamp = 1_526_403_590L, totalAccounts = 10L)
        val expected = extractExpectedAccountIds(block, emptyList())
        val seen = expected.first()
        every { repository.findTotalsBefore(1L) } returns previous
        every { repository.findSeen(expected) } returns setOf(seen)

        val update = service.processBlock(block, emptyList())

        assertEquals(expected - seen, update.newAccounts.toSet())
        assertEquals(10L + expected.size - 1L, update.totals?.totalAccounts)
        assertEquals(listOf(TimeFrame.HOUR), update.totals?.timeFrames)
    }

    @Test
    fun `processBlock writes no row when nothing changed and no boundary was crossed`() {
        val previous =
            totals(blockNumber = 50L, blockTimestamp = 1_526_403_610L, totalAccounts = 10L)
        val block =
            BlockFixtures.BLOCK_NO_CLAUSES.copy(
                number = 51L,
                id = "0x51",
                timestamp = previous.blockTimestamp + 10L,
                beneficiary = Address.ZERO_ADDRESS,
                transactions = emptyList(),
            )
        every { repository.findTotalsBefore(51L) } returns previous

        val update = service.processBlock(block, emptyList())

        assertTrue(update.newAccounts.isEmpty())
        assertNull(update.totals)
    }

    @Test
    fun `extractAccountIds includes beneficiary clause recipients and all transfer participants`() {
        val beneficiary = "0x00000000000000000000000000000000000000bb"
        val ftFrom = "0x0000000000000000000000000000000000000101"
        val ftTo = "0x0000000000000000000000000000000000000102"
        val nftFrom = "0x0000000000000000000000000000000000000201"
        val nftTo = "0x0000000000000000000000000000000000000202"
        val sfTo = "0x0000000000000000000000000000000000000302"
        val batchFrom = "0x0000000000000000000000000000000000000401"

        val block = BlockFixtures.BLOCK_VET_TRANSFER.copy(number = 10L, beneficiary = beneficiary)
        val events =
            listOf(
                buildTransferEvent("VET_TRANSFER", ftFrom, ftTo, "amount" to "10"),
                buildTransferEvent("Transfer", nftFrom, nftTo, "tokenId" to "1"),
                buildTransferEvent("TransferSingle", Address.ZERO_ADDRESS, sfTo, "value" to "5"),
                buildTransferEvent(
                    "TransferBatch",
                    batchFrom,
                    Address.ZERO_ADDRESS,
                    "values" to listOf("1", "2"),
                ),
            )

        val accountIds = service.extractAccountIds(block, events)

        assertTrue(accountIds.contains(beneficiary))
        assertTrue(accountIds.contains("0x435933c8064b4ae76be665428e0307ef2ccfbd68"))
        assertTrue(accountIds.contains(ftFrom))
        assertTrue(accountIds.contains(ftTo))
        assertTrue(accountIds.contains(nftFrom))
        assertTrue(accountIds.contains(nftTo))
        assertTrue(accountIds.contains(sfTo))
        assertTrue(accountIds.contains(batchFrom))
        assertFalse(accountIds.contains(Address.ZERO_ADDRESS))
    }

    @Test
    fun `extractAccountIds excludes genesis beneficiary`() {
        val beneficiary = "0x00000000000000000000000000000000000000cc"
        val block =
            BlockFixtures.BLOCK_NO_CLAUSES.copy(
                number = 0L,
                beneficiary = beneficiary,
                transactions = emptyList(),
            )

        assertTrue(service.extractAccountIds(block, emptyList()).isEmpty())
    }

    @Test
    fun `extractAccountIds deduplicates addresses across sources`() {
        val shared = "0x00000000000000000000000000000000000000dd"
        val block =
            BlockFixtures.BLOCK_NO_CLAUSES.copy(
                number = 1L,
                beneficiary = shared,
                transactions = emptyList(),
            )
        val events =
            listOf(
                buildTransferEvent("Transfer", shared, shared, "value" to "1"),
                buildTransferEvent("TransferSingle", Address.ZERO_ADDRESS, shared, "value" to "1"),
                buildTransferEvent(
                    "TransferBatch",
                    shared,
                    Address.ZERO_ADDRESS,
                    "values" to listOf("1"),
                ),
            )

        assertEquals(setOf(shared), service.extractAccountIds(block, events))
    }

    @Test
    fun `extractAccountIds ignores missing null blank and invalid event addresses`() {
        val validFrom = "0x0000000000000000000000000000000000000a01"
        val validTo = "0x0000000000000000000000000000000000000a02"
        val block =
            BlockFixtures.BLOCK_NO_CLAUSES.copy(
                number = 1L,
                beneficiary = Address.ZERO_ADDRESS,
                transactions = emptyList(),
            )
        val events =
            listOf(
                buildTransferEvent("Transfer", validFrom, validTo, "value" to "1"),
                buildTransferEventWithParams("Transfer", mapOf("to" to validTo, "value" to "1")),
                buildTransferEventWithNullableParams(
                    "TransferSingle",
                    mapOf("from" to null, "to" to validTo, "value" to "1"),
                ),
                buildTransferEventWithNullableParams(
                    "TransferBatch",
                    mapOf("from" to "   ", "to" to validTo, "values" to listOf("1")),
                ),
                buildTransferEventWithNullableParams(
                    "Transfer",
                    mapOf("from" to "not-an-address", "to" to "0x1234", "value" to "1"),
                ),
            )

        assertEquals(setOf(validFrom, validTo), service.extractAccountIds(block, events))
    }

    private fun extractExpectedAccountIds(block: Block, events: List<IndexedEvent>): Set<String> =
        buildSet {
            block.transactions.forEach { tx ->
                add(tx.origin.lowercase())
                add(tx.gasPayer.lowercase())
                tx.clauses.mapNotNull { it.to?.lowercase() }.forEach(::add)
            }
            if (block.number > 0L && block.beneficiary.lowercase() != Address.ZERO_ADDRESS) {
                add(block.beneficiary.lowercase())
            }
            events
                .filter {
                    it.eventType in
                        setOf("VET_TRANSFER", "Transfer", "TransferSingle", "TransferBatch")
                }
                .flatMap { event ->
                    listOfNotNull(
                        event.params.getAsString("from")?.lowercase(),
                        event.params.getAsString("to")?.lowercase(),
                    )
                }
                .filterNot { it == Address.ZERO_ADDRESS }
                .forEach(::add)
        }

    private fun buildTransferEvent(
        eventType: String,
        from: String,
        to: String,
        vararg extraParams: Pair<String, Any>,
    ): IndexedEvent =
        buildIndexedEvent(
            eventType = eventType,
            params =
                AbiEventParameters(
                    buildMap {
                        put("from", from)
                        put("to", to)
                        extraParams.forEach { (key, value) -> put(key, value) }
                    },
                    eventType,
                ),
        )

    private fun buildTransferEventWithParams(
        eventType: String,
        params: Map<String, Any>,
    ): IndexedEvent =
        buildIndexedEvent(eventType = eventType, params = AbiEventParameters(params, eventType))

    @Suppress("UNCHECKED_CAST")
    private fun buildTransferEventWithNullableParams(
        eventType: String,
        params: Map<String, Any?>,
    ): IndexedEvent =
        buildIndexedEvent(
            eventType = eventType,
            params = AbiEventParameters(params as Map<String, Any>, eventType),
        )

    private fun totals(
        blockNumber: Long = 1L,
        blockTimestamp: Long = 1_526_403_590L,
        totalAccounts: Long = 1L,
    ) = AccountTotalsSeries("0x$blockNumber", blockNumber, blockTimestamp, totalAccounts)
}
