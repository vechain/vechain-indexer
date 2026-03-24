package org.vechain.indexer.accounts

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.repository.AccountTotalsSeriesRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.ParamUtils.getAsString

@ExtendWith(MockKExtension::class)
class AccountTotalsSeriesServiceTest {
    @MockK lateinit var repository: AccountTotalsSeriesRepository

    private lateinit var service: AccountTotalsSeriesService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = AccountTotalsSeriesService(repository)
    }

    @Test
    fun `getPreviousSeries returns null for genesis block`() {
        val result = service.getPreviousSeries(0L)
        assertNull(result)
    }

    @Test
    fun `getPreviousSeries queries repository for non-genesis block`() {
        val previousSeries = createSeries(blockNumber = 99L, blockTimestamp = 1_526_403_590L)
        every {
            repository.findFirstByRecordTypeAndBlockNumberLessThanOrderByBlockNumberDesc(
                AccountTotalsSeriesRecordType.SERIES,
                100L,
            )
        } returns previousSeries

        val result = service.getPreviousSeries(100L)

        assertEquals(previousSeries, result)
        verify(exactly = 1) {
            repository.findFirstByRecordTypeAndBlockNumberLessThanOrderByBlockNumberDesc(
                AccountTotalsSeriesRecordType.SERIES,
                100L,
            )
        }
    }

    @Test
    fun `validatePreviousSeries throws when previous record is missing`() {
        val exception =
            assertThrows<IllegalArgumentException> { service.validatePreviousSeries(null, 100L) }

        assertTrue(
            exception.message!!.contains(
                "Previous account totals record should exist for block 100"
            )
        )
    }

    @Test
    fun `validatePreviousSeries allows genesis without previous record`() {
        assertDoesNotThrow { service.validatePreviousSeries(null, 0L) }
    }

    @Test
    fun `processBlock creates genesis cumulative record and account markers`() {
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()
        val block = BlockFixtures.BLOCK_RANDOM_TX.copy(number = 0L, id = "0xgenesis")
        val expectedIds = extractExpectedAccountIds(block, emptyList())

        val records = service.processBlock(block, emptyList())
        val series = records.last()
        val markers = records.dropLast(1)

        assertEquals(expectedIds.size.toLong(), series.totalAccounts)
        assertEquals(expectedIds.size, markers.size)
        assertTrue(series.isHourly == true)
        assertTrue(series.isDaily == true)
        assertTrue(series.isWeekly == true)
        assertTrue(series.isMonthly == true)
        assertTrue(markers.all { it.recordType == AccountTotalsSeriesRecordType.ACCOUNT })
    }

    @Test
    fun `processBlock increments only for newly discovered accounts`() {
        val block =
            BlockFixtures.BLOCK_RANDOM_TX.copy(number = 1L, id = "0x1", timestamp = 1_526_404_810L)
        val previousSeries =
            createSeries(blockNumber = 0L, blockTimestamp = 1_526_403_590L, totalAccounts = 10L)
        val expectedIds = extractExpectedAccountIds(block, emptyList())
        val existingId = expectedIds.first()

        every {
            repository.findFirstByRecordTypeAndBlockNumberLessThanOrderByBlockNumberDesc(
                AccountTotalsSeriesRecordType.SERIES,
                1L,
            )
        } returns previousSeries
        every { repository.findAllById(any<Iterable<String>>()) } returns
            listOf(createAccountMarker(existingId, previousSeries.blockTimestamp))

        val records = service.processBlock(block, emptyList())
        val series = records.last()
        val markers = records.dropLast(1)

        assertEquals(10L + expectedIds.size - 1L, series.totalAccounts)
        assertEquals(expectedIds.size - 1, markers.size)
        assertTrue(series.isHourly == true)
    }

    @Test
    fun `processBlock skips series write when nothing changed and no boundary crossed`() {
        val previousSeries =
            createSeries(blockNumber = 50L, blockTimestamp = 1_526_403_610L, totalAccounts = 10L)
        val block =
            BlockFixtures.BLOCK_NO_CLAUSES.copy(
                number = 51L,
                id = "0x51",
                timestamp = previousSeries.blockTimestamp + 10L,
                beneficiary = Address.ZERO_ADDRESS,
                transactions = emptyList(),
            )

        every {
            repository.findFirstByRecordTypeAndBlockNumberLessThanOrderByBlockNumberDesc(
                AccountTotalsSeriesRecordType.SERIES,
                51L,
            )
        } returns previousSeries

        val records = service.processBlock(block, emptyList())

        assertTrue(records.isEmpty())
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

        val accountIds = service.extractAccountIds(block, emptyList())

        assertTrue(accountIds.isEmpty())
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

        val accountIds = service.extractAccountIds(block, events)

        assertEquals(setOf(shared), accountIds)
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

        val accountIds = service.extractAccountIds(block, events)

        assertEquals(setOf(validFrom, validTo), accountIds)
    }

    private fun extractExpectedAccountIds(
        block: org.vechain.indexer.thor.model.Block,
        events: List<IndexedEvent>,
    ): Set<String> = buildSet {
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
                it.eventType in setOf("VET_TRANSFER", "Transfer", "TransferSingle", "TransferBatch")
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

    private fun createSeries(
        blockNumber: Long = 1L,
        blockTimestamp: Long = 1_526_403_590L,
        totalAccounts: Long = 1L,
    ) =
        AccountTotalsSeries(
            id = "series-$blockNumber",
            blockId = "0x$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            recordType = AccountTotalsSeriesRecordType.SERIES,
            totalAccounts = totalAccounts,
            address = null,
            isHourly = null,
            isDaily = null,
            isWeekly = null,
            isMonthly = null,
        )

    private fun createAccountMarker(address: String, blockTimestamp: Long) =
        AccountTotalsSeries(
            id = "account-$address",
            blockId = "0x0",
            blockNumber = 0L,
            blockTimestamp = blockTimestamp,
            recordType = AccountTotalsSeriesRecordType.ACCOUNT,
            totalAccounts = null,
            address = address,
            isHourly = null,
            isDaily = null,
            isWeekly = null,
            isMonthly = null,
        )
}
