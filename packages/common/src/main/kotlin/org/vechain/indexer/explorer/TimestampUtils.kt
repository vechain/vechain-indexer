package org.vechain.indexer.explorer

object TimestampUtils {
    private const val SLOT_STEP = 10L

    private const val ONE_HOUR = 3600L
    private const val ONE_DAY = 86400L
    private const val ONE_WEEK = 604800L
    private const val ONE_MONTH = 2592000L

    fun isHourly(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        isMultipleOf(previousTimestamp, currentTimestamp, ONE_HOUR)

    fun isDaily(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        isMultipleOf(previousTimestamp, currentTimestamp, ONE_DAY)

    fun isWeekly(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        isMultipleOf(previousTimestamp, currentTimestamp, ONE_WEEK)

    fun isMonthly(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        isMultipleOf(previousTimestamp, currentTimestamp, ONE_MONTH)

    fun isHourlyChange(previous: Long, current: Long): Boolean =
        hasBoundaryChanged(previous, current, ONE_HOUR)

    fun isDailyChange(previous: Long, current: Long): Boolean =
        hasBoundaryChanged(previous, current, ONE_DAY)

    fun isWeeklyChange(previous: Long, current: Long): Boolean =
        hasBoundaryChanged(previous, current, ONE_WEEK)

    fun isMonthlyChange(previous: Long, current: Long): Boolean =
        hasBoundaryChanged(previous, current, ONE_MONTH)

    fun isMultipleOf(previousTimestamp: Long, currentTimestamp: Long, multiple: Long): Boolean {
        validateTimestamps(previousTimestamp, currentTimestamp)

        // Check each step in between previous and current timestamp
        var stepTimestamp = previousTimestamp + SLOT_STEP
        while (stepTimestamp <= currentTimestamp) {
            if (stepTimestamp % multiple == 0L) {
                return true
            }
            stepTimestamp += SLOT_STEP
        }

        return false
    }

    private fun hasBoundaryChanged(
        previousTimestamp: Long,
        currentTimestamp: Long,
        interval: Long,
    ): Boolean = (previousTimestamp / interval) != (currentTimestamp / interval)

    /**
     * Checks that they are multiples of SLOT_STEP and that currentTimestamp is greater than
     * previousTimestamp
     */
    fun validateTimestamps(previousTimestamp: Long, currentTimestamp: Long) {
        checkValidTimestamp(previousTimestamp)
        checkValidTimestamp(currentTimestamp)
        require(currentTimestamp > previousTimestamp) {
            "Current timestamp ($currentTimestamp) must be greater than previous timestamp ($previousTimestamp)"
        }
    }

    // A valid timestamp is a multiple of SLOT_STEP (10 seconds)
    fun checkValidTimestamp(blockTimestamp: Long) =
        require(blockTimestamp % SLOT_STEP == 0L) { "Invalid block timestamp: $blockTimestamp" }

    fun calculateTimeBoundary(
        previousTimestamp: Long,
        currentTimestamp: Long,
        boundaryCheck: (Long, Long) -> Boolean,
    ): Boolean? = if (boundaryCheck(previousTimestamp, currentTimestamp)) true else null
}
