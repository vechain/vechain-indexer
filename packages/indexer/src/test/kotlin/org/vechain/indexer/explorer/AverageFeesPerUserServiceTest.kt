package org.vechain.indexer.explorer

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction

@ExtendWith(MockKExtension::class)
class AverageFeesPerUserServiceTest {
    @MockK lateinit var repository: AverageFeesPerUserRepository

    private val service by lazy { AverageFeesPerUserService(repository) }

    @Test
    fun `processBlock creates marker and summary records using distinct origins`() {
        val block =
            block(
                number = 100L,
                timestamp = 1_704_067_200L,
                transactions =
                    listOf(
                        tx(id = "0x1", origin = "0xAA", paid = "0xde0b6b3a7640000"),
                        tx(id = "0x2", origin = "0xAA", paid = "0x1bc16d674ec80000"),
                        tx(id = "0x3", origin = "0xBB", paid = "0x29a2241af62c0000"),
                    ),
            )
        val date = "2024-01-01"

        every {
            repository.findFirstByRecordTypeAndDateAndBlockNumberLessThanOrderByBlockNumberDesc(
                AverageFeesPerUserRecordType.SUMMARY,
                date,
                100L,
            )
        } returns null
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()

        val records = service.processBlock(block)
        val markers = records.filter { it.recordType == AverageFeesPerUserRecordType.ORIGIN_MARKER }
        val summary = records.single { it.recordType == AverageFeesPerUserRecordType.SUMMARY }

        assertEquals(2, markers.size)
        assertEquals(
            setOf("origin-2024-01-01-0xaa", "origin-2024-01-01-0xbb"),
            markers.map { it.id }.toSet(),
        )
        assertEquals(date, summary.date)
        assertEquals("summary-100", summary.id)
        assertDecimalEquals("6", summary.totalFeesPaid!!)
        assertEquals(2L, summary.dailyActiveUsers)
        assertDecimalEquals("3", summary.averageFeesPerUser!!)
    }

    @Test
    fun `processBlock updates an existing day without recounting known origins`() {
        val block =
            block(
                number = 101L,
                timestamp = 1_704_067_260L,
                transactions =
                    listOf(
                        tx(id = "0x4", origin = "0xAA", paid = "0xde0b6b3a7640000"),
                        tx(id = "0x5", origin = "0xCC", paid = "0x1bc16d674ec80000"),
                    ),
            )
        val date = "2024-01-01"
        val existingSummary =
            AverageFeesPerUser(
                id = "summary-100",
                blockId = "0xold",
                blockNumber = 100L,
                blockTimestamp = 1_704_067_200L,
                recordType = AverageFeesPerUserRecordType.SUMMARY,
                date = date,
                dayStartTimestamp = service.getDayStartTimestamp(block.timestamp),
                totalFeesPaid = decimal("4"),
                dailyActiveUsers = 2L,
                averageFeesPerUser = decimal("2"),
            )

        every {
            repository.findFirstByRecordTypeAndDateAndBlockNumberLessThanOrderByBlockNumberDesc(
                AverageFeesPerUserRecordType.SUMMARY,
                date,
                101L,
            )
        } returns existingSummary
        every { repository.findAllById(any<Iterable<String>>()) } returns
            listOf(marker(date = date, origin = "0xaa", blockNumber = 100L))

        val records = service.processBlock(block)
        val markers = records.filter { it.recordType == AverageFeesPerUserRecordType.ORIGIN_MARKER }
        val summary = records.single { it.recordType == AverageFeesPerUserRecordType.SUMMARY }

        assertEquals(1, markers.size)
        assertEquals("origin-2024-01-01-0xcc", markers.single().id)
        assertEquals("summary-101", summary.id)
        assertDecimalEquals("7", summary.totalFeesPaid!!)
        assertEquals(3L, summary.dailyActiveUsers)
        assertDecimalEquals("2.333333333333", summary.averageFeesPerUser!!)
    }

