package org.vechain.indexer.explorer

import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.utils.TimeValidationUtils

@Profile("explorer")
@Service
open class AverageFeesPerUserService(private val repository: AverageFeesPerUserReadRepository) {
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

        return repository.findBetween(
            dayStartTimestamp(startTimestamp),
            dayStartTimestamp(endTimestamp),
        )
    }

    internal fun dayStartTimestamp(timestamp: Long): Long =
        Instant.ofEpochSecond(timestamp)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()
}
