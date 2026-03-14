package org.vechain.indexer.explorer

import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository

@Profile("explorer", "average-fees-per-user")
@Service
open class AverageFeesPerUserService(private val repository: AverageFeesPerUserRepository) {
    open fun getAverageFeesPerUser(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<AverageFeesPerUser> {
        require(startTimestamp >= 0) { "startTimestamp must be non-negative" }
        require(endTimestamp >= startTimestamp) {
            "endTimestamp must be greater than or equal to startTimestamp"
        }

        val startDayStartTimestamp = dayStartTimestamp(startTimestamp)
        val endDayStartTimestamp = dayStartTimestamp(endTimestamp)
        return repository.findAllByRecordTypeAndDayStartTimestampBetween(
            AverageFeesPerUserRecordType.SUMMARY,
            startDayStartTimestamp,
            endDayStartTimestamp,
        )
    }

    internal fun dayStartTimestamp(timestamp: Long): Long =
        Instant.ofEpochSecond(timestamp)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()
}