    @Test
    fun `processBlock counts the same origin again on a new utc day`() {
        val block =
            block(
                number = 200L,
                timestamp = 1_704_153_600L,
                transactions = listOf(tx(id = "0x6", origin = "0xAA", paid = "0xde0b6b3a7640000")),
            )
        val date = "2024-01-02"

        every {
            repository.findFirstByRecordTypeAndDateAndBlockNumberLessThanOrderByBlockNumberDesc(
                AverageFeesPerUserRecordType.SUMMARY,
                date,
                200L,
            )
        } returns null
        every { repository.findAllById(any<Iterable<String>>()) } returns emptyList()

        val records = service.processBlock(block)
        val markers = records.filter { it.recordType == AverageFeesPerUserRecordType.ORIGIN_MARKER }
        val summary = records.single { it.recordType == AverageFeesPerUserRecordType.SUMMARY }

        assertEquals(1, markers.size)
        assertEquals("origin-2024-01-02-0xaa", markers.single().id)
        assertEquals(1L, summary.dailyActiveUsers)
        assertDecimalEquals("1", summary.averageFeesPerUser!!)
    }

    @Test
    fun `save caches the latest summary for subsequent same-day blocks`() {
        val records =
            listOf(
                marker(date = "2024-01-01", origin = "0xaa", blockNumber = 100L),
                AverageFeesPerUser(
                    id = "summary-100",
                    blockId = "0x64",
                    blockNumber = 100L,
                    blockTimestamp = 1_704_067_200L,
                    recordType = AverageFeesPerUserRecordType.SUMMARY,
                    date = "2024-01-01",
                    dayStartTimestamp = 1_704_067_200L,
                    totalFeesPaid = decimal("1"),
                    dailyActiveUsers = 1L,
                    averageFeesPerUser = decimal("1"),
                ),
            )

        every { repository.saveAll(any<Iterable<AverageFeesPerUser>>()) } answers { firstArg() }

        service.save(records)

        assertEquals("summary-100", service.getPreviousSummary("2024-01-01", 101L)?.id)
    }

    private fun decimal(value: String): BigDecimal = BigDecimal(value)

    private fun assertDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun marker(date: String, origin: String, blockNumber: Long) =
        AverageFeesPerUser(
            id = "origin-$date-$origin",
            blockId = "0x$blockNumber",
            blockNumber = blockNumber,
            blockTimestamp = 1_704_067_200L,
            recordType = AverageFeesPerUserRecordType.ORIGIN_MARKER,
            date = date,
            origin = origin,
        )

    private fun block(number: Long, timestamp: Long, transactions: List<Transaction>) =
        Block(
            id = "0x${number.toString(16)}",
            number = number,
            timestamp = timestamp,
            parentID = "0xparent",
            size = 0,
            gasLimit = 0,
            baseFeePerGas = null,
            beneficiary = "0xbeneficiary",
            gasUsed = 0,
            totalScore = 0,
            txsRoot = "0xtxsroot",
            txsFeatures = 0,
            stateRoot = "0xstateroot",
            receiptsRoot = "0xreceiptsroot",
            signer = "0xsigner",
            isTrunk = true,
            isFinalized = true,
            transactions = transactions,
            com = false,
        )

    private fun tx(id: String, origin: String, paid: String) =
        Transaction(
            id = id,
            reward = "0x0",
            chainTag = 1,
            blockRef = "0x00",
            expiration = 720,
            clauses = listOf(mockk<Clause>(relaxed = true)),
            gasPriceCoef = 0,
            gas = 21_000,
            maxFeePerGas = "0x0",
            maxPriorityFeePerGas = "0x0",
            origin = origin,
            delegator = null,
            nonce = "0x1",
            dependsOn = null,
            size = 100,
            gasUsed = 21_000,
            gasPayer = origin,
            paid = paid,
            outputs = emptyList(),
            reverted = false,
            type = 1,
        )
}
