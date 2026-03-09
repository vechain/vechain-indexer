package org.vechain.indexer.accounts

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.repository.AccountTotalsSeriesRepository
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger

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
        val expectedIds = extractExpectedAccountIds(block)

        val records = service.processBlock(block)
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
        val expectedIds = extractExpectedAccountIds(block)
        val existingId = expectedIds.first()

        every {
            repository.findFirstByRecordTypeAndBlockNumberLessThanOrderByBlockNumberDesc(
                AccountTotalsSeriesRecordType.SERIES,
                1L,
            )
        } returns previousSeries
        every { repository.findAllById(any<Iterable<String>>()) } returns
            listOf(createAccountMarker(existingId, previousSeries.blockTimestamp))

        val records = service.processBlock(block)
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
                transactions = emptyList(),
            )

        every {
            repository.findFirstByRecordTypeAndBlockNumberLessThanOrderByBlockNumberDesc(
                AccountTotalsSeriesRecordType.SERIES,
                51L,
            )
        } returns previousSeries

        val records = service.processBlock(block)

        assertTrue(records.isEmpty())
    }

    private fun extractExpectedAccountIds(
        block: org.vechain.indexer.thor.model.Block
    ): Set<String> {
        val txSigners = block.transactions.map { it.origin.lowercase() }.toSet()
        val gasPayers = block.transactions.map { it.gasPayer.lowercase() }.toSet()
        val vetHolders =
            block.transactions
                .flatMap { it.clauses }
                .filter { it.value.hexToBigInteger() > ONE_VET }
                .mapNotNull { it.to?.lowercase() }
                .toSet()
        return txSigners + gasPayers + vetHolders
    }

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

    private companion object {
        val ONE_VET = java.math.BigInteger.TEN.pow(18)
    }
}
