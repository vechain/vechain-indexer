package org.vechain.indexer.explorer

object TimestampUtils {
    private const val SLOT_STEP = 10L

    fun isHourly(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        isMultipleOf(previousTimestamp, currentTimestamp, 3600L)

    fun isDaily(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        isMultipleOf(previousTimestamp, currentTimestamp, 86400)

    fun isWeekly(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        isMultipleOf(previousTimestamp, currentTimestamp, 604800)

    fun isMonthly(previousTimestamp: Long, currentTimestamp: Long): Boolean =
        isMultipleOf(previousTimestamp, currentTimestamp, 2592000)

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
}
