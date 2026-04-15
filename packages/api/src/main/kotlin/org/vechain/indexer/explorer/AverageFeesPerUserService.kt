package org.vechain.indexer.explorer

import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository
import org.vechain.indexer.utils.TimeValidationUtils

@Profile("explorer", "average-fees-per-user")
@Service
open class AverageFeesPerUserService(private val repository: AverageFeesPerUserRepository) {
    open fun getAverageFeesPerUser(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<AverageFeesPerUser> {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )

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
