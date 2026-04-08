package org.vechain.indexer.explorer

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository
import org.vechain.indexer.utils.TimeValidationUtils.MAX_SUPPORTED_UNIX_TIMESTAMP_LONG
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
        every {
            repository.findAllByRecordTypeAndDayStartTimestampBetween(
                AverageFeesPerUserRecordType.SUMMARY,
                1_704_067_200L,
                1_704_153_600L,
            )
        } returns records

        val result = service.getAverageFeesPerUser(1_704_070_000L, 1_704_154_000L)

        expectThat(result)
            .isEqualTo(
                listOf(
                    averageFeesPerUser(date = "2024-01-01", dayStartTimestamp = 1_704_067_200L),
                    averageFeesPerUser(date = "2024-01-02", dayStartTimestamp = 1_704_153_600L),
                )
            )
        verify(exactly = 1) {
            repository.findAllByRecordTypeAndDayStartTimestampBetween(
                AverageFeesPerUserRecordType.SUMMARY,
                1_704_067_200L,
                1_704_153_600L,
            )
        }
    }

    @Test
    fun `dayStartTimestamp accepts maximum supported unix timestamp`() {
        val result = assertDoesNotThrow {
            service.dayStartTimestamp(MAX_SUPPORTED_UNIX_TIMESTAMP_LONG)
        }

        expectThat(result).isEqualTo(MAX_SUPPORTED_UNIX_TIMESTAMP_LONG)
    }

    @Test
    fun `getAverageFeesPerUser rejects oversized start timestamp`() {
        val exception =
            assertThrows<BadRequestException> {
                service.getAverageFeesPerUser(31_556_889_832_694_401L, 31_556_889_832_694_401L)
            }

        expectThat(exception.message)
            .isEqualTo("Invalid 'startTimestamp' timestamp: exceeds supported Unix timestamp range")
    }

    private fun averageFeesPerUser(date: String, dayStartTimestamp: Long) =
        AverageFeesPerUser(
            id = "summary-$date",
            blockId = "0x1",
            blockNumber = 1L,
            blockTimestamp = dayStartTimestamp,
            version = 1,
            recordType = AverageFeesPerUserRecordType.SUMMARY,
            date = date,
            dayStartTimestamp = dayStartTimestamp,
            totalFeesPaid = BigDecimal.ONE,
            dailyActiveUsers = 1L,
            averageFeesPerUser = BigDecimal.ONE,
        )
}
