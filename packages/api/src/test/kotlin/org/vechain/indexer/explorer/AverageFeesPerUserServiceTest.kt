package org.vechain.indexer.explorer

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class AverageFeesPerUserServiceTest {
    private val repository: AverageFeesPerUserRepository = mockk()
    private val service = AverageFeesPerUserService(repository)

    @Test
    fun `getAverageFeesPerUser queries daily points using floored utc day boundaries`() {
        val records =
            listOf(
                averageFeesPerUser(date = "2024-01-01", dayStartTimestamp = 1_704_067_200L),
                averageFeesPerUser(date = "2024-01-02", dayStartTimestamp = 1_704_153_600L),
            )
        every { repository.findAllInDayRange(1_704_067_200L, 1_704_153_600L) } returns records

        val result = service.getAverageFeesPerUser(1_704_070_000L, 1_704_154_000L)

        expectThat(result).isEqualTo(records)
        verify(exactly = 1) { repository.findAllInDayRange(1_704_067_200L, 1_704_153_600L) }
    }

    private fun averageFeesPerUser(date: String, dayStartTimestamp: Long) =
        AverageFeesPerUser(
            id = "summary-$dayStartTimestamp",
            blockId = "0x1",
            blockNumber = 1L,
            blockTimestamp = dayStartTimestamp,
            recordType = AverageFeesPerUserRecordType.SUMMARY,
            date = date,
            dayStartTimestamp = dayStartTimestamp,
            totalFeesPaid = BigDecimal.ONE,
            dailyActiveUsers = 1L,
            averageFeesPerUser = BigDecimal.ONE,
        )
}
