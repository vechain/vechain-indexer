package org.vechain.indexer.explorer

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.Transaction

@ExtendWith(MockKExtension::class)
class AverageFeesPerUserServiceTest {
    @MockK lateinit var repository: ExplorerWriteRepository

    private val service by lazy { AverageFeesPerUserService(repository) }

    private val day1 = 1_704_067_200L
    private val day2 = 1_704_153_600L

    @Test
    fun `processBlock records each new origin once and opens the day's summary`() {
        val block =
            block(
                number = 100L,
                timestamp = day1,
                transactions =
                    listOf(
                        tx(id = "0x1", origin = "0xAA", paid = "0xde0b6b3a7640000"),
                        tx(id = "0x2", origin = "0xAA", paid = "0x1bc16d674ec80000"),
                        tx(id = "0x3", origin = "0xBB", paid = "0x29a2241af62c0000"),
                    ),
            )

        every { repository.findCurrentFees(day1) } returns null
        every { repository.findKnownOrigins(day1, any()) } returns emptySet()

        val update = requireNotNull(service.processBlock(block))

        assertEquals(setOf("0xaa", "0xbb"), update.newOrigins.map { it.origin }.toSet())
        assertEquals(setOf(day1), update.newOrigins.map { it.dayStartTimestamp }.toSet())
        assertDecimalEquals("6", update.updatedSummary.totalFeesPaid)
        assertEquals(2L, update.updatedSummary.dailyActiveUsers)
        assertDecimalEquals("3", update.updatedSummary.averageFeesPerUser)
        assertEquals("2024-01-01", update.updatedSummary.date)
    }

    @Test
    fun `processBlock adds to the same day without recounting a known origin`() {
        val block =
            block(
                number = 101L,
                timestamp = day1 + 60,
                transactions =
                    listOf(
                        tx(id = "0x4", origin = "0xAA", paid = "0xde0b6b3a7640000"),
                        tx(id = "0x5", origin = "0xCC", paid = "0x1bc16d674ec80000"),
                    ),
            )

        every { repository.findCurrentFees(day1) } returns
            summary(totalFeesPaid = "4", dailyActiveUsers = 2, averageFeesPerUser = "2")
        every { repository.findKnownOrigins(day1, setOf("0xaa", "0xcc")) } returns setOf("0xaa")

        val update = requireNotNull(service.processBlock(block))

        assertEquals(listOf("0xcc"), update.newOrigins.map { it.origin })
        assertDecimalEquals("7", update.updatedSummary.totalFeesPaid)
        assertEquals(3L, update.updatedSummary.dailyActiveUsers)
        assertDecimalEquals("2.333333333333", update.updatedSummary.averageFeesPerUser)
    }

    @Test
    fun `processBlock counts the same origin again on a new utc day`() {
        val block =
            block(
                number = 200L,
                timestamp = day2,
                transactions = listOf(tx(id = "0x6", origin = "0xAA", paid = "0xde0b6b3a7640000")),
            )

        every { repository.findCurrentFees(day2) } returns null
        every { repository.findKnownOrigins(day2, any()) } returns emptySet()

        val update = requireNotNull(service.processBlock(block))

        assertEquals(listOf("0xaa"), update.newOrigins.map { it.origin })
        assertEquals("2024-01-02", update.updatedSummary.date)
        assertEquals(1L, update.updatedSummary.dailyActiveUsers)
    }

    @Test
    fun `processBlock skips a block the day's summary already counts`() {
        val block =
            block(
                number = 100L,
                timestamp = day1 + 60,
                transactions = listOf(tx(id = "0x7", origin = "0xAA", paid = "0xde0b6b3a7640000")),
            )

        every { repository.findCurrentFees(day1) } returns
            summary(totalFeesPaid = "4", dailyActiveUsers = 2, averageFeesPerUser = "2")

        assertEquals(null, service.processBlock(block))
    }

    @Test
    fun `processBlock skips a block with no transactions`() {
        assertEquals(null, service.processBlock(block(1L, day1, emptyList())))
    }

    private fun summary(
        totalFeesPaid: String,
        dailyActiveUsers: Long,
        averageFeesPerUser: String,
    ) =
        AverageFeesPerUser(
            blockId = "0xold",
            blockNumber = 100L,
            blockTimestamp = day1,
            date = "2024-01-01",
            dayStartTimestamp = day1,
            totalFeesPaid = decimal(totalFeesPaid),
            dailyActiveUsers = dailyActiveUsers,
            averageFeesPerUser = decimal(averageFeesPerUser),
        )

    private fun decimal(value: String) = BigDecimal(value)

    private fun assertDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

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
